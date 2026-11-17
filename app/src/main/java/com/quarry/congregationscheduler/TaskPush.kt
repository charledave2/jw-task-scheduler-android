package com.quarry.congregationscheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class TaskNotificationTarget(val accountId: String, val date: LocalDate?)

internal object TaskPush {
    private const val CHANNEL = "task_assignments"
    private const val WORK = "task-push-registration"
    private fun prefs(context: Context) = context.getSharedPreferences("task_push", Context.MODE_PRIVATE)
    @Synchronized fun installationId(context: Context): String {
        val preferences = prefs(context)
        return preferences.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("installation_id", it).apply()
        }
    }
    fun allowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    fun channel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Task assignments", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "New tasks assigned to your linked personnel" }
        )
    }
    fun schedule(context: Context) {
        if (!BuildConfig.FCM_ENABLED) return
        channel(context)
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TaskPushRegistrationWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    suspend fun registerNow(context: Context) = withContext(Dispatchers.IO) {
        check(BuildConfig.FCM_ENABLED) { "Use the release app for push notifications." }
        check(allowed(context)) { "Enable notifications in Android settings first." }
        val tokens = TokenStore(context)
        val id = tokens.getUserId() ?: error("Sign in first.")
        val token = Tasks.await(FirebaseMessaging.getInstance().token, 30, TimeUnit.SECONDS)
        check(tokens.getUserId() == id) { "Account changed. Try again." }
        ApiClient(context).registerPushDevice(installationId(context), token, id)
    }
    fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(WORK) }
    fun clearNotifications(context: Context) { NotificationManagerCompat.from(context).cancelAll() }
    fun target(intent: Intent?): TaskNotificationTarget? {
        val account = intent?.getStringExtra("task_account_id") ?: return null
        val date = intent.getStringExtra("task_date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return TaskNotificationTarget(account, date)
    }
    @Synchronized fun display(context: Context, data: Map<String, String>) {
        if (!BuildConfig.FCM_ENABLED || !allowed(context)) return
        val tokens = TokenStore(context)
        val mode = context.getSharedPreferences("scheduler_preferences", Context.MODE_PRIVATE).getString("workspace_mode", null)
        if (mode != "shared" || tokens.getAccess().isNullOrBlank() || tokens.getUserId() != data["account_id"]) return
        val event = data["event_id"]?.takeIf { it.length <= 256 } ?: return
        // FCM is at-least-once; persist a small recent-event cache across process restarts.
        val preferences = prefs(context)
        val seen = preferences.getString("events", "").orEmpty().split('\n').filter(String::isNotBlank)
        if (event in seen) return
        channel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("task_account_id", data["account_id"])
            data["meeting_date"]?.let { putExtra("task_date", it) }
            this.data = android.net.Uri.parse("jwtasks://notification/${android.net.Uri.encode(event)}")
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_task_notification)
            .setColor(android.graphics.Color.rgb(39, 88, 57))
            .setContentTitle(data["title"] ?: "New task assignment")
            .setContentText(data["body"] ?: "You have a new task.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(data["body"].orEmpty()))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending).setAutoCancel(true).build()
        try {
            NotificationManagerCompat.from(context).notify(event, 1, notification)
            preferences.edit().putString("events", (seen.takeLast(99) + event).joinToString("\n")).apply()
        } catch (_: SecurityException) { /* Permission may change while the message arrives. */ }
    }
}

class TaskPushRegistrationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!BuildConfig.FCM_ENABLED) return@withContext Result.success()
        val tokens = TokenStore(applicationContext)
        val userId = tokens.getUserId() ?: return@withContext Result.success()
        val mode = applicationContext.getSharedPreferences("scheduler_preferences", Context.MODE_PRIVATE).getString("workspace_mode", null)
        if (mode != "shared") return@withContext Result.success()
        try {
            val api = ApiClient(applicationContext)
            if (!TaskPush.allowed(applicationContext)) {
                api.unregisterPushDevice(TaskPush.installationId(applicationContext))
                return@withContext Result.success()
            }
            val token = Tasks.await(FirebaseMessaging.getInstance().token, 30, TimeUnit.SECONDS)
            if (tokens.getUserId() != userId || isStopped) return@withContext Result.success()
            api.registerPushDevice(TaskPush.installationId(applicationContext), token, userId)
            Result.success()
        } catch (e: ApiException) {
            if (e.code in setOf(401, 403, 409)) Result.failure() else Result.retry()
        } catch (_: Exception) { Result.retry() }
    }
}

class TaskMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { TaskPush.schedule(this) }
    override fun onMessageReceived(message: RemoteMessage) { TaskPush.display(this, message.data) }
}

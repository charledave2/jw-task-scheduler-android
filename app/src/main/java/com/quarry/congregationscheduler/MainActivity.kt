package com.quarry.congregationscheduler

import android.content.Intent
import android.content.ClipData
import androidx.core.content.FileProvider
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.*
import androidx.compose.material.icons.twotone.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter


class MainActivity : AppCompatActivity() {
    private lateinit var appLock: AppLockController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLock = AppLockController(this)
        window.statusBarColor = android.graphics.Color.rgb(246, 246, 243)
        window.navigationBarColor = android.graphics.Color.rgb(246, 246, 243)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { AppSecurityShell(appLock) }
    }
    override fun onStop() { appLock.onBackground(); super.onStop() }
    override fun onDestroy() { appLock.dispose(); super.onDestroy() }
}

class AppStore(context: android.content.Context) {
    private val appContext = context.applicationContext
    private val api = ApiClient(context)
    private val local = LocalWorkspaceStore(context)
    private val preferences = context.getSharedPreferences("scheduler_preferences", android.content.Context.MODE_PRIVATE)
    val user = mutableStateOf<UserInfo?>(null)
    val data = mutableStateOf<ScheduleData?>(null)
    val taskReviews = mutableStateOf<List<TaskReview>>(emptyList())
    val allTaskReviews = mutableStateOf<List<TaskReview>?>(null)
    val users = mutableStateOf<List<ManagedUser>>(emptyList())
    val loading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val page = mutableStateOf("Home")
    val month = mutableStateOf(YearMonth.now())
    val dark = mutableStateOf(preferences.getBoolean("dark_appearance", false))
    val fontScale = mutableFloatStateOf(preferences.getFloat("font_scale", 1f))
    val offline = mutableStateOf(true)
    val avatar = mutableStateOf<ImageBitmap?>(null)
    val accountPhotos = mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
    val personPhotos = mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
    val calendarRequestedDate = mutableStateOf<LocalDate?>(null)
    val calendarShouldOpenDate = mutableStateOf(false)
    val mode = mutableStateOf(preferences.getString("workspace_mode", null))
    val localConfig = mutableStateOf<LocalWorkspaceConfig?>(null)
    val cloudConfig = mutableStateOf<LocalWorkspaceConfig?>(null)
    val autoBackup = mutableStateOf(local.autoBackupInfo())
    val availableUpdate = mutableStateOf<AppUpdate?>(null)
    val updateError = mutableStateOf<String?>(null)
    val updateDownloading = mutableStateOf(false)
    val updateProgress = mutableFloatStateOf(0f)
    val localMode get() = mode.value == "local"
    val workspaceConfig get() = if (localMode) localConfig.value else cloudConfig.value

    fun createLocalWorkspace(name: String) {
        mode.value = "local"
        preferences.edit().putString("workspace_mode", "local").putString("local_display_name", "Administrator").apply()
        local.settings(WorkspaceSettings(name.trim().ifBlank { "My Congregation" }, "18:00", "15:30", "07:45"))
        user.value = UserInfo("local-admin", "local", preferences.getString("local_display_name", "Administrator") ?: "Administrator", "admin", "local")
    }
    fun exportLocalBackup(): String = local.export()
    fun importLocalBackup(content: String) = local.import(content)
    fun configureAutoBackupFolder(uri: Uri): String = local.configureAutoBackupFolder(uri).also { autoBackup.value = local.autoBackupInfo() }
    fun setAutoBackupEnabled(enabled: Boolean) { local.setAutoBackupEnabled(enabled); autoBackup.value = local.autoBackupInfo() }
    fun refreshAutoBackupInfo() { autoBackup.value = local.autoBackupInfo() }
    private val updatePromptSession = UpdatePromptSession()
    private var knownUpdate: AppUpdate? = null
    fun beginUpdateSession() {
        updatePromptSession.begin()
        if (!updateDownloading.value) availableUpdate.value = knownUpdate
    }
    suspend fun checkAppUpdate() {
        if (updatePromptSession.dismissed || updateDownloading.value) return
        val session = updatePromptSession.generation
        val found = runCatching { AppUpdater.checkForUpdate() }.getOrNull()
        if (!updatePromptSession.accepts(session) || updateDownloading.value) return
        if (found != null) knownUpdate = found
        availableUpdate.value = found ?: knownUpdate
    }
    fun dismissAppUpdate() { updatePromptSession.dismiss(); availableUpdate.value = null }
    suspend fun refreshPageAccess() {
        if (localMode || user.value == null) return
        try { user.value = api.getMe() } catch (e: Exception) {
            if (e is ApiException && e.code == 401) signOut()
        }
    }
    fun canOpenPage(page: String): Boolean = localMode || accountCanOpenPage(user.value, page)
    suspend fun downloadAndInstallUpdate(context: android.content.Context) {
        val update = availableUpdate.value ?: return
        updateDownloading.value = true
        updateProgress.value = 0f
        updateError.value = null
        try {
            val apk = AppUpdater.download(context, update) { updateProgress.value = it }
            AppUpdater.install(context, apk)
        } catch(e:Exception) { updateError.value=e.message ?: "Could not download the update."; throw e }
        finally { updateDownloading.value = false }
    }
    suspend fun enableSharedWorkspace() { preferences.edit().putString("workspace_mode", "shared").apply(); mode.value = "shared"; load() }
    fun chooseSharedWorkspace() { mode.value = "shared"; preferences.edit().putString("workspace_mode", "shared").apply() }
    fun returnToWorkspaceSetup() { mode.value = null; preferences.edit().remove("workspace_mode").apply() }
    fun saveLocalConfig(config:LocalWorkspaceConfig) { local.saveConfig(config); localConfig.value=config; refreshAutoBackupInfo() }
    suspend fun saveCloudConfig(config:LocalWorkspaceConfig) { cloudConfig.value=api.saveWorkspaceConfig(config) }

    suspend fun initialize() {
        loading.value = true
        try {
            if (mode.value == "local") {
                user.value = UserInfo("local-admin", "local", preferences.getString("local_display_name", "Administrator") ?: "Administrator", "admin", "local")
                refreshAvatar()
                load()
                return
            }
            if (mode.value == null && !api.hasSession()) return
            user.value = api.getMe()
            mode.value = "shared"
            preferences.edit().putString("workspace_mode", "shared").apply()
            refreshAvatar()
            load()
        } catch (e: Exception) {
            if (e is ApiException && e.code == 401) api.logout()
            user.value = null
        } finally { loading.value = false }
    }
    suspend fun login(username: String, password: String) {
        loading.value = true; error.value = null
        try { mode.value = "shared"; preferences.edit().putString("workspace_mode", "shared").apply(); user.value = api.login(username.trim(), password); refreshAvatar(); load(); page.value = "Home" }
        catch (e: Exception) { error.value = e.message ?: "Could not sign in."; user.value = null }
        finally { loading.value = false }
    }
    suspend fun load() {
        error.value = null
        try {
            if (localMode) { localConfig.value = local.config(); refreshAutoBackupInfo(); data.value = local.schedule(month.value) }
            else { cloudConfig.value = api.loadWorkspaceConfig(); data.value = api.loadSchedule(month.value, cloudConfig.value) }
            // Keep schedules usable while the Cloudflare migration/API update is being deployed.
            taskReviews.value = if (localMode) local.reviews(month.value) else runCatching { api.loadTaskReviews(month.value) }.getOrDefault(emptyList())
        }
        catch (e: Exception) { error.value = e.message ?: "Could not load the schedule."; throw e }
    }
    suspend fun reviewTask(meeting: Meeting, slot: Slot, status: String) {
        val review = TaskReview(meeting.date, meeting.type, slot.department, slot.key.removePrefix("dept:"), slot.personId ?: "", slot.name ?: "", status)
        if (localMode) { local.review(review); refreshAutoBackupInfo() } else api.saveTaskReview(meeting, slot, status)
        allTaskReviews.value = null
        val reviewedMonth = YearMonth.from(LocalDate.parse(meeting.date))
        taskReviews.value = taskReviews.value.filterNot { runCatching { YearMonth.from(LocalDate.parse(it.date)) == reviewedMonth }.getOrDefault(false) } + if (localMode) local.reviews(reviewedMonth) else api.loadTaskReviews(reviewedMonth)
    }
    suspend fun loadReviewRange(start: LocalDate, end: LocalDate): Pair<List<Meeting>, List<TaskReview>> {
        val months = generateSequence(YearMonth.from(start)) { previous -> if (previous < YearMonth.from(end)) previous.plusMonths(1) else null }.toList()
        val schedules = months.map { if (localMode) local.schedule(it) else api.loadSchedule(it, cloudConfig.value ?: api.loadWorkspaceConfig()) }
        val reviews = months.flatMap { if (localMode) local.reviews(it) else api.loadTaskReviews(it) }
        return schedules.flatMap { it.meetings }.filter { runCatching { LocalDate.parse(it.date) in start..end }.getOrDefault(false) } to reviews.filter { runCatching { LocalDate.parse(it.date) in start..end }.getOrDefault(false) }
    }
    suspend fun loadAllTaskReviews(forceRefresh: Boolean = false): List<TaskReview> {
        if (!forceRefresh) allTaskReviews.value?.let { return it }
        val loaded = if (localMode) local.allReviews() else api.loadTaskReviews(allHistory = true)
        allTaskReviews.value = loaded
        return loaded
    }
    suspend fun changeMonth(next: YearMonth) { month.value = next; load() }
    suspend fun loadMonthForExport(month: YearMonth): ScheduleData = if (localMode) local.schedule(month) else api.loadSchedule(month, cloudConfig.value ?: api.loadWorkspaceConfig())
    suspend fun assign(meeting: Meeting, slot: Slot, person: Person?) { if (localMode) local.saveAssignment(meeting, slot, person) else api.assign(meeting, slot, person); load() }
    suspend fun savePerson(person: Person) {
        loading.value = true
        try { if (localMode) local.savePerson(person) else if (person.id.isBlank()) api.createPerson(person) else api.updatePerson(person); load() }
        finally { loading.value = false }
    }
    suspend fun deletePerson(id: String) {
        loading.value = true
        try { if (localMode) local.deletePerson(id) else api.deletePerson(id); load() }
        finally { loading.value = false }
    }
    suspend fun savePersonPhoto(id: String, uri: Uri) {
        val bytes = withContext(Dispatchers.IO) { encodeAvatar(uri) }
        if (localMode) { local.savePhoto("person:$id", bytes); data.value?.people?.firstOrNull { it.id == id }?.let { local.savePerson(it.copy(avatarUpdatedAt = System.currentTimeMillis().toString())) } }
        else api.uploadPersonAvatar(id, bytes)
        personPhotos.value = personPhotos.value - id
        loadPersonPhoto(id)
        load()
    }
    suspend fun removePersonPhoto(id: String) {
        if (localMode) local.removePhoto("person:$id") else api.deletePersonAvatar(id)
        personPhotos.value = personPhotos.value - id
        load()
    }
    suspend fun loadPersonPhoto(id: String) {
        if (personPhotos.value.containsKey(id)) return
        try {
            val bytes = if (localMode) local.photo("person:$id") else api.downloadPersonAvatar(id)
            val photo = bytes?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
            if (photo != null) personPhotos.value = personPhotos.value + (id to photo)
        } catch (_: Exception) { }
    }
    suspend fun loadUsers() { if (localMode) { users.value = emptyList(); return }; loading.value = true; try { users.value = api.loadUsers() } catch (e: Exception) { error.value = e.message }; loading.value = false }
    suspend fun loadAccountPhoto(account: ManagedUser) {
        if (!account.hasAvatar) { accountPhotos.value = accountPhotos.value - account.id; return }
        val bytes = api.downloadAccountAvatar(account.id)
        val image = bytes?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
        accountPhotos.value = if (image == null) accountPhotos.value - account.id else accountPhotos.value + (account.id to image)
    }
    suspend fun savePageAccess(account: ManagedUser, pages: Set<String>) { api.savePageAccess(account,pages); loadUsers() }
    suspend fun resetPassword(account: ManagedUser, password: String, confirm: String) { api.resetUserPassword(account,password,confirm) }
    suspend fun createUser(username: String, displayName: String, password: String, confirmPassword: String, role: String, departments: List<String>, actions: List<String>) {
        api.createUser(username, displayName, password, confirmPassword, role, departments, actions)
        loadUsers()
    }
    suspend fun setActive(user: ManagedUser, active: Boolean) { api.setUserActive(user, active); loadUsers() }
    suspend fun setRole(user: ManagedUser, role: String) { api.setUserRole(user, role); loadUsers() }
    suspend fun deleteUser(user: ManagedUser) { api.deleteUser(user); loadUsers() }
    suspend fun saveWorkspace(settings: WorkspaceSettings) {
        val saved = if (localMode) settings.also(local::settings) else api.saveWorkspace(settings)
        data.value = data.value?.copy(settings = saved) ?: api.loadSchedule(month.value)
        load()
    }
    suspend fun saveProfile(name: String) { if (localMode) { preferences.edit().putString("local_display_name", name).apply(); user.value = user.value?.copy(displayName = name) } else { val updated = api.saveProfile(name); user.value = user.value?.copy(displayName = updated.displayName) } }
    suspend fun saveAvatar(uri: Uri) {
        val bytes = withContext(Dispatchers.IO) { encodeAvatar(uri) }
        if (localMode) { local.savePhoto("local-admin", bytes); refreshAvatar() } else api.uploadAvatar(bytes)
        refreshAvatar()
    }
    suspend fun removeAvatar() { if (localMode) local.removePhoto("local-admin") else api.deleteAvatar(); avatar.value = null }
    private suspend fun refreshAvatar() {
        avatar.value = try {
            val bytes = if (localMode) local.photo("local-admin") else api.downloadAvatar()
            bytes?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
        } catch (_: Exception) { null }
    }
    private fun encodeAvatar(uri: Uri): ByteArray {
        val resolver = appContext.contentResolver
        val decoded = if (android.os.Build.VERSION.SDK_INT >= 28) {
            runCatching {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val width = info.size.width
                    val height = info.size.height
                    val scale = minOf(1f, 1024f / maxOf(width, height))
                    decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
                }
            }.getOrNull()
        } else null
        val bitmap = decoded ?: run {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }.getOrNull()
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, bounds) }
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IllegalArgumentException("This image provider did not return a readable photo. Try saving the image to your device first, then select it again.")
            }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val fromStream = runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } }.getOrNull()
            fromStream ?: runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, options) }
            }.getOrNull() ?: throw IllegalArgumentException("Could not read that photo. Try choosing a JPG or PNG image saved on your device.")
        }
        val cropSize = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width-cropSize)/2, (bitmap.height-cropSize)/2, cropSize, cropSize)
        if (square !== bitmap) bitmap.recycle()
        val scaled = Bitmap.createScaledBitmap(square, 256, 256, true)
        if (scaled !== square) square.recycle()
        val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        for (quality in listOf(84, 72, 60, 48)) {
            val output = ByteArrayOutputStream()
            scaled.compress(format, quality, output)
            if (output.size() <= 250_000 || quality == 48) { scaled.recycle(); return output.toByteArray() }
        }
        scaled.recycle()
        throw IllegalArgumentException("Could not prepare that photo.")
    }
    fun setFontScale(value: Float) { val next = value.coerceIn(.85f, 1.30f); fontScale.value = next; preferences.edit().putFloat("font_scale", next).apply() }
    fun setDarkAppearance(enabled: Boolean) {
        dark.value = enabled
        preferences.edit().putBoolean("dark_appearance", enabled).apply()
    }
    suspend fun signOut() { if (localMode) { user.value = null; data.value = null; mode.value = null; preferences.edit().remove("workspace_mode").apply() } else { api.logout(); user.value = null; data.value = null; users.value = emptyList(); accountPhotos.value = emptyMap(); avatar.value = null; mode.value = null; preferences.edit().remove("workspace_mode").apply() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerApp(appLock: AppLockController) {
    val context = LocalContext.current
    val store = remember { AppStore(context) }
    val scope = rememberCoroutineScope()
    val user by store.user
    val signedIn = user
    val workspaceMode by store.mode
    val page by store.page
    val loading by store.loading
    val error by store.error
    var assignmentTarget by remember { mutableStateOf<Pair<Meeting, Slot>?>(null) }
    var confirmDelete by remember { mutableStateOf<ManagedUser?>(null) }
    var profileDialog by remember { mutableStateOf(false) }
    var createAccountDialog by remember { mutableStateOf(false) }
    var snack by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var showSplash by remember { mutableStateOf(true) }
    val backupWriter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch { runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.exportLocalBackup()) } ?: error("Could not write backup file.") }.onSuccess { snackbar.showSnackbar("Backup saved.") }.onFailure { snackbar.showSnackbar(it.message ?: "Could not save backup.") } }
    }
    val backupReader = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Could not read backup file.") }.onSuccess { content -> runCatching { store.importLocalBackup(content); store.load() }.onSuccess { snackbar.showSnackbar("Backup restored.") }.onFailure { snackbar.showSnackbar(it.message ?: "Could not restore backup.") } }.onFailure { snackbar.showSnackbar(it.message ?: "Could not read backup.") } }
    }
    val autoBackupFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                store.configureAutoBackupFolder(uri)
            }.onSuccess { folder -> snackbar.showSnackbar("Automatic backup enabled · $folder · .bcdv") }
                .onFailure { snackbar.showSnackbar(it.message ?: "Could not enable automatic backup.") }
        }
    }
    val installPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (AppUpdater.canInstallPackages(context)) scope.launch {
            runCatching { store.downloadAndInstallUpdate(context) }
                .onFailure { snackbar.showSnackbar(it.message ?: "Could not download the update.") }
        }
    }

    var moreOpen by remember { mutableStateOf(false) }
    val profilePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) scope.launch { try { store.saveAvatar(uri);snackbar.showSnackbar("Profile photo updated.") } catch(e:Exception){snackbar.showSnackbar(e.message ?: "Could not upload photo.")} }
    }
    val openCalendar: (LocalDate?) -> Unit = { requestedDate ->
        scope.launch {
            if (!store.canOpenPage("Schedule Calendar")) { snackbar.showSnackbar("This page is not available for your account."); return@launch }
            val date = requestedDate ?: LocalDate.now()
            val requestedMonth = YearMonth.from(date)
            if (store.month.value != requestedMonth) runCatching { store.changeMonth(requestedMonth) }
            store.calendarRequestedDate.value = date
            store.calendarShouldOpenDate.value = requestedDate != null
            store.page.value = "Schedule Calendar"
        }
    }
    DisposableEffect(context) {
        val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                store.beginUpdateSession()
                scope.launch { store.checkAppUpdate() }
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                scope.launch { store.refreshPageAccess() }
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        try { store.initialize() } finally { delay(450); showSplash = false; store.checkAppUpdate() }
    }
    LaunchedEffect(store.data.value?.people?.map { it.id to it.avatarUpdatedAt }) {
        store.data.value?.people.orEmpty().filter { it.avatarUpdatedAt != null }.forEach { person ->
            launch { store.loadPersonPhoto(person.id) }
        }
    }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it) } }

    val colors = if (store.dark.value) darkColorScheme(
        primary = Color(0xFFA9C6A8), onPrimary = Color(0xFF172318),
        primaryContainer = Color(0xFF34473A), onPrimaryContainer = Color(0xFFDCE8D7),
        secondary = Color(0xFFC3D0C0), onSecondary = Color(0xFF253328),
        background = Color(0xFF151713), surface = Color(0xFF20231E), onSurface = Color(0xFFE8E9E1),
        surfaceDim = Color(0xFF151713), surfaceBright = Color(0xFF383C35),
        surfaceContainerLowest = Color(0xFF151713), surfaceContainerLow = Color(0xFF1B1E19),
        surfaceContainer = Color(0xFF292D27), surfaceContainerHigh = Color(0xFF32372F), surfaceContainerHighest = Color(0xFF3B4038),
        onSurfaceVariant = Color(0xFFB9BDB2), outline = Color(0xFF7A8276), outlineVariant = Color(0xFF3B4038),
        secondaryContainer = Color(0xFF343F34), onSecondaryContainer = Color(0xFFDCE8D7),
        tertiary = Color(0xFFC6D2B8), onTertiary = Color(0xFF263126),
        tertiaryContainer = Color(0xFF384437), onTertiaryContainer = Color(0xFFDDE8D4),
        surfaceVariant = Color(0xFF292D27), surfaceTint = Color(0xFFA9C6A8),
        inverseSurface = Color(0xFFE8E9E1), inverseOnSurface = Color(0xFF20231E), inversePrimary = Color(0xFF5C745F)
    ) else lightColorScheme(
        primary = Color(0xFF5C745F), onPrimary = Color.White, background = Color(0xFFF6F6F3),
        primaryContainer = Color(0xFFDCE8D8), onPrimaryContainer = Color(0xFF1D3021),
        secondary = Color(0xFF627A67), onSecondary = Color.White,
        surface = Color.White, onSurface = Color(0xFF252721),
        surfaceDim = Color(0xFFDADDD7), surfaceBright = Color.White,
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F9F5),
        surfaceContainer = Color(0xFFF0F2ED), surfaceContainerHigh = Color(0xFFE9ECE6), surfaceContainerHighest = Color(0xFFE1E5DE),
        outline = Color(0xFF73796F), outlineVariant = Color(0xFFE5E6DF), secondaryContainer = Color(0xFFEDF2EB),
        onSecondaryContainer = Color(0xFF253328), tertiary = Color(0xFF77835D), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE6EACF), onTertiaryContainer = Color(0xFF30391D),
        surfaceVariant = Color(0xFFF0F2ED), onSurfaceVariant = Color(0xFF686D64), surfaceTint = Color(0xFF5C745F),
        inverseSurface = Color(0xFF303B30), inverseOnSurface = Color(0xFFF6F6F3), inversePrimary = Color(0xFFA9C6A8)
    )
    MaterialTheme(colorScheme = colors) {
      val baseDensity = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * store.fontScale.value)) {
        SideEffect {
            (context as? android.app.Activity)?.window?.let { window ->
                val bar = if (store.dark.value) android.graphics.Color.rgb(21, 23, 19) else android.graphics.Color.rgb(246, 246, 243)
                window.statusBarColor = bar
                window.navigationBarColor = bar
                window.decorView.systemUiVisibility = if (store.dark.value) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
        if (showSplash) {
            LoadingSplashScreen()
        } else if (signedIn == null && workspaceMode == null) {
            WorkspaceModeScreen(onLocal = { name -> store.createLocalWorkspace(name); scope.launch { runCatching { store.load() } } }, onShared = { store.chooseSharedWorkspace() })
        } else if (signedIn == null) {
            LoginScreen(loading = loading, error = error, onLogin = { u, p -> scope.launch { store.login(u, p) } }, onBackToSetup = { store.returnToWorkspaceSetup() })
        } else {
                Scaffold(
                    modifier=Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    bottomBar={ PreviewBottomNavigation(page=page,moreOpen=moreOpen,canOpen=store::canOpenPage,onMore={moreOpen=true},onNavigate={destination ->
                        if(store.canOpenPage(destination)) {
                            moreOpen=false
                            if(destination=="Schedule Calendar")openCalendar(null) else store.page.value=destination
                        }
                    }) },
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = { Text(when (page) { "Home" -> store.data.value?.settings?.name ?: "JW Task Scheduler"; "Top Performers" -> "Performance Page"; else -> page }, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            navigationIcon = { Image(painterResource(R.drawable.csp_logo),null,Modifier.padding(start=16.dp,end=10.dp).size(32.dp).clip(RoundedCornerShape(9.dp))) },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbar) }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                      Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (!store.canOpenPage(page)) {
                            LaunchedEffect(page, signedIn.permissions) { store.page.value = "Home" }
                        } else when (page) {
                            "Home" -> HomeScreen(store, onGo = { if(store.canOpenPage(it)) store.page.value = it else scope.launch { snackbar.showSnackbar("This page is not available for your account.") } }, onOpenCalendar = openCalendar)
                            "Schedule Calendar" -> ScheduleCalendarScreen(store)
                            "Schedule", "Manage Schedule" -> ScheduleScreen(store, onAssign = { meeting, slot -> assignmentTarget = meeting to slot })
                            "People Directory" -> PeopleScreen(store)
                            "Reports" -> ReportsScreen(store.data.value, store.workspaceConfig)
                            "Task Review" -> if (store.user.value?.role == "admin") TaskReviewScreen(store, onViewTopPerformers = { store.page.value = "Top Performers" }, onReview = { meeting, slot, status -> try { store.reviewTask(meeting, slot, status); snackbar.showSnackbar("Task review saved.") } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Could not save task review.") } }) else TopPerformersScreen(store)
                            "Top Performers" -> TopPerformersScreen(store)
                            "Export Studio" -> ExportScreen(store)
                            "Settings & Accounts", "Settings & accounts" -> SettingsScreen(store, scope, snackbar, appLock = appLock, onDelete = { confirmDelete = it }, onAddAccount = { createAccountDialog = true }, onBackup = { backupWriter.launch("jw-task-scheduler-backup.bcdv") }, onRestore = { backupReader.launch(arrayOf("*/*")) }, onChooseAutoBackupFolder = { autoBackupFolderPicker.launch(null) }, onAutoBackupToggle = { enabled ->
                                if (enabled && store.autoBackup.value.folderName == null) autoBackupFolderPicker.launch(null)
                                else runCatching { store.setAutoBackupEnabled(enabled) }.onSuccess {
                                    scope.launch { snackbar.showSnackbar(if (enabled) "Automatic .bcdv backup enabled." else "Automatic backup turned off.") }
                                }.onFailure { error -> scope.launch { snackbar.showSnackbar(error.message ?: "Could not update automatic backup.") } }
                            })
                            else -> HomeScreen(store, onGo = { if(store.canOpenPage(it)) store.page.value = it else scope.launch { snackbar.showSnackbar("This page is not available for your account.") } }, onOpenCalendar = openCalendar)
                        }
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = MaterialTheme.colorScheme.primary)
                      }
                    }
                }
        }

        if(moreOpen && signedIn!=null && !appLock.locked) {
            ModalBottomSheet(onDismissRequest={moreOpen=false;profileDialog=false},containerColor=MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*.72f).dp).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("More",fontSize=22.sp,fontWeight=FontWeight.Bold)
                    val tools=buildList {
                        add("Reports" to "chart")
                        if(signedIn.role in setOf("admin","assistant"))add("Task Review" to "clipboard-check")
                        add("Export Studio" to "file-down")
                        add("Settings & Accounts" to "settings")
                    }.filter{store.canOpenPage(it.first)}
                    tools.forEach { (destination,icon) ->
                        TextButton(onClick={moreOpen=false;profileDialog=false;store.page.value=destination;if(destination=="Settings & Accounts"&&signedIn.role=="admin")scope.launch{store.loadUsers()}},modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=12.dp,vertical=12.dp)) {
                            Icon(MaterialSymbol(icon),null,Modifier.size(22.dp));Spacer(Modifier.width(12.dp));Text(destination,Modifier.weight(1f));Icon(MaterialSymbol("chevron-right"),null,Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable{profileDialog=!profileDialog}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Avatar(signedIn.displayName,44.dp,store.avatar.value);Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(roleLabel(signedIn.role).uppercase(java.util.Locale.ROOT),fontWeight=FontWeight.Bold,fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                                Text(signedIn.displayName,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(MaterialSymbol("chevron-right"),"Open profile",Modifier.size(20.dp))
                        }
                        ProfileDialog(user=signedIn,photo=store.avatar.value,expanded=profileDialog,onDismiss={profileDialog=false},onSave={name->store.saveProfile(name);profileDialog=false},onSignOut={profileDialog=false;moreOpen=false;scope.launch{store.page.value="Home";store.signOut()}},onRequestPhoto={profilePhotoPicker.launch("image/*")})
                    }
                    
                }
            }
        }
        if (!showSplash && !appLock.locked) {
                      store.availableUpdate.value?.let { update ->
                        AppUpdateDialog(
                            update = update,
                            downloading = store.updateDownloading.value,
                            progress = store.updateProgress.floatValue,
                            onUpdate = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !AppUpdater.canInstallPackages(context)) {
                                    installPermissionLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                                } else scope.launch {
                                    runCatching { store.downloadAndInstallUpdate(context) }
                                        .onFailure { snackbar.showSnackbar(it.message ?: "Could not download the update.") }
                                }
                            },
                            error = store.updateError.value,
                            onLater = { store.dismissAppUpdate() }
                        )
                      }
        }
        if (!appLock.locked && assignmentTarget != null && store.canOpenPage("Manage Schedule")) {
            val (meeting, slot) = assignmentTarget!!
            val occupiedOnDate = store.data.value?.meetings.orEmpty()
                .filter { it.date == meeting.date }
                .flatMap { dayMeeting -> dayMeeting.slots.filterNot { dayMeeting.type == meeting.type && it.key == slot.key && it.department == slot.department } }
                .mapNotNull { it.personId }
                .toSet()
            val candidates = store.data.value?.people.orEmpty().filter { personEligible(it, meeting, slot) && it.id !in occupiedOnDate }
            AssignmentPickerDialog(meeting, slot, candidates, onDismiss={assignmentTarget=null}, onSelect={person -> scope.launch { try { store.assign(meeting,slot,person); assignmentTarget=null } catch(e:Exception) { snackbar.showSnackbar(e.message ?: "Could not assign person") } } })
        }
        if (createAccountDialog && !appLock.locked) AccountCreateDialog(onDismiss = { createAccountDialog = false }, onCreate = { username, displayName, password, confirm, role, departments, actions -> store.createUser(username, displayName, password, confirm, role, departments, actions); snackbar.showSnackbar("Account created.") })
        confirmDelete?.takeIf { !appLock.locked }?.let { account -> AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete account?") },
            text = { Text("Permanently delete ${account.displayName}'s account? This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; scope.launch { try { store.deleteUser(account) } catch(e:Exception) { snackbar.showSnackbar(e.message ?: "Could not delete account") } } }) { Text("Delete", color=Color(0xFFB45148)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        ) }
      }
    }
}

@Composable
private fun PreviewBottomNavigation(page:String,moreOpen:Boolean,canOpen:(String)->Boolean,onMore:()->Unit,onNavigate:(String)->Unit) {
    val density=LocalDensity.current
    if(WindowInsets.ime.getBottom(density)>0)return
    val compact=LocalConfiguration.current.screenHeightDp<420
    val totalHeight=if(compact)88.dp else 108.dp
    val buttonSize=if(compact)58.dp else 70.dp
    val barHeight=if(compact)66.dp else 80.dp
    val primary=previewPrimaryPage(canOpen)
    val primaryLabel=when(primary){"Manage Schedule"->"Manage";"Schedule Calendar"->"Calendar";else->"Home"}
    val primaryIcon=when(primary){"Manage Schedule"->"calendar-check";"Schedule Calendar"->"calendar-days";else->"house"}
    val regular=listOf(Triple("Home","Home","house"),Triple("Schedule Calendar","Calendar","calendar-days"),Triple("People Directory","People","users-round"))
        .filter{it.first!=primary && canOpen(it.first)}+Triple("More","More","more_horiz")
    val left=regular.take(2)
    val right=regular.drop(2)
    val scale=remember{Animatable(1f)}
    val rotation=remember{Animatable(0f)}
    val lift=remember{Animatable(0f)}
    val scope=rememberCoroutineScope()
    var motionJob by remember{mutableStateOf<kotlinx.coroutines.Job?>(null)}
    val bump=with(density){10.dp.toPx()}
    val top=with(density){(totalHeight-barHeight).toPx()}
    val radius=with(density){27.dp.toPx()}
    val shoulder=with(density){52.dp.toPx()}
    val shape=remember(top,radius,shoulder){androidx.compose.foundation.shape.GenericShape { size,_ ->
        val mid=size.width/2f
        moveTo(radius,top);lineTo(mid-shoulder,top)
        cubicTo(mid-shoulder*.70f,top,mid-shoulder*.70f,0f,mid,0f)
        cubicTo(mid+shoulder*.70f,0f,mid+shoulder*.70f,top,mid+shoulder,top)
        lineTo(size.width-radius,top);quadraticBezierTo(size.width,top,size.width,top+radius)
        lineTo(size.width,size.height-radius);quadraticBezierTo(size.width,size.height,size.width-radius,size.height)
        lineTo(radius,size.height);quadraticBezierTo(0f,size.height,0f,size.height-radius)
        lineTo(0f,top+radius);quadraticBezierTo(0f,top,radius,top);close()
    }}
    Box(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),contentAlignment=Alignment.Center) {
        Box(Modifier.widthIn(max=600.dp).fillMaxWidth().height(totalHeight)) {
            Surface(Modifier.fillMaxSize(),shape=shape,color=MaterialTheme.colorScheme.surfaceContainerHigh,shadowElevation=7.dp,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.6f))) {}
            Row(Modifier.fillMaxWidth().height(barHeight).align(Alignment.BottomCenter).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                fun isCurrent(destination:String)=if(destination=="More") moreOpen || page in listOf("Reports","Task Review","Top Performers","Export Studio","Settings & Accounts") else canonicalPage(page)==destination
                for(index in 0..1) {
                    val item=left.getOrNull(index)
                    if(item==null)Spacer(Modifier.weight(1f)) else PreviewNavigationItem(item.second,item.third,isCurrent(item.first),Modifier.weight(1f),compact){if(item.first=="More")onMore() else onNavigate(item.first)}
                }
                Spacer(Modifier.width(if(compact)64.dp else 78.dp))
                for(index in 0..1) {
                    val item=right.getOrNull(index)
                    if(item==null)Spacer(Modifier.weight(1f)) else PreviewNavigationItem(item.second,item.third,isCurrent(item.first),Modifier.weight(1f),compact){if(item.first=="More")onMore() else onNavigate(item.first)}
                }
            }
            Column(Modifier.align(Alignment.TopCenter).padding(top=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Surface(onClick={
                    motionJob?.cancel()
                    motionJob=scope.launch {
                        launch { scale.animateTo(.85f,tween(70));scale.animateTo(1.1f,spring(dampingRatio=.45f,stiffness=500f));scale.animateTo(1f,spring(dampingRatio=.55f,stiffness=550f)) }
                        launch { rotation.animateTo(18f,tween(90));rotation.animateTo(-12f,tween(110));rotation.animateTo(0f,spring(dampingRatio=.5f,stiffness=450f)) }
                        launch { lift.animateTo(-bump,tween(110));lift.animateTo(0f,spring(dampingRatio=.35f,stiffness=450f)) }
                    }
                    onNavigate(primary)
                },modifier=Modifier.size(buttonSize).graphicsLayer(scaleX=scale.value,scaleY=scale.value,rotationZ=rotation.value,translationY=lift.value).semantics{contentDescription=if(primary=="Manage Schedule")"Manage schedule" else primary;role=Role.Button},shape=CircleShape,color=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,shadowElevation=10.dp,border=androidx.compose.foundation.BorderStroke(4.dp,MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(MaterialSymbol(primaryIcon),null,Modifier.size(if(compact)26.dp else 31.dp))}
                }
                Text(primaryLabel,fontSize=if(compact)9.sp else 10.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.padding(top=4.dp))
            }
        }
    }
}
@Composable
private fun PreviewNavigationItem(label:String,icon:String,selected:Boolean,modifier:Modifier,compact:Boolean,onClick:()->Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick=onClick).semantics{contentDescription=if(label=="More")"More navigation" else label;role=Role.Button}.heightIn(min=48.dp).padding(vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Icon(MaterialSymbol(icon),null,Modifier.size(if(compact)21.dp else 24.dp),if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label,fontSize=if(compact)9.sp else 10.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal,color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        Box(Modifier.size(4.dp).clip(CircleShape).background(if(selected)MaterialTheme.colorScheme.primary else Color.Transparent))
    }
}

@Composable
private fun AppUpdateDialog(update: AppUpdate, downloading: Boolean, progress: Float, error: String?, onUpdate: ()->Unit, onLater: ()->Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .85f).dp
    Dialog(onDismissRequest={if(!downloading)onLater()}) {
        Card(Modifier.widthIn(max=480.dp).fillMaxWidth().heightIn(max=maxHeight),shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer) {
                    Icon(MaterialSymbol("system_update"),null,Modifier.padding(14.dp).size(30.dp),MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text("Update available",fontSize=22.sp,fontWeight=FontWeight.Bold)
                Text("JW Task Scheduler · ${update.version}",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Text("What's new",fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                val notes=update.notes.lineSequence().map { it.trim().trimStart('#').trim().removePrefix("- ").removePrefix("* ").replace("**", "") }.filter { it.isNotBlank() }.toList()
                if(notes.isEmpty()) Text("The latest app improvements are ready to install.",fontSize=13.sp)
                notes.forEach { note ->
                    Row(verticalAlignment=Alignment.Top) {
                        Icon(MaterialSymbol("check_circle"),null,Modifier.padding(top=2.dp).size(17.dp),MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(9.dp)); Text(note,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(downloading) {
                    LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
                    Text("Downloading… ${(progress*100).toInt()}%",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick=onLater,enabled=!downloading,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp)) {
                        Icon(MaterialSymbol("schedule"),null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Later")
                    }
                    Button(onClick=onUpdate,enabled=!downloading,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp)) {
                        Icon(MaterialSymbol("download"),null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Update")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingSplashScreen() {
    val transition = rememberInfiniteTransition(label = "splash-logo")
    val scale by transition.animateFloat(
        initialValue = .98f, targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "logo-breathe"
    )
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painterResource(R.drawable.csp_logo), null,
            modifier = Modifier.size(196.dp).graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(44.dp)),
            contentScale = ContentScale.Fit
        )
        Text("JW Task Scheduler", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 24.dp))
        Text("Preparing your schedule", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
        LinearProgressIndicator(
            modifier = Modifier.width(220.dp).height(4.dp).clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.secondaryContainer
        )
    }
}

@Composable
private fun WorkspaceModeScreen(onLocal: (String) -> Unit, onShared: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.csp_logo), null, modifier = Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)))
        Text("JW Task Scheduler", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 23.sp, modifier = Modifier.padding(top = 14.dp))
        Text("Choose how your congregation stores its schedule", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Local mode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Keep schedules, people, and reviews on this device. No Cloudflare account or internet connection is required. You can export a backup in Settings.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                if (creating) {
                    OutlinedTextField(name, { name = it }, label = { Text("Congregation name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp))
                    Button(onClick = { onLocal(name.trim()) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
                        Icon(MaterialSymbol("database"), null, Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create local workspace")
                    }
                } else {
                    OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
                        Icon(MaterialSymbol("database"), null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Set up local workspace")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Shared sync", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Sign in to a shared workspace to sync this congregation across devices. Your administrator provides the account.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                FilledTonalButton(onClick = onShared, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
                    Icon(MaterialSymbol("cloud"), null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect shared workspace")
                }

            }
        }
        Text("Local data stays on this device until you share a backup.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
    }
}
@Composable
private fun LoginScreen(loading: Boolean, error: String?, onLogin: (String, String) -> Unit, onBackToSetup: () -> Unit) {
    var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.csp_logo), null, modifier = Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)))
        Spacer(Modifier.height(15.dp)); Text("JW Task Scheduler", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(28.dp)); Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(22.dp)) {
                Text("Welcome back", fontWeight = FontWeight.Bold, fontSize = 23.sp); Text("Sign in to your workspace", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp))
                Spacer(Modifier.height(12.dp)); PasswordField(password, { password = it }, "Password", modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(13.dp))
                if (!error.isNullOrBlank()) Text(error, color = Color(0xFFB45148), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                Spacer(Modifier.height(18.dp))
                Button(onClick = { onLogin(username, password) }, enabled = username.isNotBlank() && password.isNotBlank() && !loading, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(13.dp)) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else {
                        Icon(MaterialSymbol("log-in"), null, Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign In", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBackToSetup,
            enabled = !loading,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(MaterialSymbol("arrow-back"), null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("Back to setup")
        }
        Text("Secure mobile sign-in", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun HomeScreen(store: AppStore, onGo: (String) -> Unit, onOpenCalendar: (LocalDate?) -> Unit) {
    val data = store.data.value
    val today = LocalDate.now().toString()
    val next = data?.meetings?.firstOrNull { it.date >= today }
    val total = data?.meetings?.sumOf { it.slots.size } ?: 0
    val filled = data?.meetings?.sumOf { m -> m.slots.count { it.name != null } } ?: 0
    val greeting by produceState(initialValue = timeGreeting()) {
        while (true) { delay(60_000); value = timeGreeting() }
    }
    var selectedDepartment by remember { mutableStateOf<Pair<String, List<Person>>?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · MMMM d")).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp)
        Text("$greeting, ${store.user.value?.displayName ?: ""}", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
        Text("Here’s your congregation at a glance.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(18.dp))
        if (store.canOpenPage("Schedule Calendar")) { if (next != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth().clickable { onOpenCalendar(null) }) {
            Column(Modifier.padding(18.dp)) { Text("NEXT MEETING", color = MaterialTheme.colorScheme.onPrimary.copy(.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Text("${meetingTitle(next)} Meeting", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(top = 8.dp)); Text("${friendlyDate(next.date)} · ${prettyTime(next.time)}", color = MaterialTheme.colorScheme.onPrimary.copy(.85f), fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp)); Text("Open schedule  →", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)) }
        } else EmptyCard("No upcoming meetings found.") }
        val reviewable = data?.meetings.orEmpty().filter { it.date < today }.flatMap { meeting ->
            meeting.slots.filter { it.personId != null }.filter { slot -> store.taskReviews.value.none { it.date == meeting.date && it.type == meeting.type && it.department == slot.department && it.slotKey == slot.key.removePrefix("dept:") } }
        }.size
        if (store.canOpenPage("Reports") || store.user.value?.role=="admin") {
        SectionTitle("Task reviews", if (store.user.value?.role == "admin") "Review" else "${store.taskReviews.value.count { it.status == "completed" }} completed") { if (store.user.value?.role == "admin") onGo("Task Review") else onGo("Reports") }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(15.dp)) {
                Text(if (store.user.value?.role == "admin") "$reviewable past assignments need review" else "Verified service this month", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val reviewed = store.taskReviews.value.filter { it.status != "excused" }
                val completedCount = reviewed.count { it.status == "completed" }
                Text("$completedCount completed · ${store.month.value.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                LinearProgressIndicator(progress = { if (reviewed.isEmpty()) 0f else completedCount.toFloat() / reviewed.size }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        TopPerformerCard(data?.people.orEmpty(), store.taskReviews.value, store.month.value, store)
        }
        if(store.canOpenPage("People Directory")) {
        SectionTitle("Departments", "Directory") { onGo("People Directory") }
        val configuredDepartments = store.workspaceConfig?.departments?.filter { it.active }?.map { it.label to it.id }.orEmpty()
        val departments = configuredDepartments
        departments.forEach { (name, dept) ->
            val members = data?.people.orEmpty().filter { person -> person.status == "active" && when (dept) { "mic" -> person.mic; "av" -> person.audio || person.video; "attendant", "platform" -> person.platform; else -> dept in person.customDepartmentIds } }
            RowCard(icon = when(dept) { "mic" -> "mic"; "av" -> "audio-lines"; "attendant", "platform" -> "podium"; else -> "groups" }, title = name, subtitle = "${members.size} active ${if (members.size == 1) "member" else "members"}", trailing = "View", onClick = { selectedDepartment = name to members })
        }
        }
        if(store.canOpenPage("Schedule Calendar")) {
        SectionTitle("This Week", "Calendar") { onOpenCalendar(null) }
        data?.meetings?.filter { it.date >= today }?.take(3)?.forEach { m -> RowCard("calendar-days", "${meetingTitle(m)} · ${friendlyDate(m.date)}", prettyTime(m.time), "→") { onOpenCalendar(runCatching { LocalDate.parse(m.date) }.getOrNull()) } }
        }
        Spacer(Modifier.height(18.dp))
    }
    if(store.canOpenPage("People Directory")) selectedDepartment?.let { (name, members) ->
        DepartmentMembersDialog(name, members, store, onDismiss = { selectedDepartment = null })
    }
}

@Composable
private fun TopPerformerCard(people: List<Person>, reviews: List<TaskReview>, month: YearMonth, store: AppStore) {
    val options = store.workspaceConfig?.departments?.filter { it.active }?.map { it.id to it.label }.orEmpty()
    var department by remember(options) { mutableStateOf(options.firstOrNull()?.first ?: "mic") }
    var period by remember { mutableStateOf("Monthly") }
    var loadingOverall by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == department }?.second ?: "Department"

    LaunchedEffect(period, store.allTaskReviews.value) {
        if (period == "Overall" && store.allTaskReviews.value == null) {
            loadingOverall = true
            runCatching { store.loadAllTaskReviews() }
            loadingOverall = false
        }
    }

    val periodReviews = if (period == "Overall") {
        store.allTaskReviews.value.orEmpty()
    } else {
        reviews.filter { runCatching { YearMonth.from(LocalDate.parse(it.date)) == month }.getOrDefault(false) }
    }
    val ranking = periodReviews.filter { it.department == department && it.status != "excused" }
        .groupBy { it.personId }
        .mapNotNull { (id, rows) ->
            val person = people.firstOrNull { it.id == id } ?: return@mapNotNull null
            val done = rows.count { it.status == "completed" }
            val total = rows.size
            if (total == 0) null else Triple(person, done, done * 100 / total)
        }.sortedWith(compareByDescending<Triple<Person, Int, Int>> { it.second }.thenByDescending { it.third })
    Column {
        SectionTitle("Top performer", if (period == "Overall") "Overall" else month.format(DateTimeFormatter.ofPattern("MMM yyyy"))) { }
        Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(13.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterPill("Overall", period == "Overall") { period = "Overall" }
                    FilterPill("Monthly", period == "Monthly") { period = "Monthly" }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    options.forEach { (key, text) -> FilterPill(text, department == key) { department = key } }
                }
                val top = ranking.firstOrNull()
                if (loadingOverall && period == "Overall") {
                    Text("Loading review history…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 13.dp))
                } else if (top == null) {
                    Text("No reviewed assignments yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 13.dp))
                } else Row(Modifier.fillMaxWidth().padding(top = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    LaunchedEffect(top.first.id, top.first.avatarUpdatedAt) { if (top.first.avatarUpdatedAt != null) store.loadPersonPhoto(top.first.id) }
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.BottomEnd) {
                        Avatar(top.first.name, 48.dp, store.personPhotos.value[top.first.id])
                        Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Icon(MaterialSymbol("trophy"), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp)) }
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(top.first.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("$label · ${top.second} completed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${top.third}%", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
private fun timeGreeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun DepartmentMembersDialog(title: String, members: List<Person>, store: AppStore, onDismiss: () -> Unit) {
    val admin = store.user.value?.role == "admin"
    var editing by remember { mutableStateOf<Person?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(.92f).heightIn(max = 600.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("DEPARTMENT", fontSize = 9.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text("${members.size} active ${if (members.size == 1) "member" else "members"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = onDismiss) { Icon(MaterialSymbol("close"), "Close") }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                if (members.isEmpty()) Text("No active personnel in this department.", Modifier.padding(vertical = 18.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    members.forEach { person ->
                        LaunchedEffect(person.id, person.avatarUpdatedAt) { if (person.avatarUpdatedAt != null) store.loadPersonPhoto(person.id) }
                        Row(Modifier.fillMaxWidth().clickable(enabled = admin) { editing = person }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(person.name, 40.dp, store.personPhotos.value[person.id])
                            Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(person.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text("Group ${person.group} · ${person.status.replaceFirstChar(Char::uppercase)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (admin) Text("Edit", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        if (person != members.last()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(.7f))
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
    editing?.let { person -> PersonEditorDialog(
        localConfig = store.workspaceConfig,
        person = person,
        photo = store.personPhotos.value[person.id],
        completedTasks = store.taskReviews.value.filter { it.personId == person.id && it.status == "completed" }.sortedByDescending { it.date },
        completedMonth = store.month.value,
        onDismiss = { editing = null },
        onSave = { updated -> store.savePerson(updated) },
        onDelete = { id -> store.deletePerson(id) },
        onPhotoSelected = { id, uri -> store.savePersonPhoto(id, uri) },
        onRemovePhoto = { id -> store.removePersonPhoto(id) }
    ) }
}

@Composable
private fun ScheduleCalendarScreen(store: AppStore) {
    val month = store.month.value
    val meetings = store.data.value?.meetings.orEmpty().filter { runCatching { YearMonth.from(LocalDate.parse(it.date)) == month }.getOrDefault(false) }
    val meetingsByDate = remember(meetings) { meetings.groupBy { LocalDate.parse(it.date) } }
    var selectedDate by remember(month) {
        mutableStateOf(store.calendarRequestedDate.value?.takeIf { YearMonth.from(it) == month }
            ?: LocalDate.now().takeIf { YearMonth.from(it) == month }
            ?: month.atDay(1))
    }
    var modalDate by remember(month) { mutableStateOf<LocalDate?>(null) }
    val scope = rememberCoroutineScope()
    val firstDate = remember(month) { month.atDay(1) }
    val calendarCells = remember(month) {
        buildList<LocalDate?> {
            repeat(firstDate.dayOfWeek.value % 7) { add(null) }
            for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
        }.chunked(7)
    }
    val requestedDate = store.calendarRequestedDate.value
    LaunchedEffect(requestedDate, month, meetingsByDate.keys.toList()) {
        val requested = store.calendarRequestedDate.value ?: return@LaunchedEffect
        if (YearMonth.from(requested) == month) {
            selectedDate = requested
            if (store.calendarShouldOpenDate.value && !meetingsByDate[requested].isNullOrEmpty()) modalDate = requested
            store.calendarRequestedDate.value = null
            store.calendarShouldOpenDate.value = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { scope.launch { runCatching { store.changeMonth(month.minusMonths(1)) } } }) { Icon(MaterialSymbol("chevron-left"), "Previous month") }
                Text(month.format(DateTimeFormatter.ofPattern("MMM yyyy")), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { scope.launch { runCatching { store.changeMonth(month.plusMonths(1)) } } }) { Icon(MaterialSymbol("chevron-right"), "Next month") }
            }
        }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT").forEach { day ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(day, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Spacer(Modifier.height(7.dp))
                calendarCells.forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        (0 until 7).forEach { index ->
                            val date = week.getOrNull(index)
                            if (date == null) {
                                Spacer(Modifier.weight(1f).height(48.dp))
                            } else {
                                val hasMeeting = !meetingsByDate[date].isNullOrEmpty()
                                val selected = selectedDate == date
                                Column(
                                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            selectedDate = date
                                            if (hasMeeting) modalDate = date
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(Modifier.size(30.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent), contentAlignment = Alignment.Center) {
                                        Text(date.dayOfMonth.toString(), fontSize = 12.sp, fontWeight = if (selected || hasMeeting) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    }
                                    Box(Modifier.padding(top = 1.dp).size(5.dp).clip(CircleShape).background(if (hasMeeting) MaterialTheme.colorScheme.primary else Color.Transparent))
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(start = 5.dp, top = 9.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Text("  Meeting scheduled", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        selectedDate?.let { date ->
            val selectedMeetings = meetingsByDate[date].orEmpty()
            Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(MaterialSymbol("calendar-days"), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (selectedMeetings.isEmpty()) "No meeting scheduled" else selectedMeetings.joinToString(" · ") { prettyTime(it.time) }, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (selectedMeetings.isNotEmpty()) TextButton(onClick = { modalDate = date }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("View", fontSize = 11.sp) }
                }
            }
        } ?: Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
            Text("Choose a date. Meeting dates are marked in green.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (store.loading.value) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
    }

    modalDate?.let { date ->
        val dayMeetings = meetingsByDate[date].orEmpty()
        Dialog(onDismissRequest = { modalDate = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
                val dialogMaxHeight = maxHeight
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).clickable { modalDate = null })
                Card(
                    Modifier.fillMaxWidth(.94f).heightIn(max = maxHeight * .92f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 12.dp, end = 9.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                if (dayMeetings.isEmpty()) Text("No meetings", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                            }
                            IconButton(onClick = { modalDate = null }) { Icon(MaterialSymbol("close"), "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(Modifier.fillMaxWidth().heightIn(max = dialogMaxHeight * .72f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            dayMeetings.forEach { meeting ->
                                Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                    Column {
                                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(MaterialSymbol("calendar-days"), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                                Text("${meetingTitle(meeting)} Meeting", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text(prettyTime(meeting.time), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                            }
                                        }
                                        meeting.slots.forEachIndexed { index, slot ->
                                            val person = store.data.value?.people?.firstOrNull { it.id == slot.personId }
                                            Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                                                    Icon(MaterialSymbol(slotIcon(slot)), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                                                }
                                                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                                    Text(slot.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(slot.name ?: "Unassigned", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                                                }
                                                if (person != null) Avatar(person.name, 30.dp, store.personPhotos.value[person.id])
                                            }
                                            if (index != meeting.slots.lastIndex) HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { modalDate = null }, modifier = Modifier.align(Alignment.End).padding(end = 12.dp)) { Text("Close") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleScreen(store: AppStore, onAssign: (Meeting, Slot) -> Unit) {
    val data = store.data.value
    var filter by remember { mutableStateOf("All Departments") }
    var openOnly by remember { mutableStateOf(false) }
    val month = store.month.value
    var weekStart by remember(month) { mutableStateOf(exportWeeks(month).firstOrNull { LocalDate.now() in it.first..it.second }?.first ?: firstExportWeek(month)) }
    var monthPickerOpen by remember { mutableStateOf(false) }
    var weekPickerOpen by remember { mutableStateOf(false) }
    var focusedMeeting by remember(weekStart) { mutableStateOf<String?>(null) }
    var expandedMeetings by remember(month, weekStart) { mutableStateOf<Set<String>>(emptySet()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val weeks = remember(month) { exportWeeks(month) }
    val selectedWeek = weeks.firstOrNull { it.first == weekStart } ?: weeks.first()
    val weekNumber = (weeks.indexOf(selectedWeek) + 1).coerceAtLeast(1)
    val localDepartments = store.workspaceConfig?.departments?.filter { it.active }.orEmpty()
    val departmentFilters = localDepartments.map { it.label }
    fun departmentName(id: String) = localDepartments.firstOrNull { it.id == id }?.label ?: deptLabel(id)
    val meetingsThisWeek = data?.meetings.orEmpty().filter { meeting ->
        val date = LocalDate.parse(meeting.date)
        date in selectedWeek.first..selectedWeek.second && YearMonth.from(date) == month
    }.map { meeting -> meeting.copy(slots = meeting.slots.filter { filter == "All Departments" || departmentName(it.department) == filter }) }
        .filter { it.slots.isNotEmpty() }
        .sortedBy { it.date }
    val rows = meetingsThisWeek.filter { meeting -> !openOnly || meeting.slots.any { !it.isAssigned() } }
    val firstMeetingKey = rows.firstOrNull()?.let { it.date + it.type }
    val expandedKeys = expandedMeetings
    LaunchedEffect(month) { if (store.data.value == null) try { store.load() } catch (_: Exception) { } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ExportPickerField(Modifier.weight(.9f), "Month", month.format(DateTimeFormatter.ofPattern("MMM yyyy")), "calendar-days") { monthPickerOpen = true }
                ExportPickerField(Modifier.weight(1.25f), "Week $weekNumber", "${selectedWeek.first.format(DateTimeFormatter.ofPattern("MMM d"))} – ${selectedWeek.second.format(DateTimeFormatter.ofPattern("MMM d"))}", null) { weekPickerOpen = true }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { meeting ->
                    val open = meeting.slots.count { !it.isAssigned() }
                    val key = meeting.date + meeting.type
                    val selected = focusedMeeting == key || (focusedMeeting == null && key == firstMeetingKey)
                    Surface(onClick = {
                        focusedMeeting = key
                        expandedMeetings = expandedKeys + key
                        scope.launch { rows.indexOfFirst { it.date + it.type == key }.takeIf { it >= 0 }?.let { listState.animateScrollToItem(it) } }
                    }, shape = RoundedCornerShape(13.dp), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(MaterialSymbol("calendar-days"), null, Modifier.size(15.dp), tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                            Text("${meetingTitle(meeting).take(3)} ${LocalDate.parse(meeting.date).dayOfMonth}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            if (open > 0) Text("$open open", fontSize = 9.sp, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (rows.isEmpty()) Text("No meetings in this week", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("All Departments") + departmentFilters).forEach { label -> FilterPill(label, filter == label) { filter = label; expandedMeetings = emptySet(); focusedMeeting = null } }
                }
                Spacer(Modifier.width(6.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().padding(start = 11.dp, end = 7.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(MaterialSymbol("filter"), null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Open assignments", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("Show only unassigned positions", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = openOnly,
                            onCheckedChange = { openOnly = it; expandedMeetings = emptySet(); focusedMeeting = null },
                            modifier = Modifier.height(30.dp),
                            thumbContent = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
            }
            val weekSlots = meetingsThisWeek.flatMap { it.slots }
            val filled = weekSlots.count { it.isAssigned() }
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(MaterialSymbol("calendar-check"), null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$filled of ${weekSlots.size} assignments filled", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        LinearProgressIndicator(progress = { if (weekSlots.isEmpty()) 0f else filled.toFloat() / weekSlots.size }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(4.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        val canEdit: (String) -> Boolean = { department -> canEditSlot(store.user.value, department) }
        if (rows.isEmpty() && !store.loading.value) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (openOnly) "No open assignments this week." else "No meetings in this week.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        else LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.date + it.type }) { meeting ->
                val key = meeting.date + meeting.type
                WeeklyMeetingCard(meeting, expanded = key in expandedKeys, openOnly = openOnly, canEdit = canEdit,
                    onToggle = {
                        val current = expandedMeetings ?: setOfNotNull(firstMeetingKey)
                        expandedMeetings = if (key in current) current - key else current + key
                    }, onAssign = onAssign)
            }
        }
    }
    if (monthPickerOpen) ExportMonthPickerDialog(month, onDismiss = { monthPickerOpen = false }, onSelect = { next ->
        monthPickerOpen = false
        store.month.value = next
        store.data.value = null
        weekStart = exportWeeks(next).firstOrNull { LocalDate.now() in it.first..it.second }?.first ?: firstExportWeek(next)
        expandedMeetings = emptySet()
        focusedMeeting = null
        scope.launch { runCatching { store.load() } }
    })
    if (weekPickerOpen) WeekPickerDialog(month, weeks, selectedWeek.first, onDismiss = { weekPickerOpen = false }, onSelect = { next ->
        weekStart = next
        expandedMeetings = emptySet()
        focusedMeeting = null
        weekPickerOpen = false
    })
}

private fun firstSunday(month: YearMonth): LocalDate {
    val first = month.atDay(1)
    return first.minusDays((first.dayOfWeek.value % 7).toLong())
}

@Composable
private fun WeeklyMeetingCard(meeting: Meeting, expanded: Boolean, openOnly: Boolean, canEdit: (String) -> Boolean, onToggle: () -> Unit, onAssign: (Meeting, Slot) -> Unit) {
    val open = meeting.slots.count { !it.isAssigned() }
    val visibleSlots = if (openOnly) meeting.slots.filter { !it.isAssigned() } else meeting.slots
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = .34f) else MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).background(if (expanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(MaterialSymbol("calendar-days"), null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("${meetingTitle(meeting)}, ${friendlyDate(meeting.date)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${prettyTime(meeting.time)} · ${meeting.slots.count { it.isAssigned() }}/${meeting.slots.size} filled", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                if (open > 0) Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) { Text("$open open", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.width(5.dp)); Icon(MaterialSymbol("chevron-down"), null, Modifier.size(18.dp).rotate(if (expanded) 180f else 0f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) visibleSlots.forEachIndexed { index, slot ->
                Row(Modifier.fillMaxWidth().clickable(enabled = canEdit(slot.department) && !slot.locked) { onAssign(meeting, slot) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(31.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                        Icon(MaterialSymbol(slotIcon(slot)), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(slot.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(slot.name ?: if (slot.isAssigned()) "Assigned member" else "Unassigned", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (slot.isAssigned()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 1.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (slot.locked) Icon(MaterialSymbol("lock-keyhole"), "Protected", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    else if (canEdit(slot.department)) Text(if (!slot.isAssigned()) "Assign" else "Change", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                }
                if (index < visibleSlots.lastIndex) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
            }
        }
    }
}

@Composable
private fun PeopleScreen(store: AppStore) {
    val people = store.data.value?.people.orEmpty()
    val admin = store.user.value?.role == "admin"
    var query by remember { mutableStateOf("") }; var filter by remember { mutableStateOf("All Status") }
    var editorOpen by remember { mutableStateOf(false) }; var editingPerson by remember { mutableStateOf<Person?>(null) }
    val shown = people.filter { it.name.contains(query, true) && (filter == "All Status" || if (filter == "Active") it.status == "active" else it.status != "active") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("DIRECTORY", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                Text("People Directory", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (admin) FilledTonalButton(onClick = { editingPerson = null; editorOpen = true }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Icon(MaterialSymbol("user-plus"), null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Add person", fontSize = 11.sp)
            }
        }
        Text("${people.size} personnel · ${workspaceGroupSummary(store.workspaceConfig?.groupCount)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        OutlinedTextField(query, { query = it }, placeholder = { Text("Search people") }, leadingIcon = { Icon(MaterialSymbol("search"), null) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, shape = RoundedCornerShape(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp)) { listOf("All Status", "Active", "Inactive / unavailable").forEach { label -> FilterPill(label, filter == label) { filter = label } } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            items(shown, key = { it.id }) { person ->
                RowCard("user-round", person.name, "Group ${person.group} · ${person.status.replaceFirstChar(Char::uppercase)}${if (person.noThursday) " · Thursday exempt" else ""}", person.eligibleSummary(store.workspaceConfig), photoName = person.name, photo = store.personPhotos.value[person.id]) {
                    if (admin) { editingPerson = person; editorOpen = true }
                }
            }
        }
    }
    if (editorOpen && admin) PersonEditorDialog(
        localConfig = store.workspaceConfig,
        person = editingPerson,
        photo = editingPerson?.let { store.personPhotos.value[it.id] },
        completedTasks = editingPerson?.let { person ->
            store.taskReviews.value.filter { it.personId == person.id && it.status == "completed" }
                .sortedByDescending { it.date }
        }.orEmpty(),
        completedMonth = store.month.value,
        onDismiss = { editorOpen = false },
        onSave = { person -> store.savePerson(person) },
        onDelete = { id -> store.deletePerson(id) },
        onPhotoSelected = { id, uri -> store.savePersonPhoto(id, uri) },
        onRemovePhoto = { id -> store.removePersonPhoto(id) }
    )
}

@Composable
private fun PersonEditorDialog(localConfig: LocalWorkspaceConfig?, person: Person?, photo: ImageBitmap?, completedTasks: List<TaskReview>, completedMonth: YearMonth, onDismiss: () -> Unit, onSave: suspend (Person) -> Unit, onDelete: suspend (String) -> Unit, onPhotoSelected: suspend (String, Uri) -> Unit, onRemovePhoto: suspend (String) -> Unit) {
    val isNew = person == null
    var name by remember(person?.id) { mutableStateOf(person?.name ?: "") }
    val groupCount = (localConfig?.groupCount ?: 0).coerceAtLeast(0)
    var group by remember(person?.id, localConfig?.groupCount) { mutableIntStateOf(person?.group ?: 1) }
    var customDepartments by remember(person?.id, localConfig) { mutableStateOf(person?.customDepartmentIds ?: emptySet()) }
    var status by remember(person?.id) { mutableStateOf(person?.status ?: "active") }
    var noThursday by remember(person?.id) { mutableStateOf(person?.noThursday ?: false) }
    var mic by remember(person?.id) { mutableStateOf(person?.mic ?: false) }
    var audio by remember(person?.id) { mutableStateOf(person?.audio ?: false) }
    var video by remember(person?.id) { mutableStateOf(person?.video ?: false) }
    var platform by remember(person?.id) { mutableStateOf(person?.platform ?: false) }
    var groupPickerOpen by remember(person?.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var photoBusy by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && person != null) scope.launch { photoBusy = true; try { onPhotoSelected(person.id, uri); photoError = null } catch (e: Exception) { photoError = e.message ?: "Could not upload photo." } finally { photoBusy = false } }
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(.94f).heightIn(max = 680.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isNew) "NEW PERSON" else "PERSONNEL DETAILS", fontSize = 9.sp, letterSpacing = 1.1.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(if (isNew) "Add person" else "Personnel profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                    }
                    IconButton(onClick = onDismiss, enabled = !saving) { Icon(MaterialSymbol("close"), "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isNew) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(name, 56.dp, photo)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("Profile photo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Shown in department member lists", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { photoPicker.launch("image/*") }, enabled = !photoBusy) { Text(if (photo == null) "Add photo" else "Change photo", fontSize = 11.sp) }
                                    if (photo != null) TextButton(onClick = { scope.launch { photoBusy = true; try { person?.let { onRemovePhoto(it.id) }; photoError = null } catch (e: Exception) { photoError = e.message ?: "Could not remove photo." } finally { photoBusy = false } } }, enabled = !photoBusy) { Text("Remove", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                                }
                            }
                            if (photoBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        photoError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 10.sp) }
                        if (completedTasks.isNotEmpty()) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(MaterialSymbol("award"), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                            Text("Completed assignments", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text(completedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(completedTasks.size.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    completedTasks.take(4).forEach { task ->
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(task.slotKey.removePrefix("dept:").replace('_', ' ').replaceFirstChar(Char::uppercase), modifier = Modifier.weight(1f), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text("${meetingLabel(task.type).replaceFirstChar(Char::uppercase)} · ${friendlyDate(task.date)}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (completedTasks.size > 4) Text("+${completedTasks.size - 4} more completed", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                }
                            }
                        } else if (!isNew) {
                            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(MaterialSymbol("award"), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text("Completed assignments", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("No completed tasks in ${completedMonth.format(DateTimeFormatter.ofPattern("MMMM"))}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("0", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    OutlinedTextField(name, { name = it; error = null }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("GROUP", fontSize = 10.sp, letterSpacing = .9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(onClick = { if (groupCount > 0) groupPickerOpen = true }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                                    Text(group.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if(groupCount==0)"Groups not configured" else "Group $group", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(if(groupCount==0)"Configure groups in Workspace settings first." else groupAvailability(group, noThursday, localConfig), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if(groupCount>0) {
                                    Text("Change", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(5.dp)); Icon(MaterialSymbol("chevron-right"), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("DEPARTMENTS & ROLES", fontSize = 10.sp, letterSpacing = .9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (localConfig != null) {
                            localConfig.departments.filter { it.active }.forEach { dept ->
                                val checked = when (dept.id) { "mic" -> mic; "av" -> audio || video; "attendant" -> platform; else -> dept.id in customDepartments }
                                PersonRoleToggle(dept.label, checked) { enabled ->
                                    when (dept.id) { "mic" -> mic = enabled; "av" -> { audio = enabled; video = enabled }; "attendant" -> platform = enabled; else -> customDepartments = if (enabled) customDepartments + dept.id else customDepartments - dept.id }
                                }
                            }
                        } else {
                            PersonRoleToggle("Mic Men", mic) { mic = it }
                            PersonRoleToggle("Audio Operator", audio) { audio = it }
                            PersonRoleToggle("Video Operator", video) { video = it }
                            PersonRoleToggle("Platform", platform) { platform = it }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("STATUS", fontSize = 10.sp, letterSpacing = .9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("active" to "Active", "inactive" to "Inactive", "unavailable" to "Unavailable").forEach { (key, label) -> FilterPill(label, status == key) { status = key } }
                        }
                    }
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            PreferenceRow("event-busy", "Exempt from Thursday assignments", "Exclude from Thursday assignments") {
                                Switch(checked = noThursday, onCheckedChange = { noThursday = it }, colors = appSwitchColors())
                            }
                            Text("This exemption applies to every department.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!isNew) TextButton(onClick = { confirmDelete = true }, enabled = !saving) { Icon(MaterialSymbol("trash-2"), null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(4.dp)); Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel", fontSize = 12.sp) }
                    Spacer(Modifier.width(5.dp))
                    Button(onClick = {
                        val updated = Person(person?.id ?: "", name.trim(), group, status, noThursday, mic, audio, video, platform,
                            person?.gate ?: false, person?.remarks.orEmpty(), person?.allowDuplicate ?: false, person?.ignoreMeetingConflict ?: false,
                            person?.neverAutoAssign ?: false, person?.assignmentPriority ?: "normal", person?.avatarUpdatedAt, customDepartments)
                        when {
                            groupCount == 0 -> error = "Set up at least one group in Workspace settings before adding personnel."
                            group !in workspaceGroupNumbers(groupCount) -> error = "Choose a group from the current Workspace setup."
                            name.trim().length < 2 -> error = "Enter a name with at least 2 characters."
                            !mic && !audio && !video && !platform && customDepartments.isEmpty() -> error = "Select at least one department role."
                            else -> { saving = true; error = null; scope.launch { try { onSave(updated); onDismiss() } catch (e: Exception) { error = e.message ?: "Could not save person." } finally { saving = false } } }
                        }
                    }, enabled = !saving, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 17.dp, vertical = 9.dp)) {
                        if (saving) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp) else Text(if (isNew) "Add person" else "Save changes", fontSize = 11.sp)
                    }
                }
            }
        }
    }
    if (groupPickerOpen && groupCount > 0) GroupPickerDialog(selected = group, groupCount = groupCount, onDismiss = { groupPickerOpen = false }, onSelect = { group = it; groupPickerOpen = false })
    if (confirmDelete && person != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${person.name}?") },
        text = { Text("This removes the person from the directory. Existing assignments for this person will become unassigned.") },
        confirmButton = { TextButton(onClick = { saving = true; scope.launch { try { onDelete(person.id); confirmDelete = false; onDismiss() } catch (e: Exception) { error = e.message ?: "Could not delete person."; confirmDelete = false } finally { saving = false } } }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}

@Composable
private fun GroupPickerDialog(selected: Int, groupCount: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(.88f).heightIn(max=(LocalConfiguration.current.screenHeightDp*.85f).dp), shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Icon(MaterialSymbol("users-round"), null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Choose a group", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Select the personnel’s group", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = onDismiss) { Icon(MaterialSymbol("close"), "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    workspaceGroupNumbers(groupCount).toList().chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { number ->
                                val isSelected = number == selected
                                Surface(onClick = { onSelect(number) }, modifier = Modifier.weight(1f).height(58.dp), shape = RoundedCornerShape(13.dp), color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(number.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                            repeat(4-row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Text("Groups follow the current Workspace setup.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

private fun groupAvailability(group: Int, noThursday: Boolean, config: LocalWorkspaceConfig?): String {
    val labels=config?.meetings.orEmpty().filter { it.active && group in it.eligibleGroups && !(noThursday && it.dayOfWeek==4) }.map { it.label }.distinct()
    return if(labels.isEmpty()) "No eligible meeting days" else labels.joinToString(" · ")
}

@Composable
private fun PersonRoleToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TaskReviewScreen(store: AppStore, onViewTopPerformers: () -> Unit, onReview: suspend (Meeting, Slot, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var weekStart by remember { mutableStateOf(LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())) }
    var meetings by remember { mutableStateOf<List<Meeting>>(emptyList()) }
    var reviews by remember { mutableStateOf<List<TaskReview>>(emptyList()) }
    var selected by remember { mutableStateOf<Pair<Meeting, Slot>?>(null) }
    var expanded by remember(weekStart) { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val weekEnd = weekStart.plusDays(6)
    val weekLabel = "${weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} – ${weekEnd.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
    val statusBySlot = reviews.associateBy { "${it.date}|${it.type}|${it.department}|${it.slotKey}" }
    LaunchedEffect(weekStart, refresh) {
        loading = true
        try {
            val result = store.loadReviewRange(weekStart, weekEnd)
            meetings = result.first.filter { it.date < LocalDate.now().toString() && it.slots.any { slot -> slot.personId != null } }.sortedWith(compareBy({ it.date }, { mapOf("thursday" to 0, "saturday" to 1, "sunday" to 2)[it.type] ?: 9 }))
            reviews = result.second
            loadError = false
        } catch (_: Exception) { meetings = emptyList(); reviews = emptyList(); loadError = true }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onViewTopPerformers) { Icon(MaterialSymbol("trophy"), null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("View top performers", fontSize = 11.sp) }
            }
            Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { weekStart = weekStart.minusDays(7) }) { Icon(MaterialSymbol("chevron-left"), "Previous week") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("WEEKLY REVIEW", fontSize = 9.sp, letterSpacing = .9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(weekLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    val canAdvance = weekStart.plusDays(7) <= LocalDate.now()
                    IconButton(onClick = { if (canAdvance) weekStart = weekStart.plusDays(7) }, enabled = canAdvance) { Icon(MaterialSymbol("chevron-right"), "Next week") }
                }
            }
            Text("Review assignments for this week. Excused tasks are excluded from performance totals.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 9.dp))
        }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (loadError) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Could not load this week. Check your connection and try again.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        else if (meetings.none { meeting -> meeting.slots.any { it.personId != null && statusBySlot["${meeting.date}|${meeting.type}|${it.department}|${it.key.removePrefix("dept:")}"] == null } }) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(MaterialSymbol("task-alt"), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                Text("No Weekly Task to Review", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                Text("All assigned tasks for this week have been reviewed.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        else LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(meetings, key = { "${it.date}|${it.type}" }) { meeting ->
                val assigned = meeting.slots.filter { it.personId != null }
                val reviewed = assigned.count { slot -> statusBySlot["${meeting.date}|${meeting.type}|${slot.department}|${slot.key.removePrefix("dept:")}"] != null }
                val key = "${meeting.date}|${meeting.type}"
                val isExpanded = key in expanded
                Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable { expanded = if (isExpanded) expanded - key else expanded + key }.background(if (isExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(MaterialSymbol("calendar-check"), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text("${meetingTitle(meeting)}, ${friendlyDate(meeting.date)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${prettyTime(meeting.time)} · $reviewed/${assigned.size} reviewed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(MaterialSymbol(if (isExpanded) "chevron-up" else "chevron-down"), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isExpanded) assigned.forEachIndexed { index, slot ->
                            val review = statusBySlot["${meeting.date}|${meeting.type}|${slot.department}|${slot.key.removePrefix("dept:")}"]
                            Row(Modifier.fillMaxWidth().clickable { selected = meeting to slot }.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(slot.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(slot.name ?: "Assigned personnel", fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
                                }
                                val statusText = when (review?.status) { "completed" -> "Completed"; "not_completed" -> "Not completed"; "excused" -> "Excused"; else -> "Review" }
                                Surface(shape = RoundedCornerShape(50), color = if (review == null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(statusText, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 10.sp, color = if (review == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (index != assigned.lastIndex) HorizontalDivider(Modifier.padding(start = 13.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
    selected?.let { (meeting, slot) ->
        val person = store.data.value?.people?.firstOrNull { it.id == slot.personId }
        LaunchedEffect(person?.id, person?.avatarUpdatedAt) { if (person?.avatarUpdatedAt != null) store.loadPersonPhoto(person.id) }
        val currentStatus = statusBySlot["${meeting.date}|${meeting.type}|${slot.department}|${slot.key.removePrefix("dept:")}"]?.status
        Dialog(onDismissRequest = { selected = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(
                modifier = Modifier.fillMaxWidth(.92f).heightIn(max = 560.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                            Icon(MaterialSymbol("clipboard-check"), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Review task", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("Select the task outcome", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { selected = null }, modifier = Modifier.size(36.dp)) { Icon(MaterialSymbol("close"), "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Surface(Modifier.fillMaxWidth().padding(top = 17.dp, bottom = 17.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(slot.name ?: "?", 44.dp, person?.let { store.personPhotos.value[it.id] })
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Text(slot.name ?: "Personnel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("${meetingTitle(meeting)} · ${friendlyDate(meeting.date)} · ${prettyTime(meeting.time)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                                Text(slot.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text("TASK STATUS", fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    val options = listOf(
                        Triple("completed", "Completed", "The assigned task was fulfilled."),
                        Triple("not_completed", "Not completed", "The assigned task was not fulfilled."),
                        Triple("excused", "Excused", "Exclude this task from performance totals.")
                    )
                    options.forEach { (status, title, description) ->
                        val selectedStatus = currentStatus == status
                        val accent = when (status) { "completed" -> Color(0xFF4E8B62); "not_completed" -> Color(0xFFB75C55); else -> MaterialTheme.colorScheme.onSurfaceVariant }
                        Surface(
                            onClick = { selected = null; scope.launch { onReview(meeting, slot, status); refresh++ } },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(15.dp),
                            color = if (selectedStatus) accent.copy(alpha = .13f) else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(if (selectedStatus) 1.5.dp else 1.dp, if (selectedStatus) accent.copy(alpha = .7f) else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                                    Icon(MaterialSymbol(when (status) { "completed" -> "circle-check"; "not_completed" -> "circle-x"; else -> "circle-minus" }), null, tint = accent, modifier = Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
                                }
                                if (selectedStatus) Icon(MaterialSymbol("check"), "Current status", tint = accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    TextButton(onClick = { selected = null }, modifier = Modifier.align(Alignment.End).padding(top = 5.dp)) { Text("Cancel") }
                }
            }
        }
    }
}

private data class PerformanceRow(val person: Person, val completed: List<TaskReview>, val missed: List<TaskReview>, val percent: Int)

@Composable
private fun TopPerformersScreen(store: AppStore) {
    var reviews by remember { mutableStateOf<List<TaskReview>>(emptyList()) }
    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var expandedPeople by remember { mutableStateOf<Set<String>>(emptySet()) }
    val detailsStart = LocalDate.now().minusDays(20)
    val allowedDepartments = remember(store.user.value?.permissions) {
        val user = store.user.value
        if (user?.role != "assistant") setOf("mic", "av", "attendant") else runCatching {
            val rows = JSONObject(user.permissions).optJSONArray("departments")
            if (rows == null) setOf("mic", "av", "attendant") else (0 until rows.length()).map { rows.optString(it) }.toSet()
        }.getOrDefault(emptySet())
    }
    LaunchedEffect(store.page.value, refresh) {
        loading = true
        loadError = null
        try {
            reviews = store.loadAllTaskReviews(forceRefresh = true)
            val personMap = (store.data.value?.people.orEmpty()).associateBy { it.id }.toMutableMap()
            store.loadMonthForExport(YearMonth.now()).people.forEach { personMap[it.id] = it }
            people = personMap.values.toList()
        } catch (e: Exception) { reviews = emptyList(); loadError = e.message ?: "Could not load performance history." }
        loading = false
    }
    val relevant = reviews.filter { it.department in allowedDepartments && it.status != "excused" }
    val grouped = relevant.groupBy { it.personId }
        .mapNotNull { (id, rows) ->
            val person = people.firstOrNull { it.id == id } ?: return@mapNotNull null
            val completed = rows.filter { it.status == "completed" }
            val missed = rows.filter { it.status == "not_completed" }
            PerformanceRow(person, completed, missed, if (rows.isEmpty()) 0 else completed.size * 100 / rows.size)
        }
        .sortedWith(compareByDescending<PerformanceRow> { it.completed.size }.thenBy { it.person.name.lowercase() })

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (store.user.value?.role == "admin") TextButton(onClick = { store.page.value = "Task Review" }) { Icon(MaterialSymbol("arrow-back"), null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Task Review", fontSize = 11.sp) }
            }
            Text("Overall completed assignments rank personnel. Tap a name for recent task details; counts include older reviews and exclude excused tasks.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
        }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (loadError != null) Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(loadError!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp); TextButton(onClick = { refresh++ }) { Text("Retry") } }
        else if (grouped.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No reviewed tasks yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        else LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(grouped, key = { it.person.id }) { indexed ->
                val person = indexed.person
                val completedTasks = indexed.completed
                val missedTasks = indexed.missed
                val percent = indexed.percent
                val rank = grouped.indexOf(indexed) + 1
                val needsSupport = rank > 3 && missedTasks.size > completedTasks.size
                LaunchedEffect(person.id, person.avatarUpdatedAt) { if (person.avatarUpdatedAt != null) store.loadPersonPhoto(person.id) }
                val expanded = person.id in expandedPeople
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = .4f) else MaterialTheme.colorScheme.outlineVariant)) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable { expandedPeople = if (expanded) expandedPeople - person.id else expandedPeople + person.id }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(46.dp), contentAlignment = Alignment.BottomEnd) {
                                Avatar(person.name, 42.dp, store.personPhotos.value[person.id])
                                if (rank <= 3) Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text("$rank", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(person.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Group ${person.group} · ${completedTasks.size} completed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                val rankLabel = when (rank) { 1 -> "1st place"; 2 -> "2nd place"; 3 -> "3rd place"; else -> if (needsSupport) "Needs support" else "Rank $rank" }
                                Text(rankLabel, fontSize = 9.sp, color = if (needsSupport) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                            }
                            Text("$percent%", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Icon(MaterialSymbol(if (expanded) "chevron-up" else "chevron-down"), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 5.dp))
                        }
                        if (expanded) {
                            Text("RECENT TASKS · LAST 3 WEEKS", fontSize = 9.sp, letterSpacing = .8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 17.dp, top = 4.dp))
                            val detailRows = (completedTasks.map { it to "completed" } + missedTasks.map { it to "not_completed" })
                                .filter { runCatching { LocalDate.parse(it.first.date) >= detailsStart }.getOrDefault(false) }
                                .sortedByDescending { it.first.date }
                            if (detailRows.isEmpty()) Text("No task activity in the last 3 weeks. Overall totals include older reviews.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(17.dp))
                            detailRows.forEachIndexed { index, (task, status) ->
                                Row(Modifier.fillMaxWidth().padding(start = 17.dp, end = 14.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val done = status == "completed"
                                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Icon(MaterialSymbol(if (done) "circle-check" else "circle-x"), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp)) }
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text(taskSlotLabel(task.slotKey), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text("${deptLabel(task.department)} · ${meetingLabel(task.type)}, ${friendlyDate(task.date)} · ${if (done) "Completed" else "Not completed"}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                                if (index != detailRows.lastIndex) HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun taskSlotLabel(key: String): String = when (key.removePrefix("dept:")) {
    "main_mic_1" -> "Main Mic 1"; "main_mic_2" -> "Main Mic 2"; "backup_mic_1" -> "Backup Mic 1"; "backup_mic_2" -> "Backup Mic 2"
    "audio_operator" -> "Audio Operator"; "video_operator" -> "Video Operator"; "backup_av_operator" -> "Backup A/V Operator"; "attendant_1" -> "Platform"; "backup_attendant" -> "Platform Backup"
    else -> key.removePrefix("dept:").replace('_', ' ').replaceFirstChar(Char::uppercase)
}

@Composable
private fun ReportsScreen(data: ScheduleData?, localConfig: LocalWorkspaceConfig? = null) {
    val meetings = data?.meetings.orEmpty(); val slots = meetings.flatMap { it.slots }; val filled = slots.count { it.name != null }
    val departmentCoverage = if (localConfig != null) {
        localConfig.departments.filter { it.active }.map { it.id to it.label }
    } else {
        slots.map { it.department }.distinct().map { id ->
            id to when (id) { "mic" -> "Mic Men"; "av" -> "Audio / Video"; "attendant", "platform" -> "Platform"; else -> id.removePrefix("dept:").replace('_', ' ').replaceFirstChar(Char::uppercase) }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("INSIGHTS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold); Text("Reports", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.padding(top = 14.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("ROTATION", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp); Text("Schedule coverage", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)); Text("Current month", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Box(Modifier.size(70.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Text(if (slots.isEmpty()) "—" else "${filled*100/slots.size}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } } }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("${meetings.size}", "Meetings", Modifier.weight(1f)); MetricCard("$filled", "Filled", Modifier.weight(1f)); MetricCard("${(slots.size-filled).coerceAtLeast(0)}", "Vacant", Modifier.weight(1f)) }
        if (departmentCoverage.isNotEmpty()) {
            Text("Department Coverage", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
            departmentCoverage.forEach { (id,label) ->
                val group=slots.filter { it.department==id }
                RowCard("chart", label, "${group.count { it.name!=null }} of ${group.size} positions filled", "${(group.size-group.count { it.name!=null }).coerceAtLeast(0)} vacant") {}
            }
        }
        Text("Full rotation fairness reports are available in the web workspace.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExportScreen(store: AppStore) {
    val context = LocalContext.current
    val previewMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.58f).coerceIn(300f, 520f).dp
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(store.month.value) }
    val initialMonth = remember { store.month.value }
    var selectedWeek by remember(month) { mutableStateOf(exportWeeks(month).firstOrNull { LocalDate.now() in it.first..it.second }?.first ?: firstExportWeek(month)) }
    var exportData by remember(month) { mutableStateOf(if (month == initialMonth) store.data.value else null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var weekPickerOpen by remember { mutableStateOf(false) }
    var monthPickerOpen by remember { mutableStateOf(false) }
    val weeks = remember(month) { exportWeeks(month) }
    LaunchedEffect(month) {
        loadError = null
        try { exportData = store.loadMonthForExport(month) } catch (e: Exception) { loadError = e.message ?: "Could not load this month." }
    }
    val chosenWeek = weeks.firstOrNull { it.first == selectedWeek } ?: weeks.first()
    val selectedMeetings = exportData?.meetings.orEmpty().filter { meeting ->
        val date = runCatching { LocalDate.parse(meeting.date) }.getOrNull()
        date != null && date in chosenWeek.first..chosenWeek.second && YearMonth.from(date) == month
    }.sortedBy { it.date }
    val period = "${chosenWeek.first.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))} – ${chosenWeek.second.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
    val posterDepartments = if(store.localMode) store.localConfig.value?.departments?.filter { it.active }?.map { it.id to it.label }.orEmpty()
        else listOf("mic" to "Mic Men", "av" to "Audio / Video", "attendant" to "Platform")
    val poster = renderSchedulePoster(exportData?.settings?.name ?: "Congregation", selectedMeetings, period, posterDepartments)
    val transform = rememberTransformableState { zoomChange, _, _ -> zoom = (zoom * zoomChange).coerceIn(1f, 3f) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("EXPORT SCHEDULE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold); Text("Weekly Card", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                Text("PNG · 1080 px", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            val selectedWeekNumber = (weeks.indexOfFirst { it.first == selectedWeek } + 1).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ExportPickerField(
                    modifier = Modifier.weight(.9f),
                    label = "Month",
                    value = month.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                    icon = "calendar-days",
                    onClick = { monthPickerOpen = true }
                )
                ExportPickerField(
                    modifier = Modifier.weight(1.25f),
                    label = "Week $selectedWeekNumber",
                    value = "${chosenWeek.first.format(DateTimeFormatter.ofPattern("MMM d"))} – ${chosenWeek.second.format(DateTimeFormatter.ofPattern("MMM d"))}",
                    icon = null,
                    onClick = { weekPickerOpen = true }
                )
            }
            if (!loadError.isNullOrBlank()) Text(loadError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Preview", fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text("1080 px portrait", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Pinch to zoom · ${((zoom * 100).toInt())}%", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Keep the poster inside a predictable viewport on short landscape screens.
            // Pan gestures were previously consumed by transformable even though pan was
            // ignored, preventing the surrounding page from receiving vertical scrolls.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = previewMaxHeight)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    poster.asImageBitmap(),
                    contentDescription = "Schedule poster preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = previewMaxHeight)
                        .graphicsLayer(scaleX = zoom, scaleY = zoom)
                        .transformable(transform, canPan = { false }),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.background) {
            Button(onClick = {
                if (poster.height > 0) scope.launch {
                    try {
                        val uri = withContext(Dispatchers.IO) { createPosterShareUri(context, poster, selectedWeek) }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newUri(context.contentResolver, "Schedule image", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Share schedule"))
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, e.message ?: "Could not prepare schedule image.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }, enabled = selectedMeetings.isNotEmpty() && loadError == null, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(MaterialSymbol("share-2"), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(10.dp)); Text("Share schedule", fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Icon(MaterialSymbol("chevron-right"), null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
    if (monthPickerOpen) ExportMonthPickerDialog(month, onDismiss = { monthPickerOpen = false }, onSelect = { month = it; monthPickerOpen = false })
    if (weekPickerOpen) WeekPickerDialog(month, weeks, selectedWeek, onDismiss = { weekPickerOpen = false }, onSelect = { selectedWeek = it; zoom = 1f; weekPickerOpen = false })
}

@Composable
private fun ExportPickerField(modifier: Modifier = Modifier, label: String, value: String, icon: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(MaterialSymbol(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(value, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(4.dp))
            Icon(MaterialSymbol("chevron-down"), "Choose $label", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ExportMonthPickerDialog(month: YearMonth, onDismiss: () -> Unit, onSelect: (YearMonth) -> Unit) {
    var year by remember(month) { mutableIntStateOf(month.year) }
    val months = (1..12).map { YearMonth.of(year, it) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(18.dp)) {
                Text("Choose month", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { year-- }, modifier = Modifier.size(38.dp)) { Icon(MaterialSymbol("chevron-left"), "Previous year", tint = MaterialTheme.colorScheme.primary) }
                    Text(year.toString(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { year++ }, modifier = Modifier.size(38.dp)) { Icon(MaterialSymbol("chevron-right"), "Next year", tint = MaterialTheme.colorScheme.primary) }
                }
                months.chunked(3).forEach { rowMonths ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        rowMonths.forEach { option ->
                            val selected = option == month
                            Surface(onClick = { onSelect(option) }, modifier = Modifier.weight(1f).padding(bottom = 7.dp).height(42.dp), shape = RoundedCornerShape(11.dp), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                Box(contentAlignment = Alignment.Center) { Text(option.format(DateTimeFormatter.ofPattern("MMM")), fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun WeekPickerDialog(month: YearMonth, weeks: List<Pair<LocalDate, LocalDate>>, selected: LocalDate, onDismiss: () -> Unit, onSelect: (LocalDate) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(.9f), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("Choose a week", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
                weeks.forEachIndexed { index, range ->
                    val active = range.first == selected
                    Surface(onClick = { onSelect(range.first) }, modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp), shape = RoundedCornerShape(12.dp), color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant, border = androidx.compose.foundation.BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary.copy(alpha = .4f) else MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Week ${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("${range.first.format(DateTimeFormatter.ofPattern("MMM d"))} – ${range.second.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (active) Icon(MaterialSymbol("check"), "Selected week", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}

private fun exportWeeks(month: YearMonth): List<Pair<LocalDate, LocalDate>> {
    var monday = firstExportWeek(month)
    val weeks = mutableListOf<Pair<LocalDate, LocalDate>>()
    while (monday <= month.atEndOfMonth()) { weeks += monday to monday.plusDays(6); monday = monday.plusDays(7) }
    return weeks
}

private fun firstExportWeek(month: YearMonth): LocalDate {
    val first = month.atDay(1)
    return first.minusDays((first.dayOfWeek.value - 1).toLong())
}

private fun renderSchedulePoster(congregation: String, meetings: List<Meeting>, period: String, departments: List<Pair<String,String>>): Bitmap {
    val width = 1080
    val margin = 62f
    val headerHeight = 295f
    val meetingTop = 22f
    val cardHeader = 74f
    val slotHeight = 80f
    // Extra bottom breathing room keeps the last personnel name away from the card rule.
    val cardBodyHeight = 380f
    val cardHeight = cardHeader + cardBodyHeight
    val footerHeight = 90f
    val height = (headerHeight + meetings.size * (cardHeight + meetingTop) + footerHeight + 24).toInt().coerceAtLeast(800)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cream = android.graphics.Color.rgb(244, 240, 224)
    val ink = android.graphics.Color.rgb(39, 38, 32)
    val muted = android.graphics.Color.rgb(105, 100, 85)
    val border = android.graphics.Color.rgb(207, 199, 174)
    val teal = android.graphics.Color.rgb(43, 108, 99)
    val dayColors = mapOf(
        1 to android.graphics.Color.rgb(57, 104, 137),   // Monday · blue
        2 to android.graphics.Color.rgb(163, 83, 61),   // Tuesday · terracotta
        3 to android.graphics.Color.rgb(46, 113, 102),  // Wednesday · teal
        4 to android.graphics.Color.rgb(75, 103, 78),   // Thursday · sage
        5 to android.graphics.Color.rgb(111, 101, 49),  // Friday · olive
        6 to android.graphics.Color.rgb(157, 112, 56),  // Saturday · amber
        7 to android.graphics.Color.rgb(156, 126, 52)   // Sunday · ochre
    )
    canvas.drawColor(cream)
    fun p(color: Int, size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = android.graphics.Typeface.create("sans-serif${if (bold) "-medium" else ""}", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL) }
    val kicker = p(muted, 22f, true); kicker.letterSpacing = .12f
    val title = p(ink, 43f, true)
    val section = p(teal, 23f, true); section.letterSpacing = .12f
    val header = p(android.graphics.Color.WHITE, 22f, true)
    val date = p(android.graphics.Color.WHITE, 18f)
    val deptTitle = p(teal, 19f, true)
    val rolePaint = p(muted, 14f, true)
    val namePaint = p(ink, 19f, true)
    val emptyPaint = p(muted, 19f)
    canvas.drawText(congregation.uppercase(), margin, 68f, kicker)
    canvas.drawText("CONGREGATION ASSIGNMENT SCHEDULE", margin, 128f, title)
    val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = teal }
    canvas.drawRoundRect(android.graphics.RectF(margin, 160f, margin + 365f, 208f), 24f, 24f, pill)
    canvas.drawText(period.uppercase(), margin + 20f, 191f, p(android.graphics.Color.WHITE, 17f, true))
    canvas.drawText("DUTY ROSTER", margin, 248f, section)
    canvas.drawLine(margin, 276f, width - margin, 276f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; strokeWidth = 5f })
    var y = headerHeight
    meetings.forEach { meeting ->
        val cardRect = android.graphics.RectF(margin, y, width - margin, y + cardHeight)
        canvas.drawRect(cardRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cream })
        canvas.drawRect(cardRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = border; style = Paint.Style.STROKE; strokeWidth = 4f })
        val labelDay=meetingTitle(meeting).trim().lowercase()
        val dayIndex=runCatching { LocalDate.parse(meeting.date).dayOfWeek.value }.getOrElse {
            when {
                labelDay.startsWith("mon") -> 1
                labelDay.startsWith("tue") -> 2
                labelDay.startsWith("wed") -> 3
                labelDay.startsWith("thu") -> 4
                labelDay.startsWith("fri") -> 5
                labelDay.startsWith("sat") -> 6
                labelDay.startsWith("sun") -> 7
                else -> 0
            }
        }
        val band = dayColors[dayIndex] ?: teal
        canvas.drawRect(margin + 3f, y + 3f, width - margin - 3f, y + cardHeader, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = band })
        canvas.drawText("${meetingTitle(meeting).uppercase()} MEETING", margin + 22f, y + 47f, header)
        val meetingDate = runCatching { LocalDate.parse(meeting.date).format(DateTimeFormatter.ofPattern("MMM d, yyyy")) }.getOrDefault(meeting.date)
        val meta = "$meetingDate · ${prettyTime(meeting.time)}"
        val metaWidth = date.measureText(meta)
        canvas.drawText(meta, width - margin - 22f - metaWidth, y + 46f, date)
        val columns=departments.ifEmpty { listOf("none" to "No departments configured") }
        val innerLeft = margin + 24f
        val innerRight = width - margin - 24f
        val colWidth = (innerRight - innerLeft) / columns.size
        columns.forEachIndexed { index, (deptId, departmentLabel) ->
            val left = innerLeft + index * colWidth
            if (index > 0) canvas.drawLine(left - 16f, y + cardHeader + 24f, left - 16f, y + cardHeight - 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = border; strokeWidth = 3f })
            val label=departmentLabel.uppercase().take(22)
            canvas.drawText(label, left, y + cardHeader + 42f, deptTitle)
            val columnSlots = meeting.slots.filter { it.department == deptId }
            columnSlots.forEachIndexed { slotIndex, slot ->
                val slotY = y + cardHeader + 80f + slotIndex * slotHeight
                val role = slot.label.uppercase().take(28)
                canvas.drawText(role, left, slotY, rolePaint)
                val person = slot.name?.takeIf { it.isNotBlank() } ?: "—"
                canvas.drawText(person, left, slotY + 27f, if (person == "—") emptyPaint else namePaint)
            }
        }
        y += cardHeight + meetingTop
    }
    val footerY = height - footerHeight
    canvas.drawLine(margin, footerY, width - margin, footerY, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; strokeWidth = 4f })
    canvas.drawText("Prepared for $congregation", margin, footerY + 35f, p(muted, 15f))
    canvas.drawText("Generated ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))}", margin, footerY + 60f, p(muted, 15f))
    return bitmap
}

private fun createPosterShareUri(context: android.content.Context, poster: Bitmap, week: LocalDate): Uri {
    val folder = File(context.cacheDir, "shared-schedules").apply { mkdirs() }
    val file = File(folder, "Congregation-Schedule-${week}.png")
    file.outputStream().use { output -> if (!poster.compress(Bitmap.CompressFormat.PNG, 100, output)) error("Could not create PNG image.") }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun SettingsScreen(store: AppStore, scope: kotlinx.coroutines.CoroutineScope, snackbar: SnackbarHostState, appLock: AppLockController, onDelete: (ManagedUser) -> Unit, onAddAccount: () -> Unit, onBackup: () -> Unit, onRestore: () -> Unit, onChooseAutoBackupFolder: () -> Unit, onAutoBackupToggle: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf<String?>(null) }
    var name by remember(store.data.value?.settings?.name) { mutableStateOf(store.data.value?.settings?.name ?: "") }
    var thu by remember(store.data.value?.settings?.thursday) { mutableStateOf(store.data.value?.settings?.thursday ?: "18:00") }
    var sat by remember(store.data.value?.settings?.saturday) { mutableStateOf(store.data.value?.settings?.saturday ?: "15:30") }
    var sun by remember(store.data.value?.settings?.sunday) { mutableStateOf(store.data.value?.settings?.sunday ?: "07:45") }
    val admin = store.user.value?.role == "admin"
    var localConfigDialog by remember { mutableStateOf(false) }
    var sharedSyncInfo by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Settings & Accounts", fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        if (store.user.value?.role != "viewer") SettingSection("Data & Backup", if (store.localMode) "Local workspace · stored on this device" else "Shared workspace · syncs through your congregation account", "database", expanded=="data", { expanded=if(expanded=="data")null else "data" }) {
            Text(if (store.localMode) "Your schedules, personnel, reviews, and profile photos stay on this device. Save a backup before changing phones." else "This congregation uses a shared online workspace. Sign in on another device to access the same schedule." , fontSize=12.sp, color=MaterialTheme.colorScheme.onSurfaceVariant, lineHeight=18.sp)
            if (store.localMode) {
                Row(Modifier.fillMaxWidth().padding(top=10.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=onBackup, modifier=Modifier.weight(1f), shape=RoundedCornerShape(12.dp)) { Icon(MaterialSymbol("download"), null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Back up") }
                    OutlinedButton(onClick=onRestore, modifier=Modifier.weight(1f), shape=RoundedCornerShape(12.dp)) { Icon(MaterialSymbol("upload"), null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Restore") }
                }
                val backup = store.autoBackup.value
                PreferenceRow(icon="download",title="Automatic backup",subtitle=when {
                    backup.folderName != null -> "${backup.folderName} · jw-task-scheduler-backup.bcdv"
                    else -> "Save a .bcdv copy to a folder you choose"
                }) {
                    Switch(checked=backup.enabled,onCheckedChange=onAutoBackupToggle,colors=appSwitchColors())
                }
                OutlinedButton(onClick=onChooseAutoBackupFolder,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) {
                    Icon(MaterialSymbol("file-down"),null,Modifier.size(17.dp)); Spacer(Modifier.width(7.dp))
                    Text(if(backup.folderName==null) "Choose backup folder" else "Change backup folder")
                }
                backup.lastSaved?.let { Text("Last saved: $it",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp)) }
                backup.error?.let { Text("Backup issue: $it",fontSize=10.sp,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=4.dp)) }
                TextButton(onClick={ sharedSyncInfo=true }, modifier=Modifier.align(Alignment.End)) { Text("Use shared sync") }
            } else {
                Text("Optional sync is enabled for this shared workspace.", fontSize=11.sp, color=MaterialTheme.colorScheme.primary, modifier=Modifier.padding(top=10.dp))
            }
        }
        if (store.user.value?.role != "viewer") SettingSection("Workspace", "${store.data.value?.settings?.name ?: "Congregation"} · Meeting times", "building-2", expanded == "workspace", { expanded = if (expanded=="workspace") null else "workspace" }) {
            OutlinedTextField(name, { name=it }, label={Text("Congregation Name")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
            Spacer(Modifier.height(8.dp))
            Text("Manage meeting days, times, groups, departments, and roles in workspace setup below.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={ scope.launch { try { store.saveWorkspace(WorkspaceSettings(name,thu,sat,sun)); expanded=null; snackbar.showSnackbar("Workspace settings saved.") } catch(e:Exception) { snackbar.showSnackbar(e.message ?: "Could not save workspace settings.") } } }, modifier=Modifier.fillMaxWidth().padding(top=10.dp).height(44.dp), contentPadding=PaddingValues(horizontal=16.dp, vertical=8.dp)) { Text("Save Changes") }
            if (admin) {
                OutlinedButton(onClick={ localConfigDialog=true }, modifier=Modifier.fillMaxWidth().padding(top=8.dp).height(44.dp), shape=RoundedCornerShape(12.dp)) {
                    Icon(MaterialSymbol("tune"), null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Groups, departments & meetings")
                }
                Text(if (store.localMode) "Customize groups, departments, roles, and meeting times for this device." else "Customize groups, departments, roles, and meeting times for the shared congregation.", fontSize=10.sp, color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(top=6.dp))
            }
        }
        if (admin && !store.localMode) SettingSection("Accounts & Access", "Manage roles and permissions", "users-round", expanded=="accounts", { expanded=if(expanded=="accounts")null else "accounts" }) {
            var selectedAccount by remember { mutableStateOf<ManagedUser?>(null) }
            var passwordAccount by remember { mutableStateOf<ManagedUser?>(null) }
            var pageAccessAccount by remember { mutableStateOf<ManagedUser?>(null) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                Text("Manage Accounts", fontSize=14.sp, fontWeight=FontWeight.SemiBold)
                FilledTonalIconButton(onClick=onAddAccount,modifier=Modifier.size(42.dp)) { Icon(MaterialSymbol("user-plus"),"Add account",Modifier.size(22.dp),MaterialTheme.colorScheme.primary) }
            }
            if (store.users.value.isEmpty()) Text("No accounts found.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
            val accounts=store.users.value
            val columns=if(LocalConfiguration.current.screenWidthDp>=600) 4 else 3
            LazyVerticalGrid(columns=GridCells.Fixed(columns),modifier=Modifier.fillMaxWidth().heightIn(max=440.dp).padding(top=8.dp),contentPadding=PaddingValues(4.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(accounts.size) { index ->
                    val account=accounts[index]
                    LaunchedEffect(account.id,account.avatarUpdatedAt,account.hasAvatar) {
                        try { store.loadAccountPhoto(account) } catch(e:Exception) { snackbar.showSnackbar("Could not load account photo. Try opening Accounts & Access again.") }
                    }
                    Card(onClick={selectedAccount=account},shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.42f))) {
                        Column(Modifier.fillMaxWidth().padding(vertical=14.dp,horizontal=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            Box(Modifier.size(68.dp)) {
                                Avatar(account.displayName,size=68.dp,photo=store.accountPhotos.value[account.id])
                                if(account.role=="admin") Surface(Modifier.align(Alignment.BottomEnd).size(24.dp),shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer) {
                                    Icon(MaterialSymbol("workspace_premium"),"Administrator",Modifier.padding(3.dp),MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(account.displayName,fontSize=12.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(roleLabel(account.role),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                            Text(if(account.active)"Active" else "Disabled",fontSize=9.sp,color=if(account.active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            selectedAccount?.let { account ->
                Dialog(onDismissRequest={selectedAccount=null}) {
                    Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Box(Modifier.size(64.dp)) {
                                    Avatar(account.displayName,size=64.dp,photo=store.accountPhotos.value[account.id])
                                    if(account.role=="admin") Surface(Modifier.align(Alignment.BottomEnd).size(23.dp),shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer) {
                                        Icon(MaterialSymbol("workspace_premium"),"Administrator",Modifier.padding(3.dp),MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(account.displayName,fontWeight=FontWeight.Bold,fontSize=18.sp)
                                    Text("@${account.username} · ${roleLabel(account.role)}",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                                }
                            }
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            Text("Change role",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                (if(account.role=="admin") listOf("admin" to "Admin") else listOf("assistant" to "Editor","viewer" to "Viewer")).forEach { (role,label) ->
                                    FilterChip(selected=account.role==role,onClick={
                                        if(account.role!=role && !(account.role=="admin" && store.users.value.count{it.role=="admin"&&it.active}<=1)) scope.launch {
                                            try { store.setRole(account,role); selectedAccount=null; snackbar.showSnackbar("Role changed to $label.") }
                                            catch(e:Exception) { snackbar.showSnackbar(e.message ?: "Could not change role.") }
                                        }
                                    },label={Text(label,fontSize=10.sp)})
                                }
                            }
                            if(account.role!="admin") {
                                OutlinedButton(onClick={pageAccessAccount=account;selectedAccount=null},modifier=Modifier.fillMaxWidth()) {
                                    Icon(MaterialSymbol("tune"),null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Page access")
                                }
                                OutlinedButton(onClick={passwordAccount=account;selectedAccount=null},modifier=Modifier.fillMaxWidth()) {
                                    Icon(MaterialSymbol("lock"),null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Reset password")
                                }
                                OutlinedButton(onClick={scope.launch {
                                    try { store.setActive(account,!account.active); selectedAccount=null; snackbar.showSnackbar(if(account.active)"Account disabled." else "Account enabled.") }
                                    catch(e:Exception) { snackbar.showSnackbar(e.message ?: "Could not update account status.") }
                                }},modifier=Modifier.fillMaxWidth()) {
                                    Icon(MaterialSymbol("block"),null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if(account.active)"Disable account" else "Enable account")
                                }
                                OutlinedButton(onClick={selectedAccount=null;onDelete(account)},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)) {
                                    Icon(MaterialSymbol("trash-2"),null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Delete account")
                                }
                            }
                            TextButton(onClick={selectedAccount=null},modifier=Modifier.align(Alignment.End)){Text("Close")}
                        }
                    }
                }
            }
            pageAccessAccount?.let { account ->
                AccountPageAccessDialog(account,onDismiss={pageAccessAccount=null},onSave={pages ->
                    store.savePageAccess(account,pages)
                    snackbar.showSnackbar("Page access saved.")
                })
            }
            passwordAccount?.let { account ->
                AccountPasswordResetDialog(account,onDismiss={passwordAccount=null},onReset={password,confirm ->
                    store.resetPassword(account,password,confirm)
                    snackbar.showSnackbar("Password reset. This account must sign in again.")
                })
            }
        }
        SettingSection("App Preferences", "Security · Appearance · Text size · About", "sliders-horizontal", expanded=="prefs", { expanded=if(expanded=="prefs")null else "prefs" }) {
            PreferenceRow(icon="fingerprint",title="Biometric app lock",subtitle=if(appLock.enabled) "On · Unlock when you reopen the app" else "Fingerprint / secure face or phone screen lock") {
                Switch(checked=appLock.enabled,onCheckedChange=appLock::changeEnabled,enabled=!appLock.busy,colors=appSwitchColors())
            }
            TextButton(onClick=appLock::openSecuritySettings,enabled=!appLock.busy,contentPadding=PaddingValues(horizontal=2.dp,vertical=4.dp)) {
                Icon(MaterialSymbol("settings"),null,Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text("Phone security settings",fontSize=11.sp)
            }
            HorizontalDivider(Modifier.padding(vertical=4.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.65f))
            PreferenceRow(icon = if (store.dark.value) "moon" else "sun", title = "Dark appearance", subtitle = if (store.dark.value) "On · Easier viewing in low light" else "Off · Using light colors") {
                Switch(checked = store.dark.value, onCheckedChange = { store.setDarkAppearance(it) }, colors = appSwitchColors())
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
            Text("App text size · ${(store.fontScale.value * 100).roundToInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { store.setFontScale(store.fontScale.value - .05f) }, enabled = store.fontScale.value > .85f) { Text("A−", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
                Slider(value = store.fontScale.value, onValueChange = store::setFontScale, valueRange = .85f..1.30f, steps = 8, modifier = Modifier.weight(1f))
                IconButton(onClick = { store.setFontScale(store.fontScale.value + .05f) }, enabled = store.fontScale.value < 1.30f) { Text("A+", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
            }
            Text("Adjusts text throughout the app.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
            PreferenceRow(icon = "wifi-off", title = "Offline schedule access", subtitle = if (store.offline.value) "Available without a connection" else "Use the latest online schedule") {
                Switch(checked = store.offline.value, onCheckedChange = { store.offline.value = it }, colors = appSwitchColors())
            }
            Card(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("APP INFORMATION", fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp))
                    Text(if (store.localMode) "Local administrator · This device" else "Signed in as ${store.user.value?.username ?: "—"}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }
            val context = LocalContext.current
            TextButton(
                onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/charledave2"))) }
                },
                modifier = Modifier.align(Alignment.Start).padding(top = 2.dp).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                androidx.compose.material3.Icon(painter=painterResource(R.drawable.ic_github),contentDescription=null,modifier=Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Visit me on GitHub", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    if (sharedSyncInfo && store.user.value?.role != "viewer") {
        AlertDialog(
            onDismissRequest = { sharedSyncInfo = false },
            title = { Text("Shared sync is planned") },
            text = { Text("Your local workspace stays on this device. This button will not open the existing cloud login. A future setup can let the admin create a congregation sync space, invite other devices with a code or QR, and keep working offline while changes sync when connected.") },
            confirmButton = { TextButton(onClick = { sharedSyncInfo = false }) { Text("Got it") } }
        )
    }
    if (localConfigDialog && admin) LocalWorkspaceConfigDialog(
        initial = store.workspaceConfig ?: LocalWorkspaceConfig(0, emptyList(), emptyList()),
        maxGroups = if (store.localMode) 30 else 9,
        workspaceLabel = if (store.localMode) "LOCAL WORKSPACE" else "SHARED WORKSPACE",
        onDismiss = { localConfigDialog=false },
        onSave = { value -> scope.launch { try { if (store.localMode) store.saveLocalConfig(value) else store.saveCloudConfig(value); store.load(); snackbar.showSnackbar("Workspace setup saved.") } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Could not save workspace setup.") } } }
    )
}

@Composable
private fun LocalWorkspaceConfigDialog(initial: LocalWorkspaceConfig, maxGroups: Int, workspaceLabel: String, onDismiss: () -> Unit, onSave: (LocalWorkspaceConfig) -> Unit) {
    var groupCount by remember(initial) { mutableIntStateOf(initial.groupCount) }
    var departments by remember(initial) { mutableStateOf<List<LocalDepartment>>(initial.departments) }
    var meetings by remember(initial) { mutableStateOf<List<LocalMeetingRule>>(initial.meetings) }
    var error by remember { mutableStateOf<String?>(null) }
    var newDepartment by remember { mutableStateOf("") }
    var newMeeting by remember { mutableStateOf("") }
    var selectedDay by remember { mutableIntStateOf(0) }
    var newMeetingTime by remember { mutableStateOf("") }
    val context=LocalContext.current
    val scroll=rememberScrollState()
    Dialog(onDismissRequest=onDismiss, properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.94f).widthIn(max=520.dp).fillMaxHeight(.88f), shape=RoundedCornerShape(24.dp), color=MaterialTheme.colorScheme.surface, border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(workspaceLabel,fontSize=9.sp,letterSpacing=1.sp,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold); Text("Congregation setup",fontSize=19.sp,fontWeight=FontWeight.Bold) }
                    IconButton(onClick=onDismiss){Icon(MaterialSymbol("close"),"Close")}
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal=18.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("GROUPS",fontSize=10.sp,letterSpacing=.8.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Number of groups",Modifier.weight(1f),fontSize=13.sp)
                        OutlinedButton(onClick={if(groupCount>0)groupCount--},enabled=groupCount>0,shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(horizontal=13.dp,vertical=6.dp)){Text("−")}
                        Text(if(groupCount==0)"—" else groupCount.toString(),fontSize=16.sp,fontWeight=FontWeight.Bold)
                        OutlinedButton(onClick={if(groupCount<maxGroups)groupCount++},enabled=groupCount<maxGroups,shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(horizontal=13.dp,vertical=6.dp)){Text("+")}
                    }
                    Text(if(groupCount==0)"No groups configured yet. Add the number your congregation uses." else "$groupCount numbered groups are available for personnel and meeting eligibility.",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Text("DEPARTMENTS & ROLES",fontSize=10.sp,letterSpacing=.8.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(departments.isEmpty()) Text("No departments yet. Add one below, then add the assignment roles it needs.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    departments.forEachIndexed { index, dept ->
                        Card(shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f))) {
                            Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    OutlinedTextField(dept.label,{ value -> departments=departments.toMutableList().also { it[index]=dept.copy(label=value) } },modifier=Modifier.weight(1f),singleLine=true,label={Text("Department")},shape=RoundedCornerShape(11.dp))
                                    IconButton(onClick={departments=departments.toMutableList().also { it[index]=dept.copy(active=!dept.active) }}) { Icon(MaterialSymbol(if(dept.active)"archive" else "unarchive"),if(dept.active)"Archive department" else "Restore department",tint=MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick={departments=departments.filterNot { it.id==dept.id }}) { Icon(MaterialSymbol("delete"),"Delete department",tint=MaterialTheme.colorScheme.error) }
                                }
                                dept.roles.forEachIndexed { roleIndex, role ->
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        OutlinedTextField(role.label,{ value -> val updated=dept.roles.toMutableList().also { it[roleIndex]=role.copy(label=value) }; departments=departments.toMutableList().also { it[index]=dept.copy(roles=updated) } },modifier=Modifier.weight(1f),singleLine=true,label={Text("Assignment / role")},shape=RoundedCornerShape(10.dp))
                                        IconButton(onClick={ val updated=dept.roles.toMutableList().also { it[roleIndex]=role.copy(active=!role.active) }; departments=departments.toMutableList().also { it[index]=dept.copy(roles=updated) } }) { Icon(MaterialSymbol(if(role.active)"archive" else "unarchive"),if(role.active)"Archive role" else "Restore role",tint=MaterialTheme.colorScheme.onSurfaceVariant) }
                                        IconButton(onClick={ val updated=dept.roles.filterNot { it.id==role.id }; departments=departments.toMutableList().also { it[index]=dept.copy(roles=updated) } }) { Icon(MaterialSymbol("delete"),"Delete role",tint=MaterialTheme.colorScheme.error) }
                                    }
                                }
                                TextButton(onClick={ val role=LocalAssignmentRole("role_${System.currentTimeMillis()}",""); departments=departments.toMutableList().also { it[index]=dept.copy(roles=dept.roles+role) } }) { Icon(MaterialSymbol("add"),null,Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Add role") }
                                if(!dept.active) Text("Archived for new schedules",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(newDepartment,{newDepartment=it},modifier=Modifier.weight(1f),singleLine=true,label={Text("New department")},shape=RoundedCornerShape(11.dp))
                        FilledTonalButton(onClick={ val label=newDepartment.trim(); if(label.isNotBlank()){ val id="dept_${System.currentTimeMillis()}"; departments=departments+LocalDepartment(id,label,emptyList()); newDepartment="" } },shape=RoundedCornerShape(11.dp)){Text("Add")}
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Text("MEETING DAYS & TIMES",fontSize=10.sp,letterSpacing=.8.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(meetings.isEmpty()) Text("No meetings yet. Add a meeting below and choose its day and time.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    meetings.forEachIndexed { index, meeting ->
                        Card(shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f))) {
                            Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    OutlinedTextField(meeting.label,{v->meetings=meetings.toMutableList().also { it[index]=meeting.copy(label=v) }},modifier=Modifier.weight(1f),singleLine=true,label={Text("Meeting name")},shape=RoundedCornerShape(11.dp))
                                    IconButton(onClick={meetings=meetings.toMutableList().also { it[index]=meeting.copy(active=!meeting.active) }}) { Icon(MaterialSymbol(if(meeting.active)"archive" else "unarchive"),if(meeting.active)"Archive meeting" else "Restore meeting",tint=MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick={meetings=meetings.filterNot { it.id==meeting.id }}) { Icon(MaterialSymbol("delete"),"Delete meeting",tint=MaterialTheme.colorScheme.error) }
                                }
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    var dayMenu by remember(meeting.id) { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(onClick={dayMenu=true},shape=RoundedCornerShape(10.dp)){Text(dayName(meeting.dayOfWeek),fontSize=12.sp)}
                                        DropdownMenu(expanded=dayMenu,onDismissRequest={dayMenu=false}) { (1..7).forEach { d->DropdownMenuItem(text={Text(dayName(d))},onClick={meetings=meetings.toMutableList().also { it[index]=meeting.copy(dayOfWeek=d) };dayMenu=false}) } }
                                    }
                                    TimeInput(meeting.time){ t->meetings=meetings.toMutableList().also { it[index]=meeting.copy(time=t) } }
                                }
                                Text("Eligible groups",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                    (1..groupCount).forEach { g ->
                                        val enabled=g in meeting.eligibleGroups
                                        FilterPill(g.toString(),enabled){ meetings=meetings.toMutableList().also { it[index]=meeting.copy(eligibleGroups=if(enabled)meeting.eligibleGroups-g else meeting.eligibleGroups+g) } }
                                    }
                                }
                                if(!meeting.active) Text("Archived from future schedules",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    OutlinedTextField(newMeeting,{newMeeting=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("New meeting name")},shape=RoundedCornerShape(11.dp))
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        var dayMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick={dayMenu=true},shape=RoundedCornerShape(10.dp)){Text(if(selectedDay==0)"Choose day" else dayName(selectedDay))}
                            DropdownMenu(expanded=dayMenu,onDismissRequest={dayMenu=false}){(1..7).forEach{d->DropdownMenuItem(text={Text(dayName(d))},onClick={selectedDay=d;dayMenu=false})}}
                        }
                        if(newMeetingTime.isBlank()) {
                            OutlinedButton(onClick={TimePickerDialog(context,{_,h,m->newMeetingTime="%02d:%02d".format(h,m)},9,0,false).show()},shape=RoundedCornerShape(10.dp)){Text("Choose time")}
                        } else {
                            TimeInput(newMeetingTime){newMeetingTime=it}
                        }
                        FilledTonalButton(enabled=newMeeting.trim().isNotBlank() && selectedDay in 1..7 && newMeetingTime.isNotBlank(),onClick={
                            val label=newMeeting.trim()
                            if(label.isNotBlank() && selectedDay in 1..7 && newMeetingTime.isNotBlank()) {
                                meetings=meetings+LocalMeetingRule("meeting_${System.currentTimeMillis()}",label,selectedDay,newMeetingTime,(1..groupCount).toSet())
                                newMeeting=""; selectedDay=0; newMeetingTime=""
                            }
                        },shape=RoundedCornerShape(11.dp)){Text("Add")}
                    }
                    error?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=11.sp) }
                    Text("Archived departments and removed meetings stop appearing in new schedules. Existing saved assignments remain in the local backup.",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,lineHeight=15.sp)
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=onDismiss){Text("Cancel")}
                    Button(onClick={
                        val normalized=departments.map { d-> d.copy(label=d.label.trim(),roles=d.roles.map { it.copy(label=it.label.trim()) }) }
                        val cleanedMeetings=meetings.map { it.copy(label=it.label.trim(),eligibleGroups=it.eligibleGroups.filter { g->g in 1..groupCount }.toSet()) }
                        val config=LocalWorkspaceConfig(groupCount,normalized,cleanedMeetings)
                        when { normalized.any { it.label.isBlank() || it.roles.any { role->role.label.isBlank() } } || cleanedMeetings.any { it.label.isBlank() } -> error="Enter a name for every department, role, and meeting."; (groupCount>0 && cleanedMeetings.any { it.eligibleGroups.isEmpty() }) -> error="Choose at least one eligible group for each meeting."; else -> runCatching { config.also { require(it.departments.map { d->d.id }.distinct().size==it.departments.size); require(it.meetings.map { m->m.id }.distinct().size==it.meetings.size) } }.onSuccess { error=null;onSave(it);onDismiss() }.onFailure { error=it.message ?: "Could not save setup." } }
                    },shape=RoundedCornerShape(12.dp)){Text("Save setup")}
                }
            }
        }
    }
}

private fun dayName(day: Int): String = when(day) { 1->"Monday";2->"Tuesday";3->"Wednesday";4->"Thursday";5->"Friday";6->"Saturday";7->"Sunday";else->"Day" }

@Composable
private fun GenerateDialogContent(onConfirm: () -> Unit) { Text("Review the generated draft before applying it.") }

@Composable
private fun SettingSection(title: String, subtitle: String, icon: String, open: Boolean, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(shape=RoundedCornerShape(16.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface), border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant), modifier=Modifier.fillMaxWidth().padding(bottom=9.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(13.dp), verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),contentAlignment=Alignment.Center){Icon(MaterialSymbol(icon),null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))}; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold,fontSize=13.sp);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=10.sp,modifier=Modifier.padding(top=3.dp),maxLines=1,overflow=TextOverflow.Ellipsis)};Icon(MaterialSymbol(if(open)"chevron-down" else "chevron-right"),null,tint=MaterialTheme.colorScheme.onSurfaceVariant) }
            if(open) Column(Modifier.padding(start=14.dp,end=14.dp,bottom=14.dp),content=content)
        }
    }
}

@Composable
private fun ProfileDialog(
    user: UserInfo,
    photo: ImageBitmap?,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSave: suspend (String) -> Unit,
    onSignOut: () -> Unit,
    onRequestPhoto: () -> Unit
) {
    var name by remember(user.id, user.displayName) { mutableStateOf(user.displayName) }
    var editingName by remember(user.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(expanded) {
        if(!expanded) { editingName=false; error=null; name=user.displayName }
    }
    LaunchedEffect(editingName, expanded) {
        if (editingName && expanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { if(!saving) { keyboardController?.hide(); onDismiss() } },
        modifier = Modifier.widthIn(max=300.dp).width(280.dp).imePadding(),
        offset = androidx.compose.ui.unit.DpOffset(9.dp, 0.dp),
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * .7f).dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(user.displayName, 42.dp, photo)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(roleLabel(user.role).uppercase(java.util.Locale.ROOT),color=MaterialTheme.colorScheme.primary,fontSize=12.sp,fontWeight=FontWeight.Bold)
                    Text(user.displayName,fontSize=13.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                TextButton(onClick=onRequestPhoto,enabled=!saving,modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=8.dp,vertical=6.dp)) {
                    Icon(MaterialSymbol("camera"),null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Change photo",fontSize=12.sp,maxLines=2)
                }
                TextButton(onClick={name=user.displayName;editingName=true},enabled=!saving && !editingName,modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=8.dp,vertical=6.dp)) {
                    Icon(MaterialSymbol("edit"),null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Rename",fontSize=12.sp,maxLines=2)
                }
            }
            if(editingName) {
                OutlinedTextField(value=name,onValueChange={name=it;error=null},label={Text("Display name")},singleLine=true,enabled=!saving,modifier=Modifier.fillMaxWidth().focusRequester(focusRequester),shape=RoundedCornerShape(12.dp))
                error?.let { Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled=!saving,onClick={editingName=false;name=user.displayName;error=null;keyboardController?.hide()},modifier=Modifier.weight(1f)){Text("Cancel")}
                    Button(enabled=!saving,onClick={
                        val trimmed=name.trim()
                        if(trimmed.length !in 2..60) error="Display name must contain 2–60 characters."
                        else { saving=true;scope.launch {
                            try { onSave(trimmed);editingName=false;keyboardController?.hide() }
                            catch(e:Exception){error=e.message ?: "Could not update profile."}
                            finally {saving=false}
                        } }
                    },modifier=Modifier.weight(1f)){Text(if(saving)"Saving…" else "Save")}
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick={keyboardController?.hide();onSignOut()},enabled=!saving,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) {
                Icon(MaterialSymbol("log-out"),null,Modifier.size(19.dp));Spacer(Modifier.width(8.dp));Text("Sign out")
            }
        }
    }
}

@Composable
private fun AssignmentPickerDialog(meeting:Meeting,slot:Slot,candidates:List<Person>,onDismiss:()->Unit,onSelect:(Person?)->Unit){
    var query by remember { mutableStateOf("") }
    val filtered=candidates.filter { it.name.contains(query.trim(),ignoreCase=true) }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.94f).widthIn(max=440.dp).fillMaxHeight(.84f).heightIn(max=620.dp),shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surface,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),tonalElevation=8.dp) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start=20.dp,end=12.dp,top=17.dp,bottom=13.dp),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),contentAlignment=Alignment.Center){Icon(MaterialSymbol("users-round"),null,tint=MaterialTheme.colorScheme.primary)}
                    Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(slot.label,fontSize=19.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${meetingTitle(meeting)} · ${friendlyDate(meeting.date)} · ${deptLabel(slot.department)}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=2.dp),maxLines=1,overflow=TextOverflow.Ellipsis)}
                    IconButton(onClick=onDismiss){Icon(MaterialSymbol("close"),"Close",tint=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                OutlinedTextField(query,{query=it},placeholder={Text("Find a person")},leadingIcon={Icon(MaterialSymbol("search"),null,tint=MaterialTheme.colorScheme.onSurfaceVariant)},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp),shape=RoundedCornerShape(13.dp))
                Text("ELIGIBLE PEOPLE · ${filtered.size}",Modifier.padding(start=20.dp,bottom=8.dp),fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                PickerChoiceRow(title="Unassigned",subtitle="Leave this position empty",selected=slot.personId==null,icon="user-round",onClick={onSelect(null)})
                if(filtered.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text(if(query.isBlank())"No eligible people for this assignment." else "No people match your search.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                else LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(bottom=10.dp)){
                    items(filtered,key={it.id}){person->PickerChoiceRow(person.name,"Group ${person.group} · ${person.status.replaceFirstChar(Char::uppercase)}",slot.personId==person.id,person=person,onClick={onSelect(person)})}
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=10.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("${candidates.size} eligible",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick=onDismiss){Text("Cancel")}}
            }
        }
    }
}

@Composable
private fun PickerChoiceRow(title:String,subtitle:String,selected:Boolean,icon:String?=null,person:Person?=null,onClick:()->Unit){
    Surface(onClick=onClick,shape=RoundedCornerShape(14.dp),color=if(selected)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,border=androidx.compose.foundation.BorderStroke(1.dp,if(selected)MaterialTheme.colorScheme.primary.copy(alpha=.45f)else MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=3.dp)){
        Row(Modifier.padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){
            if(person!=null) Avatar(person.name,38.dp) else Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),contentAlignment=Alignment.Center){Icon(MaterialSymbol(icon?:"user-round"),null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))}
            Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(title,fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis);Text(subtitle,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=2.dp))}
            if(selected) Icon(MaterialSymbol("check"),"Selected",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp)) else Icon(MaterialSymbol("chevron-right"),null,tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(18.dp))
        }
    }
}

private val accountPages = listOf("Home", "Schedule Calendar", "Manage Schedule", "People Directory", "Reports", "Task Review", "Export Studio", "Settings & Accounts")
private fun canonicalPage(page: String) = when(page) { "Schedule" -> "Manage Schedule"; "Top Performers" -> "Reports"; "Settings & accounts" -> "Settings & Accounts"; else -> page }
private fun accountVisiblePages(permissions: String): Set<String> {
    val parsed=runCatching { JSONObject(permissions) }.getOrElse { JSONObject() }
    val pages=parsed.optJSONArray("pages") ?: return accountPages.toSet()
    return (0 until pages.length()).map { pages.optString(it) }.filter { it in accountPages }.toSet()+"Home"
}
private fun accountCanOpenPage(user: UserInfo?, page: String): Boolean {
    if(user==null)return false
    if(user.role=="admin")return true
    val canonical=canonicalPage(page)
    if(canonical=="Task Review" && user.role!="assistant")return false
    return canonical in accountVisiblePages(user.permissions)
}
@Composable
private fun AccountPageAccessDialog(account: ManagedUser,onDismiss:()->Unit,onSave:suspend(Set<String>)->Unit) {
    var pages by remember(account.id) { mutableStateOf(accountVisiblePages(account.permissions)) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    Dialog(onDismissRequest={if(!saving)onDismiss()}) {
        Card(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*.85f).dp),shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(MaterialSymbol("tune"),null,tint=MaterialTheme.colorScheme.primary)
                Text("Page access",fontSize=20.sp,fontWeight=FontWeight.Bold)
                Text("Choose the pages ${account.displayName} can open. Home stays available. Role permissions still apply.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                accountPages.filter { it!="Task Review" || account.role=="assistant" }.forEach { page ->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(page,Modifier.weight(1f),fontSize=13.sp)
                        Switch(checked=page in pages,enabled=!saving && page!="Home",onCheckedChange={checked -> pages=if(checked)pages+page else pages-page},colors=appSwitchColors())
                    }
                }
                error?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=onDismiss,enabled=!saving){Text("Cancel")}
                    Button(enabled=!saving,onClick={saving=true;scope.launch { try { onSave(pages);onDismiss() } catch(e:Exception){error=e.message ?: "Could not save page access."} finally{saving=false} }}) {
                        Icon(MaterialSymbol("check"),null,Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text(if(saving)"Saving…" else "Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountPasswordResetDialog(account: ManagedUser,onDismiss:()->Unit,onReset:suspend(String,String)->Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    Dialog(onDismissRequest={if(!saving)onDismiss()}) {
        Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Reset password",fontSize=20.sp,fontWeight=FontWeight.Bold)
                Text("Set a new password for ${account.displayName}. They will need to sign in again on all devices.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                PasswordField(password,{password=it;error=null},"New password",Modifier.fillMaxWidth(),supportingText="10–200 characters")
                PasswordField(confirm,{confirm=it;error=null},"Confirm password",Modifier.fillMaxWidth())
                error?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(enabled=!saving,onClick=onDismiss){Text("Cancel")}
                    Button(enabled=!saving,onClick={
                        when {
                            password.length !in 10..200 -> error="Password must contain 10–200 characters."
                            password!=confirm -> error="Password confirmation does not match."
                            else -> { saving=true; scope.launch { try { onReset(password,confirm); onDismiss() } catch(e:Exception) { error=e.message ?: "Could not reset password." } finally { saving=false } } }
                        }
                    }) { if(saving) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp) else Text("Reset") }
                }
            }
        }
    }
}

@Composable
private fun AccountCreateDialog(onDismiss: () -> Unit, onCreate: suspend (String, String, String, String, String, List<String>, List<String>) -> Unit) {
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("assistant") }
    var departments by remember { mutableStateOf(setOf("mic")) }
    var actions by remember { mutableStateOf(setOf("view", "edit", "preview", "apply", "export")) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val departmentOptions = listOf("mic" to "Mic Men", "av" to "Audio / Video", "attendant" to "Platform")
    val actionOptions = listOf("view" to "View", "edit" to "Edit", "preview" to "Preview", "apply" to "Apply", "export" to "Export")

    Dialog(onDismissRequest={if(!saving)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(
            Modifier.fillMaxWidth(.94f).widthIn(max=460.dp).fillMaxHeight(.90f).heightIn(max=760.dp),
            shape=RoundedCornerShape(26.dp), color=MaterialTheme.colorScheme.surface,
            border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant), tonalElevation=8.dp
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start=20.dp,end=12.dp,top=17.dp,bottom=13.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),contentAlignment=Alignment.Center) { Icon(MaterialSymbol("user-plus"),null,tint=MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("Add account",fontSize=20.sp,fontWeight=FontWeight.Bold); Text("Set up sign-in and access",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=2.dp)) }
                    IconButton(onClick={if(!saving)onDismiss()}) { Icon(MaterialSymbol("close"),"Close",tint=MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text("ACCOUNT DETAILS",fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(displayName,{displayName=it;error=null},label={Text("Display name")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp))
                    OutlinedTextField(username,{username=it;error=null},label={Text("Username")},supportingText={Text("3–40 letters, numbers, dot, underscore, or hyphen")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp))
                    Text("ACCOUNT TYPE",fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=2.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        AccountRoleOption("Department editor",role=="assistant",Modifier.weight(1f)){role="assistant";actions=setOf("view","edit","preview","apply","export")}
                        AccountRoleOption("Viewer",role=="viewer",Modifier.weight(1f)){role="viewer";actions=setOf("view","export")}
                    }
                    Text("DEPARTMENTS",fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=2.dp))
                    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal=10.dp,vertical=4.dp)) {
                        departmentOptions.forEach { (key,label) -> Row(Modifier.fillMaxWidth().heightIn(min=40.dp).clickable { departments=if(key in departments)departments-key else departments+key },verticalAlignment=Alignment.CenterVertically) { Checkbox(key in departments,{checked->departments=if(checked)departments+key else departments-key}); Text(label,Modifier.weight(1f),fontSize=13.sp) } }
                    }
                    Text("ALLOWED ACTIONS",fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=2.dp))
                    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal=10.dp,vertical=4.dp)) {
                        actionOptions.forEach { (key,label) ->
                            val enabled=role=="assistant"||key in listOf("view","export")
                            Row(Modifier.fillMaxWidth().heightIn(min=38.dp).clickable(enabled=enabled){actions=if(key in actions)actions-key else actions+key},verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(key in actions,{checked->if(enabled)actions=if(checked)actions+key else actions-key},enabled=enabled)
                                Text(label,Modifier.weight(1f),fontSize=13.sp,color=if(enabled)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text("SIGN-IN PASSWORD",fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=2.dp))
                    PasswordField(password,{password=it;error=null},"Password",modifier=Modifier.fillMaxWidth(),supportingText="At least 10 characters")
                    PasswordField(confirm,{confirm=it;error=null},"Confirm password",modifier=Modifier.fillMaxWidth())
                    error?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=11.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                    TextButton(enabled=!saving,onClick=onDismiss){Text("Cancel")}
                    Spacer(Modifier.width(8.dp))
                    Button(enabled=!saving,onClick={
                        val normalizedUsername=username.trim()
                        when {
                            displayName.trim().length !in 2..60 -> error="Display name must contain 2–60 characters."
                            !Regex("^[A-Za-z0-9._-]{3,40}$").matches(normalizedUsername) -> error="Username must be 3–40 characters and use letters, numbers, dot, underscore, or hyphen."
                            password.length < 10 -> error="Password must contain at least 10 characters."
                            password != confirm -> error="Password confirmation does not match."
                            departments.isEmpty() -> error="Select at least one department."
                            actions.isEmpty() -> error="Select at least one allowed action."
                            else -> { saving=true; error=null; scope.launch { try { onCreate(normalizedUsername,displayName.trim(),password,confirm,role,departments.toList(),actions.toList()); onDismiss() } catch(e:Exception) { error=e.message ?: "Could not create account." } finally { saving=false } } }
                        }
                    },shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp)){if(saving)CircularProgressIndicator(Modifier.size(17.dp),strokeWidth=2.dp)else Text("Create account")}
                }
            }
        }
    }
}

@Composable
private fun AccountRoleOption(label:String,selected:Boolean,modifier:Modifier=Modifier,onClick:()->Unit){
    Surface(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(11.dp),color=if(selected)MaterialTheme.colorScheme.surface else Color.Transparent,border=if(selected)androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)else null){Box(Modifier.padding(horizontal=8.dp,vertical=11.dp),contentAlignment=Alignment.Center){Text(label,fontSize=12.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,color=if(selected)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)}}
}

@Composable
private fun PasswordField(value:String,onValueChange:(String)->Unit,label:String,modifier:Modifier=Modifier,shape:RoundedCornerShape=RoundedCornerShape(13.dp),supportingText:String?=null){
    var visible by remember(label){mutableStateOf(false)}
    OutlinedTextField(value,onValueChange,label={Text(label)},singleLine=true,visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={visible=!visible}){Icon(MaterialSymbol(if(visible)"eye-off" else "eye"),if(visible)"Hide password" else "Show password",tint=MaterialTheme.colorScheme.onSurfaceVariant)}},supportingText=supportingText?.let{ { Text(it) } },modifier=modifier,shape=shape)
}

@Composable
private fun MeetingTimeRow(label: String, value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp), verticalAlignment=Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize=13.sp, fontWeight=FontWeight.Medium, color=MaterialTheme.colorScheme.onSurface)
        TimeInput(value, onChange)
    }
}

@Composable
private fun TimeInput(value: String, onChange: (String)->Unit) {
    val context = LocalContext.current
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0,23) ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0,59) ?: 0
    OutlinedButton(
        onClick = { TimePickerDialog(context, { _, h, m -> onChange("%02d:%02d".format(h,m)) }, hour, minute, false).show() },
        modifier = Modifier.width(132.dp).height(40.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal=10.dp, vertical=6.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.onSurface)
    ) {
        Icon(MaterialSymbol("clock-3"), null, Modifier.size(15.dp), tint=MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(prettyTime(value), fontSize=12.sp, fontWeight=FontWeight.SemiBold)
    }
}
@Composable
private fun PreferenceRow(icon: String, title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(MaterialSymbol(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun appSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.surface,
    uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
)
@Composable
private fun MetricCard(value:String,label:String,modifier:Modifier=Modifier){Card(modifier,shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(12.dp)){Text(value,fontSize=21.sp,fontWeight=FontWeight.Bold);Text(label,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
@Composable
private fun RowCard(icon:String,title:String,subtitle:String,trailing:String?=null,photoName:String?=null,photo:ImageBitmap?=null,onClick:()->Unit){Card(Modifier.fillMaxWidth().padding(bottom=8.dp).clickable { onClick() },shape=RoundedCornerShape(15.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){if(photoName!=null) Avatar(photoName,36.dp,photo) else Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.secondaryContainer),contentAlignment=Alignment.Center){Icon(MaterialSymbol(icon),null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold,fontSize=12.sp);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=10.sp,modifier=Modifier.padding(top=3.dp),maxLines=1,overflow=TextOverflow.Ellipsis)};if(trailing!=null)Text(trailing,fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)}}}
@Composable
private fun SectionTitle(title:String,action:String,onAction:()->Unit){Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=9.dp),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.Bold);TextButton(onClick=onAction,contentPadding=PaddingValues(4.dp)){Text(action,fontSize=11.sp,color=MaterialTheme.colorScheme.primary)}}}
@Composable
private fun FilterPill(label:String,selected:Boolean,onClick:()->Unit){Surface(onClick=onClick,shape=RoundedCornerShape(50),color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,border=if(selected)null else androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.defaultMinSize(minHeight=32.dp)){Box(Modifier.padding(horizontal=11.dp,vertical=7.dp),contentAlignment=Alignment.Center){Text(label,fontSize=10.sp,color=if(selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,maxLines=1)}}}
@Composable
private fun Avatar(name:String,size:androidx.compose.ui.unit.Dp=36.dp,photo:ImageBitmap?=null){Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),contentAlignment=Alignment.Center){if(photo!=null) Image(photo,contentDescription="Profile photo",modifier=Modifier.fillMaxSize().clip(CircleShape)) else Text(name.firstOrNull()?.uppercase()?:"?",fontSize=12.sp,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)}}
@Composable
private fun EmptyCard(text:String){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Text(text,Modifier.padding(17.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}

private fun roleLabel(role:String)=when(role){"admin"->"Administrator";"assistant"->"Department Editor";else->"Viewer"}
private fun canEditSlot(user: UserInfo?, department: String): Boolean {
    if (user?.role == "admin") return true
    if (user?.role != "assistant") return false
    val permissions = try { JSONObject(user.permissions) } catch (_: Exception) { JSONObject() }
    val departments = permissions.optJSONArray("departments")
    val actions = permissions.optJSONArray("actions")
    val mayApply = actions != null && (0 until actions.length()).any { actions.optString(it) in setOf("preview", "apply", "edit") }
    val hasDepartment = departments == null || (0 until departments.length()).any { departments.optString(it) == department }
    return mayApply && hasDepartment
}
private fun Slot.isAssigned(): Boolean = !personId.isNullOrBlank()
private fun deptLabel(dept:String)=when(dept){"mic"->"Mic Men";"av"->"Audio / Video";"attendant"->"Platform";else->dept}
private fun slotIcon(slot: Slot): String = when (slot.department) {
    "mic" -> "mic"
    "av" -> if (slot.key.contains("video", ignoreCase = true) || slot.label.contains("video", ignoreCase = true)) "camera" else "audio-lines"
    else -> "podium"
}
private fun meetingLabel(type:String)=when(type){"thursday"->"Thursday";"saturday"->"Saturday";"sunday"->"Sunday";else->type.replace('_',' ').split(' ').joinToString(" "){it.replaceFirstChar(Char::uppercase)}}
private fun meetingTitle(meeting: Meeting): String = meeting.displayLabel?.takeIf { it.isNotBlank() } ?: meetingLabel(meeting.type)
private fun friendlyDate(date:String)=try{LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMM d"))}catch(_:Exception){date}
private fun prettyTime(time:String)=try{val parts=time.split(":");val h=parts[0].toInt();val m=parts.getOrElse(1){"00"};"${if(h%12==0)12 else h%12}:$m ${if(h<12)"AM" else "PM"}"}catch(_:Exception){time}
private fun Person.eligibleSummary(config:LocalWorkspaceConfig?=null):String {
    val labels=if(config!=null) config.departments.filter { it.active }.mapNotNull { dept ->
        // Keep renamed built-in roles compatible with both current and older person records.
        val normalized=dept.label.trim().lowercase().replace("&","and").replace(Regex("[^a-z0-9]+")," ")
        val standardEligible=when {
            dept.id=="mic" || normalized in setOf("mic","mic men","microphone","microphones") -> mic
            dept.id=="av" || normalized in setOf("audio video","audio and video","audio","video","audio operator","video operator") -> audio||video
            dept.id=="attendant" || normalized in setOf("platform","platform operator") -> platform
            else -> false
        }
        dept.label.takeIf { standardEligible || dept.id in customDepartmentIds }
    } else buildList { if(mic)add("Mic Men");if(audio||video)add("Audio / Video");if(platform)add("Platform") }
    return labels.distinct().joinToString(" · ").ifBlank { "No eligible assignments" }
}
private fun personEligible(person:Person,meeting:Meeting,slot:Slot):Boolean {
    if(person.status!="active") return false
    if ((meeting.dayOfWeek == 4 || meeting.type=="thursday") && person.noThursday) return false
    val groupEligible = meeting.eligibleGroups?.contains(person.group) ?: when(meeting.type){"thursday"->true;"saturday"->person.group in 5..9;"sunday"->person.group in 1..4;else->true}
    if(!groupEligible) return false
    return when(slot.department){"mic"->person.mic;"av"->if(slot.key.contains("video"))person.video else person.audio;"attendant"->person.platform;else->slot.department in person.customDepartmentIds}
}
private fun shareText(context:android.content.Context,text:String){val intent=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text);context.startActivity(Intent.createChooser(intent,"Share Schedule"))}

/** Maps the app's semantic icon names to Google's Material Two-Tone icon set. */
private fun MaterialSymbol(name: String): String = when (name) {
    "house" -> "home"
    "log-in" -> "login"
    "log-out" -> "logout"
    "task-alt" -> "assignment_turned_in"
    "arrow-back" -> "arrow_back"
    "camera" -> "photo_camera"
    "circle-check" -> "check_circle"
    "circle-x" -> "cancel"
    "circle-minus" -> "do_not_disturb_on"
    "trophy" -> "trophy"
    "eye" -> "visibility"
    "eye-off" -> "visibility_off"
    "user-plus" -> "person_add"
    "calendar-check" -> "event_available"
    "calendar-off", "event-busy" -> "event_busy"
    "clipboard-check" -> "assignment_turned_in"
    "calendar-days" -> "calendar_month"
    "calendar-range" -> "date_range"
    "clock-3" -> "schedule"
    "users-round" -> "groups"
    "chart", "chart-no-axes-column-increasing" -> "bar_chart"
    "file-down" -> "download"
    "settings", "settings-2", "sliders-horizontal" -> "tune"
    "building", "building-2" -> "apartment"
    "user-round" -> "person"
    "audio-lines" -> "graphic_eq"
    "chevron-right" -> "chevron_right"
    "chevron-left" -> "chevron_left"
    "chevron-down" -> "keyboard_arrow_down"
    "chevron-up" -> "keyboard_arrow_up"
    "filter" -> "filter_list"
    "trash-2" -> "delete"
    "share-2" -> "share"
    "wand-sparkles" -> "auto_awesome"
    "lock-keyhole" -> "lock"
    "award" -> "workspace_premium"
    "moon" -> "dark_mode"
    "sun" -> "light_mode"
    "wifi-off" -> "wifi_off"
    "github" -> "code"
    else -> name.replace('-', '_')
}

/** Single global icon component: every app icon is rendered from Google's Two-Tone vectors. */
@Composable
private fun Icon(
    symbol: String,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current
) {
    androidx.compose.material3.Icon(
        imageVector = twoToneVector(symbol),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

/** Resolves both semantic names and Material Symbol ligatures to a curated Two-Tone vector. */
private fun twoToneVector(name: String): ImageVector = when (name) {
    "login" -> Icons.TwoTone.Login
    "arrow_back" -> Icons.AutoMirrored.TwoTone.ArrowBack
    "home", "house" -> Icons.TwoTone.Home
    "logout", "log-out" -> Icons.AutoMirrored.TwoTone.ExitToApp
    "photo_camera", "camera", "camera_alt" -> Icons.TwoTone.PhotoCamera
    "check_circle", "circle-check" -> Icons.TwoTone.CheckCircle
    "cancel", "circle-x" -> Icons.TwoTone.Cancel
    "do_not_disturb_on", "circle-minus" -> Icons.TwoTone.DoNotDisturbOn
    "trophy" -> Icons.TwoTone.EmojiEvents
    "workspace_premium", "award" -> Icons.TwoTone.WorkspacePremium
    "visibility", "eye" -> Icons.TwoTone.Visibility
    "visibility_off", "eye-off" -> Icons.TwoTone.VisibilityOff
    "person_add", "user-plus" -> Icons.TwoTone.PersonAdd
    "event_available", "calendar-check" -> Icons.TwoTone.EventAvailable
    "event_busy", "calendar-off" -> Icons.TwoTone.EventBusy
    "assignment_turned_in", "clipboard-check" -> Icons.TwoTone.AssignmentTurnedIn
    "calendar_month", "calendar-days" -> Icons.TwoTone.CalendarMonth
    "date_range", "calendar-range" -> Icons.TwoTone.DateRange
    "schedule", "clock-3" -> Icons.TwoTone.Schedule
    "groups", "users-round" -> Icons.TwoTone.Groups
    "bar_chart", "chart" -> Icons.TwoTone.BarChart
    "fingerprint" -> Icons.TwoTone.Fingerprint
    "system_update" -> Icons.TwoTone.SystemUpdate
    "download", "file-down" -> Icons.TwoTone.Download
    "archive" -> Icons.TwoTone.Archive
    "unarchive" -> Icons.TwoTone.Unarchive
    "add" -> Icons.TwoTone.Add
    "database" -> Icons.TwoTone.Storage
    "cloud" -> Icons.TwoTone.Cloud
    "upload" -> Icons.TwoTone.Upload
    "tune", "settings", "settings-2", "sliders-horizontal" -> Icons.TwoTone.Tune
    "apartment", "building", "building-2" -> Icons.TwoTone.Apartment
    "person", "user-round" -> Icons.TwoTone.Person
    "graphic_eq", "audio-lines" -> Icons.TwoTone.GraphicEq
    "podium", "platform" -> Icons.TwoTone.CoPresent
    "chevron_right" -> Icons.TwoTone.ChevronRight
    "chevron_left" -> Icons.TwoTone.ChevronLeft
    "keyboard_arrow_down", "chevron-down" -> Icons.TwoTone.KeyboardArrowDown
    "keyboard_arrow_up", "chevron-up" -> Icons.TwoTone.KeyboardArrowUp
    "filter_list", "filter" -> Icons.TwoTone.FilterList
    "delete", "trash-2" -> Icons.TwoTone.Delete
    "share", "share-2" -> Icons.TwoTone.Share
    "edit", "edit-note" -> Icons.TwoTone.Edit
    "auto_awesome", "wand-sparkles" -> Icons.TwoTone.AutoAwesome
    "block" -> Icons.TwoTone.Block
    "lock", "lock-keyhole" -> Icons.TwoTone.Lock
    "dark_mode", "moon" -> Icons.TwoTone.DarkMode
    "light_mode", "sun" -> Icons.TwoTone.LightMode
    "wifi_off" -> Icons.TwoTone.WifiOff
    "code", "github" -> Icons.TwoTone.Code
    "mic" -> Icons.TwoTone.Mic
    "event", "calendar" -> Icons.TwoTone.Event
    "more_horiz" -> Icons.TwoTone.MoreHoriz
    "menu" -> Icons.TwoTone.Menu
    "search" -> Icons.TwoTone.Search
    "close" -> Icons.TwoTone.Close
    "check" -> Icons.TwoTone.Check
    else -> Icons.TwoTone.Info
}







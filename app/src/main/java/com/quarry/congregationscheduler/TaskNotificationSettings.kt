package com.quarry.congregationscheduler

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskNotificationSettings(store: AppStore, scope: CoroutineScope, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    var panel by remember(store.user.value?.id) { mutableStateOf<NotificationPanelState>(NotificationPanelState.Loading) }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun decodeStatus(value: JSONObject): NotificationPanelState.Ready = NotificationPanelState.Ready(
        value.optJSONObject("person")?.optString("name")?.takeIf { it.isNotBlank() },
        value.optBoolean("configured"), value.optInt("devices"))
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { store.syncPush(); refresh++; scope.launch { snackbar.showSnackbar("Notifications enabled. This device will register when online.") } }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(store.user.value?.id, refresh) {
        panel = NotificationPanelState.Loading
        try { panel = decodeStatus(store.notificationStatus()) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (e: Exception) { panel = NotificationPanelState.Failed(e.message ?: "Could not check notification status.") }
    }
    OutlinedCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.TwoTone.Notifications, null); Text("Task notifications", style = MaterialTheme.typography.titleSmall) }
            Text("Receive new tasks assigned to the personnel linked to your account.", style = MaterialTheme.typography.bodySmall)
            NotificationStatusDetails(panel, onRetry = { refresh++ })
            Text(if (TaskPush.allowed(context)) "Enabled on this device" else "Disabled on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!BuildConfig.FCM_ENABLED) Text("Push notifications are available in the release app. TEST builds use a separate package.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            NotificationActionButtons(
                settingsEnabled = BuildConfig.FCM_ENABLED && !busy,
                testEnabled = BuildConfig.FCM_ENABLED && !busy && (panel as? NotificationPanelState.Ready)?.configured == true,
                busy = busy,
                onSettings = {
                    if (Build.VERSION.SDK_INT >= 33 && !TaskPush.allowed(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                },
                onTest = {
                    scope.launch {
                        busy = true; error = null
                        try {
                            TaskPush.registerNow(context)
                            val count = store.testNotification()
                            panel = decodeStatus(store.notificationStatus())
                            snackbar.showSnackbar("Test accepted by FCM for $count device(s).")
                        } catch (e: Exception) { error = e.message ?: "Could not send the test." }
                        finally { busy = false }
                    }
                })
            if (store.user.value?.role == "admin") TextButton(enabled = !busy && panel is NotificationPanelState.Ready, onClick = {
                scope.launch {
                    busy = true
                    try {
                        val result = store.retryNotifications()
                        snackbar.showSnackbar(if (result.optBoolean("configured")) "Queued notifications: ${result.optInt("sent")} accepted, ${result.optInt("failed")} failed." else "FCM server credentials are not configured yet.")
                    } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Retry failed.") }
                    finally { busy = false }
                }
            }) { Text("Retry queued notifications") }
        }
    }
}

@Composable
internal fun PersonnelNotificationActions(store: AppStore, account: ManagedUser, scope: CoroutineScope, snackbar: SnackbarHostState) {
    var showLink by remember(account.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showLink = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.TwoTone.Person, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Link personnel")
    }
    OutlinedButton(enabled = !busy && account.active, onClick = {
        scope.launch {
            busy = true
            try { val count = store.testNotification(account.id); snackbar.showSnackbar("Test accepted by FCM for $count device(s).") }
            catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Test notification failed.") }
            finally { busy = false }
        }
    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Sending…" else "Test notification") }
    if (showLink) PersonnelLinkDialog(store, account, onDismiss = { showLink = false })
}

@Composable
private fun PersonnelLinkDialog(store: AppStore, account: ManagedUser, onDismiss: () -> Unit) {
    var rows by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(account.id) {
        try {
            val result = store.personnelLinks()
            val people = result.getJSONArray("people")
            rows = (0 until people.length()).map { people.getJSONObject(it).let { p -> p.getString("id") to p.getString("name") } }
            val links = result.getJSONArray("links")
            selected = (0 until links.length()).map { links.getJSONObject(it) }.firstOrNull { it.getString("user_id") == account.id }?.getString("person_id")
            loaded = true
        } catch (e: Exception) { error = e.message ?: "Could not load personnel links." }
        finally { busy = false }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Link personnel") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(account.displayName)
            Text("Only an administrator can change this link. Tasks use the personnel ID, even when names change.", fontSize = 12.sp)
            OutlinedTextField(search, { search = it }, label = { Text("Search personnel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            if (loaded) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                item { Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { selected = null }.padding(8.dp)) { RadioButton(selected == null, onClick = null); Text("Not linked", Modifier.padding(start = 8.dp)) } }
                items(rows.filter { it.second.contains(search, true) }, key = { it.first }) { person ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { selected = person.first }.padding(8.dp)) {
                        RadioButton(selected == person.first, onClick = null); Text(person.second, Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(enabled = loaded && !busy, onClick = {
            scope.launch {
                busy = true
                try { store.linkPersonnel(account.id, selected); onDismiss() }
                catch (e: Exception) { error = e.message ?: "Could not save link." }
                finally { busy = false }
            }
        }) { Text("Save") }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
}

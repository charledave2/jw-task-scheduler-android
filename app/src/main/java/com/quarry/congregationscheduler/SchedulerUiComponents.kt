@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.quarry.congregationscheduler

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/** Insets and caps are applied before content fills its available bounds. */
@Composable
internal fun SchedulerDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest, properties = DialogProperties(
        dismissOnBackPress = properties.dismissOnBackPress,
        dismissOnClickOutside = properties.dismissOnClickOutside,
        securePolicy = properties.securePolicy,
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    )) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.matchParentSize().pointerInput(onDismissRequest, properties.dismissOnClickOutside) {
                detectTapGestures { if (properties.dismissOnClickOutside) onDismissRequest() }
            })
            Box(Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight)
                .testTag("scheduler-dialog").pointerInput(Unit) { detectTapGestures { } }) {
                content()
            }
        }
    }
}

@Composable
internal fun SchedulePosterPreview(poster: Bitmap?, modifier: Modifier = Modifier) {
    var zoom by remember(poster) { mutableFloatStateOf(1f) }
    var pan by remember(poster) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val imageSize = poster?.let { IntSize(it.width, it.height) } ?: IntSize.Zero
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (zoom * zoomChange).coerceIn(1f, 3f)
        pan = boundedPreviewPan(pan * (next / zoom) + panChange, next, viewport, imageSize)
        zoom = next
    }
    LaunchedEffect(viewport, poster) { pan = boundedPreviewPan(pan, zoom, viewport, imageSize) }
    val height = (LocalConfiguration.current.screenHeightDp * .48f).coerceIn(180f, 480f).dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Preview", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }, enabled = zoom > 1f,
                modifier = Modifier.testTag("preview-fit")) { Text("Fit") }
        }
        Text("Pinch to zoom · ${(zoom * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .onSizeChanged { viewport = it }
            .testTag("schedule-preview")
            .semantics { stateDescription = "Zoom ${(zoom * 100).roundToInt()}%" }
            .transformable(transform, canPan = { zoom > 1f }), contentAlignment = Alignment.Center) {
            if (poster == null) CircularProgressIndicator() else Image(poster.asImageBitmap(),
                contentDescription = "Schedule poster preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y))
        }
    }
}

@Composable
internal fun NotificationStatusDetails(state: NotificationPanelState, onRetry: () -> Unit) {
    when (state) {
        NotificationPanelState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("Checking notification status…", style = MaterialTheme.typography.bodySmall)
        }
        is NotificationPanelState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Notification status unavailable", style = MaterialTheme.typography.titleSmall)
            Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("Try again") }
        }
        is NotificationPanelState.Ready -> {
            Text(state.personName?.let { "Linked personnel: $it" } ?: "Account not linked. Ask an administrator to link your personnel record.", style = MaterialTheme.typography.bodySmall)
            Text("Registered devices: ${state.devices}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!state.configured) Text("Server setup is pending. Your administrator must configure Firebase credentials.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotificationActionButtons(settingsEnabled: Boolean, testEnabled: Boolean, busy: Boolean, onSettings: () -> Unit, onTest: () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = settingsEnabled, onClick = onSettings, modifier = Modifier.testTag("notification-settings")) { Text("Notification settings") }
        Button(enabled = testEnabled, onClick = onTest, modifier = Modifier.testTag("notification-test")) { Text(if (busy) "Sending…" else "Test notification") }
    }
}

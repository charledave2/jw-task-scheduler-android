package com.quarry.congregationscheduler

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.YearMonth

/** Debug-only, deterministic host for responsive UI regression checks. No accounts or cloud data. */
class UiReviewActivity : ComponentActivity() {
    var screen by mutableStateOf("navigation")
    var page by mutableStateOf("Export Studio")
    var moreOpen by mutableStateOf(false)
    var reviewFontScale by mutableFloatStateOf(1f)
    var reviewDark by mutableStateOf(false)
    internal var panel by mutableStateOf<NotificationPanelState>(NotificationPanelState.Loading)
    private val poster by lazy {
        Bitmap.createBitmap(600, 1200, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(android.graphics.Color.rgb(244, 240, 224))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f }
            repeat(12) { index ->
                paint.color = if (index % 2 == 0) android.graphics.Color.rgb(52, 71, 58) else android.graphics.Color.rgb(92, 116, 95)
                canvas.drawRect(24f, index * 96f + 10, 576f, index * 96f + 88, paint)
                paint.color = android.graphics.Color.WHITE
                canvas.drawText("Meeting ${index + 1} · Assigned personnel", 40f, index * 96f + 60, paint)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, reviewFontScale)) {
                SchedulerTheme(reviewDark) {
                    Surface(Modifier.fillMaxSize()) {
                        when (screen) {
                            "person" -> PersonEditorDialog(
                                LocalWorkspaceConfig(4, emptyList(), emptyList()),
                                Person("ui-review", "Sample Brother", 2, "active", false, true, true, true, true), null, emptyList(), YearMonth.now(),
                                onDismiss = { screen = "navigation" }, onSave = {}, onDelete = {}, onPhotoSelected = { _, _ -> }, onRemovePhoto = {})
                            "preview" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { SchedulePosterPreview(poster) }
                            "status" -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { NotificationStatusDetails(panel) { panel = NotificationPanelState.Loading }; NotificationActionButtons(true, true, false, {}, {}) }
                            else -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                                PreviewBottomNavigation(page, moreOpen, { true }, { moreOpen = true }, { page = it; moreOpen = false })
                            }
                        }
                    }
                }
            }
        }
    }
}

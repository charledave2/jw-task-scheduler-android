@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.quarry.congregationscheduler

import android.content.pm.ActivityInfo
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.test.filters.SdkSuppress
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@SdkSuppress(minSdkVersion = 29)
class UiConsistencyTest {
    @get:Rule val compose = createAndroidComposeRule<UiReviewActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Wait for accessibility and the platform window to settle before capturing its framebuffer.
        device.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // PixelCopy through Compose captures the rendered root, including a separate dialog window.
        val target = if (name.startsWith("dialog-")) compose.onNodeWithTag("scheduler-dialog") else compose.onRoot()
        val image = target.captureToImage().asAndroidBitmap()
        val resolver = compose.activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/jw-ui-review")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create screenshot")
        resolver.openOutputStream(uri)!!.use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        image.recycle()
    }
    @Test fun notificationsNeverShowUnlinkedWhileLoadingOrFailed() {
        compose.runOnIdle { compose.activity.screen = "status" }
        compose.onNodeWithText("Checking notification status…").assertIsDisplayed()
        compose.onNodeWithText("Account not linked", substring = true).assertDoesNotExist()
        compose.runOnIdle { compose.activity.panel = NotificationPanelState.Failed("Network unavailable") }
        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithText("Account not linked", substring = true).assertDoesNotExist()
        compose.runOnIdle { compose.activity.panel = NotificationPanelState.Ready("Sample Brother", true, 1) }
        compose.onNodeWithText("Linked personnel: Sample Brother").assertIsDisplayed()
        screenshot("notification-ready")
    }
    @Test fun navigationSelectsExportRatherThanTheProminentManageButton() {
        compose.onNodeWithTag("nav-Export Studio").assertIsSelected()
        compose.onNodeWithTag("nav-Manage Schedule").assertIsNotSelected()
        compose.onNodeWithTag("nav-More").performClick()
        compose.onNodeWithTag("nav-More").assertIsSelected()
        compose.onNodeWithTag("nav-Export Studio").assertIsNotSelected()
        screenshot("navigation-selection")
    }
    @Test fun previewZoomAndFitResetWithoutRebuildingTheImage() {
        compose.runOnIdle { compose.activity.screen = "preview" }
        compose.onNodeWithTag("schedule-preview").performTouchInput {
            pinch(start0 = Offset(center.x - 30, center.y), start1 = Offset(center.x + 30, center.y),
                end0 = Offset(center.x - 110, center.y), end1 = Offset(center.x + 110, center.y))
        }
        compose.onNodeWithTag("preview-fit").assertIsEnabled()
        compose.onNodeWithTag("schedule-preview").performTouchInput { swipeUp() }
        screenshot("preview-zoomed")
        compose.onNodeWithTag("preview-fit").performClick()
        assertEquals("Zoom 100%", compose.onNodeWithTag("schedule-preview").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        screenshot("preview-fit")
    }
    @Test fun personDialogFitsPortraitLandscapeTabletAndLargeFontWithKeyboard() {
        try {
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
            val cases = listOf("portrait", "landscape", "tablet", "large-font")
            for (name in cases) {
                device.executeShellCommand(if (name == "tablet") "wm size 1600x2560" else "wm size 1080x1920")
                device.executeShellCommand(if (name == "tablet") "wm density 240" else "wm density 480")
                compose.runOnIdle {
                    compose.activity.requestedOrientation = if (name == "landscape") ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    compose.activity.reviewFontScale = if (name == "large-font") 2f else 1f
                    compose.activity.screen = "person"
                }
                device.waitForIdle()
                compose.waitForIdle()
                val expectedWidth = when (name) { "landscape" -> 640; "tablet" -> 1066; else -> 360 }
                compose.waitUntil(10000) {
                    val metrics = android.util.DisplayMetrics()
                    @Suppress("DEPRECATION")
                    compose.activity.windowManager.defaultDisplay.getRealMetrics(metrics)
                    kotlin.math.abs(metrics.widthPixels / metrics.density - expectedWidth) <= 2f
                }
                val bounds = compose.onNodeWithTag("scheduler-dialog").fetchSemanticsNode().boundsInWindow
                val density = compose.activity.resources.displayMetrics.density
                assertTrue("$name dialog too wide: $bounds", bounds.width / density <= 481f)
                screenshot("dialog-$name")
                if (name == "landscape" || name == "large-font") compose.onNodeWithText("Save changes").performScrollTo()
                compose.onNodeWithText("Save changes").assertIsDisplayed()
                if (name == "large-font") {
                    compose.onAllNodes(hasSetTextAction()).onFirst().performScrollTo().assertIsDisplayed().performClick()
                    try {
                        compose.waitUntil(10000) { compose.onNodeWithTag("scheduler-dialog").fetchSemanticsNode().config[SchedulerImeVisibleKey] }
                    } finally { screenshot("dialog-$name-keyboard") }
                    compose.onNodeWithText("Save changes").performScrollTo().assertIsDisplayed()
                    screenshot("dialog-$name-keyboard-actions")
                    val dialog = compose.onNodeWithTag("scheduler-dialog").fetchSemanticsNode().boundsInWindow
                    val save = compose.onNodeWithText("Save changes").fetchSemanticsNode().boundsInWindow
                    assertTrue("Save action falls below the keyboard-safe dialog", save.bottom <= dialog.bottom + 1f)
                    device.pressBack()
                    compose.waitUntil(10000) { !compose.onNodeWithTag("scheduler-dialog").fetchSemanticsNode().config[SchedulerImeVisibleKey] }
                }
                compose.runOnIdle { compose.activity.screen = "preview" }
                screenshot("preview-$name")
                compose.runOnIdle { compose.activity.screen = "status"; compose.activity.panel = NotificationPanelState.Ready("Sample Brother", true, 1) }
                screenshot("notification-$name-before-scroll")
                compose.onNodeWithTag("notification-test").performScrollTo().assertIsDisplayed()
                val settings = compose.onNodeWithTag("notification-settings").fetchSemanticsNode().boundsInWindow
                val test = compose.onNodeWithTag("notification-test").fetchSemanticsNode().boundsInWindow
                assertTrue("$name notification actions overlap", test.left >= settings.right || test.top >= settings.bottom)
                screenshot("notification-$name")
                compose.runOnIdle { compose.activity.screen = "navigation" }
                screenshot("navigation-$name")
                if (name == "large-font") {
                    listOf("Calendar", "Manage", "Export").forEach { label ->
                        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                        compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        assertFalse("Navigation label is truncated at 2x text: $label", layouts.single().hasVisualOverflow)
                    }
                }
            }
        } finally {
            compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            device.executeShellCommand("wm size reset")
            device.executeShellCommand("wm density reset")
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 0")
        }
    }
}

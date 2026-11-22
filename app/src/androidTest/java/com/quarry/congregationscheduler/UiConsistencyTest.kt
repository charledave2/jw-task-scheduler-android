package com.quarry.congregationscheduler

import android.content.pm.ActivityInfo
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

class UiConsistencyTest {
    @get:Rule val compose = createAndroidComposeRule<UiReviewActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = File(compose.activity.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(directory, "$name.png")))
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
                val bounds = compose.onNodeWithTag("scheduler-dialog").fetchSemanticsNode().boundsInWindow
                val density = compose.activity.resources.displayMetrics.density
                assertTrue("$name dialog too wide: $bounds", bounds.width / density <= 481f)
                compose.onNodeWithText("Save changes", useUnmergedTree = true).assertIsDisplayed()
                screenshot("dialog-$name")
                if (name == "large-font") {
                    compose.onAllNodes(hasSetTextAction()).onFirst().performClick()
                    compose.onNodeWithText("Save changes", useUnmergedTree = true).assertIsDisplayed()
                    screenshot("dialog-$name-keyboard")
                    device.pressBack()
                }
                compose.runOnIdle { compose.activity.screen = "preview" }
                screenshot("preview-$name")
                compose.runOnIdle { compose.activity.screen = "status"; compose.activity.panel = NotificationPanelState.Ready("Sample Brother", true, 1) }
                compose.onNodeWithTag("notification-test").assertIsDisplayed()
                val settings = compose.onNodeWithTag("notification-settings").fetchSemanticsNode().boundsInWindow
                val test = compose.onNodeWithTag("notification-test").fetchSemanticsNode().boundsInWindow
                assertTrue("$name notification actions overlap", test.left >= settings.right || test.top >= settings.bottom)
                screenshot("notification-$name")
                compose.runOnIdle { compose.activity.screen = "navigation" }
                screenshot("navigation-$name")
            }
        } finally {
            device.executeShellCommand("wm size reset")
            device.executeShellCommand("wm density reset")
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 0")
        }
    }
}

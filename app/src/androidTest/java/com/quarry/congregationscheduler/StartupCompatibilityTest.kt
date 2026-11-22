package com.quarry.congregationscheduler

import android.content.Context
import android.content.Intent
import java.io.ByteArrayOutputStream
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupCompatibilityTest {
    @Test fun launchAndRecreationReachWorkspaceSetupOnMinimumAndroidVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("device_app_lock", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).commit()
        context.getSharedPreferences("scheduler_preferences", Context.MODE_PRIVATE).edit().remove("workspace_mode").commit()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Earlier lock tests intentionally leave/reopen a task. Start a fresh task so
        // this smoke test does not inherit a previous controller's locked window.
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        fun assertSetup() {
            val visible = device.wait(Until.hasObject(By.text("Set up local workspace")), 30000)
            val diagnostic = if (visible) "" else ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString("UTF-8")
            assertTrue("Workspace setup was not visible: $diagnostic", visible)
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            assertSetup()
            scenario.recreate()
            assertSetup()
        }
    }
}

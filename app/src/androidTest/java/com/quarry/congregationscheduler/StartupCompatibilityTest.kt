package com.quarry.congregationscheduler

import android.content.Context
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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.text("Set up local workspace")), 15000))
            scenario.recreate()
            assertTrue(device.wait(Until.hasObject(By.text("Set up local workspace")), 15000))
        }
    }
}

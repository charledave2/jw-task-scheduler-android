package com.quarry.congregationscheduler

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real lock window on a fresh Android emulator without an enrolled screen lock. */
@RunWith(AndroidJUnit4::class)
class AppLockGateTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    @Before fun enableSavedLock() {
        assertTrue(context.getSharedPreferences("device_app_lock",Context.MODE_PRIVATE).edit().putBoolean("enabled",true).commit())
    }
    @After fun restorePreference() {
        context.getSharedPreferences("device_app_lock",Context.MODE_PRIVATE).edit().putBoolean("enabled",false).commit()
    }
    private fun assertLocked() {
        assertTrue(device.wait(Until.hasObject(By.text("App locked")),10000))
        assertNull(device.findObject(By.text("Set up local workspace")))
        assertNull(device.findObject(By.text("Connect shared workspace")))
    }
    @Test fun coldLaunchAndRecreationKeepWorkspaceHidden() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertLocked()
            scenario.recreate()
            assertLocked()
        }
    }
    @Test fun backAndReopenCannotBypassAuthentication() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertLocked()
            device.pressBack()
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            assertLocked()
        }
    }
}

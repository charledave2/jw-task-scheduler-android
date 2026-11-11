package com.quarry.congregationscheduler

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavPreviewSmokeTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    @Before fun prepare() {
        assumeTrue(BuildConfig.NAV_PREVIEW)
        context.getSharedPreferences("device_app_lock",Context.MODE_PRIVATE).edit().putBoolean("enabled",false).commit()
        context.getSharedPreferences("scheduler_preferences",Context.MODE_PRIVATE).edit().putBoolean("dark_appearance",false).commit()
        device.setOrientationNatural()
    }
    private fun capture(name:String) {
        val folder=context.getExternalFilesDir(null)!!
        folder.mkdirs()
        assertTrue(device.takeScreenshot(File(folder,name)))
    }
    @Test fun separatePackageAndBottomNavigationWorkInBothOrientations() {
        assertEquals("com.quarry.congregationscheduler.navtest",context.packageName)
        assertTrue(BuildConfig.NAV_PREVIEW)
        kotlinx.coroutines.runBlocking { assertNull(AppUpdater.checkForUpdate()) }
        try { AppUpdater.install(context,File("/preview-no-install.apk"));fail("Preview must not install production updates") } catch(expected:IllegalStateException) { assertTrue(expected.message!!.contains("disabled")) }
        val original=context.packageManager.getPackageInfo("com.quarry.congregationscheduler",0)
        assertEquals("1.0.59",original.versionName)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.desc("Manage schedule")),15000))
            assertTrue(device.wait(Until.hasObject(By.text("Navigation Preview")),10000))
            assertNull(device.findObject(By.desc("Open navigation")))
            capture("nav-preview-light.png")
            repeat(2){device.findObject(By.desc("Manage schedule")).click();Thread.sleep(700)}
            assertTrue(device.wait(Until.hasObject(By.text("Manage Schedule")),5000))
            device.findObject(By.desc("Calendar")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Schedule Calendar")),5000))
            device.findObject(By.desc("More navigation")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Settings & Accounts")),5000))
            device.findObject(By.text("Settings & Accounts")).click()
            assertTrue(device.wait(Until.hasObject(By.text("App Preferences")),5000))
            context.getSharedPreferences("scheduler_preferences",Context.MODE_PRIVATE).edit().putBoolean("dark_appearance",true).commit()
            scenario.recreate()
            assertTrue(device.wait(Until.hasObject(By.desc("Manage schedule")),10000))
            capture("nav-preview-dark.png")
            device.setOrientationLeft();Thread.sleep(1300)
            assertTrue(device.wait(Until.hasObject(By.desc("Manage schedule")),5000))
            device.findObject(By.desc("Manage schedule")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Manage Schedule")),5000))
            Thread.sleep(900);capture("nav-preview-landscape.png")
            device.setOrientationNatural()
        }
    }
}

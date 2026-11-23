package com.quarry.congregationscheduler

import android.content.Context
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Invoked in two separate instrumentation processes by verify-session-restart.sh. */
class SessionProcessRestartTest {
    @Test fun persistedSignInSurvivesForceStop() {
        val phase = InstrumentationRegistry.getArguments().getString("session_phase")
        assumeTrue("Run through the two-process restart script", phase != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = context.getSharedPreferences("session_restart_probe", Context.MODE_PRIVATE)
        val tokens = TokenStore(context)
        val user = UserInfo("restart-fixture", "sample", "Sample Brother", "viewer", "{}")
        if (phase == "seed") {
            tokens.clear()
            tokens.saveLogin("restart-access", "restart-refresh", user)
            assertTrue(marker.edit().putInt("seed_pid", Process.myPid()).commit())
        } else {
            assertEquals("verify", phase)
            assertNotEquals(marker.getInt("seed_pid", Process.myPid()), Process.myPid())
            assertEquals("restart-access", tokens.getAccess())
            assertEquals("restart-refresh", tokens.getRefresh())
            assertEquals(user, tokens.getUser())
            tokens.clear()
            assertTrue(marker.edit().clear().commit())
        }
    }
}

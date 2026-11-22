package com.quarry.congregationscheduler

import org.junit.Assert.*
import org.junit.Test

class AppVersionTest {
    @Test fun olderAndSameReleaseDoNotInterruptATestBuild() {
        assertFalse(isNewerAppVersion("1.0.73", "1.0.74-test"))
        assertFalse(isNewerAppVersion("1.0.74", "1.0.74-test"))
        assertFalse(isNewerAppVersion("1.0.74", "v1.0.74+79"))
    }
    @Test fun realNewerReleaseStillOffersAnUpdate() {
        assertTrue(isNewerAppVersion("1.0.75", "1.0.74-test"))
        assertTrue(isNewerAppVersion("1.0.74", "1.0.73"))
        assertTrue(isNewerAppVersion("1.1.0", "1.0.99"))
    }
}

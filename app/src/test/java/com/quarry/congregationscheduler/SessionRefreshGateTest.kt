package com.quarry.congregationscheduler

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class SessionRefreshGateTest {
    @Test fun simultaneousExpiredRequestsRotateOnlyOnce() {
        val gate = SessionRefreshGate()
        var access = "expired"
        val rotations = AtomicInteger()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val results = (1..8).map { executor.submit<String> {
                start.await()
                gate.access("expired", { access }) { rotations.incrementAndGet(); access = "fresh"; access }
            } }
            start.countDown()
            results.forEach { assertEquals("fresh", it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, rotations.get())
        } finally { executor.shutdownNow() }
    }
    @Test fun temporaryRefreshFailureCanBeRetriedWithoutDiscardingSession() {
        val gate = SessionRefreshGate()
        var saved = "expired"
        try { gate.access(saved, { saved }) { throw java.io.IOException("offline") }; fail() } catch (_: java.io.IOException) { }
        assertEquals("expired", saved)
        assertEquals("new", gate.access(saved, { saved }) { saved = "new"; saved })
    }
    @Test fun temporaryKeystoreFailureDoesNotTriggerCredentialDestruction() {
        assertFalse(java.security.KeyStoreException("temporarily unavailable").hasPermanentSessionKeyFailure())
        assertFalse(java.io.IOException("network").hasPermanentSessionKeyFailure())
        assertTrue(java.security.GeneralSecurityException(javax.crypto.AEADBadTagException()).hasPermanentSessionKeyFailure())
    }
}

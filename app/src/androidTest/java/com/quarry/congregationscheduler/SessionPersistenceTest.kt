package com.quarry.congregationscheduler

import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SessionPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun encryptedSessionAndProfileSurviveStoreRecreation() {
        val store = TokenStore(context)
        store.clear()
        try {
            val profile = UserInfo("session-fixture", "sample", "Sample Brother", "viewer", "{}")
            store.saveLogin("access-fixture", "refresh-fixture", profile)
            TokenStore.reopenForTest()
            val reopened = TokenStore(context)
            assertEquals("access-fixture", reopened.getAccess())
            assertEquals("refresh-fixture", reopened.getRefresh())
            assertEquals(profile, reopened.getUser())
            val raw = java.io.File(context.applicationInfo.dataDir, "shared_prefs/secure_session.xml").readText()
            assertFalse(raw.contains("access-fixture"))
            assertFalse(raw.contains("refresh-fixture"))
            assertFalse(raw.contains("Sample Brother"))
            reopened.clear()
            TokenStore.reopenForTest()
            assertNull(TokenStore(context).getAccess())
            assertNull(TokenStore(context).getUser())
        } finally { store.clear() }
    }
    @Test fun destroyedKeystoreKeyRecoversAndNewSignInPersists() {
        val store = TokenStore(context)
        store.clear()
        try {
            store.save("old-access", "old-refresh")
            java.security.KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(androidx.security.crypto.MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
            TokenStore.reopenForTest()
            val recovered = TokenStore(context)
            assertNull(recovered.getAccess())
            recovered.save("replacement-access", "replacement-refresh")
            TokenStore.reopenForTest()
            assertEquals("replacement-refresh", TokenStore(context).getRefresh())
        } finally { TokenStore(context).clear() }
    }
    @Test fun concurrentClientsShareTheSameEncryptedKeyset() {
        TokenStore(context).clear()
        TokenStore.reopenForTest()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val clients = (1..8).map { executor.submit<TokenStore> { TokenStore(context).also { it.getAccess() } } }
                .map { it.get(10, TimeUnit.SECONDS) }
            clients.first().save("access", "refresh")
            clients.forEach { assertEquals("access", it.getAccess()); assertEquals("refresh", it.getRefresh()) }
        } finally { executor.shutdownNow(); TokenStore(context).clear() }
    }
}

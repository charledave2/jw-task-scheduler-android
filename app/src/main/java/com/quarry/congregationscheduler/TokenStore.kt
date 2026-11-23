package com.quarry.congregationscheduler

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore

/** Reuse one encrypted preferences/keyset instance throughout this process. */
class TokenStore(context: Context) {
    private val app = context.applicationContext
    companion object {
        internal val refreshGate = SessionRefreshGate()
        private var shared: SharedPreferences? = null
        private const val FILE = "secure_session"
        internal fun reopenForTest() = synchronized(refreshGate.monitor) { shared = null }
    }
    private fun open(): SharedPreferences {
        shared?.let { return it }
        var failure: Exception? = null
        repeat(3) { attempt ->
            try {
                val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                return EncryptedSharedPreferences.create(app, FILE, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM).also { shared = it }
            } catch (e: Exception) {
                failure = e
                if (e.hasPermanentSessionKeyFailure()) throw e
                if (attempt < 2) Thread.sleep(50L * (attempt + 1))
            }
        }
        throw IOException("Secure sign-in storage is temporarily unavailable. Unlock your device and try again.", failure)
    }
    private fun <T> use(block: (SharedPreferences) -> T): T = synchronized(refreshGate.monitor) {
        try { block(open()) } catch (e: Exception) {
            if (!e.hasPermanentSessionKeyFailure()) throw e
            // A destroyed key cannot decrypt old credentials. Recover once; never wipe for a temporary Keystore error.
            shared = null
            check(app.deleteSharedPreferences(FILE)) { "Could not recover secure sign-in storage." }
            if (generateSequence<Throwable>(e) { it.cause }.take(12).any { it.javaClass.simpleName in setOf("KeyPermanentlyInvalidatedException", "UnrecoverableKeyException") }) {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS) }
            }
            block(open())
        }
    }
    private fun SharedPreferences.Editor.persist() { if (!commit()) throw IOException("Could not save your sign-in securely. Please try again.") }
    fun getUserId(): String? = use { it.getString("user_id", null) }
    fun saveUserId(id: String) = use { it.edit().putString("user_id", id).persist() }
    fun clearUserId() = use { it.edit().remove("user_id").persist() }
    fun getAccess(): String? = use { it.getString("access", null) }
    fun getRefresh(): String? = use { it.getString("refresh", null) }
    fun save(access: String, refresh: String) = use { it.edit().putString("access", access).putString("refresh", refresh).persist() }
    fun saveAccess(access: String) = use { it.edit().putString("access", access).persist() }
    fun clear() = use { it.edit().clear().persist() }
    fun saveLogin(access: String, refresh: String, user: UserInfo) = use {
        it.edit().putString("access", access).putString("refresh", refresh).putString("user_id", user.id).putString("user", encode(user)).persist()
    }
    fun saveUser(user: UserInfo) = use {
        val savedId = it.getString("user_id", null)
        if (savedId == user.id || (savedId == null && !it.getString("access", null).isNullOrBlank())) {
            it.edit().putString("user_id", user.id).putString("user", encode(user)).persist()
        }
    }
    fun getUser(): UserInfo? = use { prefs ->
        prefs.getString("user", null)?.let { text -> runCatching {
            val value = JSONObject(text)
            UserInfo(value.getString("id"), value.getString("username"), value.getString("displayName"), value.getString("role"), value.getString("permissions"))
                .takeIf { it.id == prefs.getString("user_id", null) }
        }.getOrNull() }
    }
    private fun encode(user: UserInfo) = JSONObject().put("id", user.id).put("username", user.username)
        .put("displayName", user.displayName).put("role", user.role).put("permissions", user.permissions).toString()
}

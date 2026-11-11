package com.quarry.congregationscheduler

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppLockController(private val activity: AppCompatActivity) {
    private val preferences = activity.getSharedPreferences("device_app_lock", Context.MODE_PRIVATE)
    private val policy = AppLockPolicy(preferences.getBoolean("enabled", false))
    private val keyguard = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    var enabled by mutableStateOf(policy.enabled)
        private set
    var locked by mutableStateOf(policy.locked)
        private set
    var hasOpenedSession by mutableStateOf(policy.hasOpenedSession)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private var disposed = false
    private var suppressNextResume = false
    private var prompt: BiometricPrompt? = null
    private var credentialTicket: Long? = null
    private val credentialLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val ticket = credentialTicket
        credentialTicket = null
        if (ticket != null) finish(ticket, result.resultCode == Activity.RESULT_OK, "Unlock cancelled. Tap Unlock to try again.")
    }

    init { publish() }

    private fun publish() {
        enabled = policy.enabled
        locked = policy.locked
        hasOpenedSession = policy.hasOpenedSession
        busy = policy.pending != null
        if (enabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun onBackground() {
        suppressNextResume = false
        policy.background()
        if (Build.VERSION.SDK_INT < 28 && credentialTicket == null && policy.pending != null) {
            policy.abandon()
            runCatching { prompt?.cancelAuthentication() }
        }
        publish()
    }
    fun onForeground() {
        if (suppressNextResume) { suppressNextResume = false; return }
        if (locked && !busy) unlock()
    }
    fun unlock() = authenticate(LockAction.UNLOCK)
    fun changeEnabled(wanted: Boolean) = authenticate(if (wanted) LockAction.ENABLE else LockAction.DISABLE)
    fun openSecuritySettings() {
        runCatching { activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
            .onFailure { message = "Open your phone Settings to configure a screen lock or biometrics." }
    }
    fun closeApp() { activity.moveTaskToBack(true) }

    private fun authenticate(action: LockAction) {
        if (disposed || busy || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (!keyguard.isDeviceSecure) {
            message = "Set up a PIN, pattern or password in your phone Settings first."
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            return
        }
        val attempt = policy.begin(action) ?: return
        message = null
        publish()
        val strong = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val authenticators = if (Build.VERSION.SDK_INT >= 30) strong or BiometricManager.Authenticators.DEVICE_CREDENTIAL else strong
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            launchCredential(attempt.id)
            return
        }
        try {
            prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (credentialTicket == null) finish(attempt.id, true, null)
                }
                override fun onAuthenticationFailed() {
                    if (!disposed && policy.pending?.id == attempt.id) message = "Biometric not recognized. Try again or use your phone screen lock."
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (disposed || policy.pending?.id != attempt.id || credentialTicket == attempt.id) return
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && Build.VERSION.SDK_INT < 30) {
                        launchCredential(attempt.id)
                    } else if (errorCode == BiometricPrompt.ERROR_LOCKOUT || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                        launchCredential(attempt.id)
                    } else finish(attempt.id, false, errString.toString())
                }
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(if (action == LockAction.UNLOCK) "Unlock JW Task Scheduler" else if (action == LockAction.ENABLE) "Enable app lock" else "Turn off app lock")
                .setSubtitle("Use a supported biometric or your phone screen lock")
                .setAllowedAuthenticators(authenticators)
            if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText("Use screen lock")
            prompt?.authenticate(info.build())
        } catch (_: Exception) { launchCredential(attempt.id) }
    }

    @Suppress("DEPRECATION")
    private fun launchCredential(ticket: Long) {
        if (disposed || policy.pending?.id != ticket) return
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            finish(ticket, false, "Authentication paused. Tap Unlock when you return.")
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent("JW Task Scheduler", "Confirm your phone screen lock")
        if (intent == null) { finish(ticket, false, "Configure a screen lock in phone Settings, then try again."); return }
        credentialTicket = ticket
        try { credentialLauncher.launch(intent) }
        catch (_: Exception) { credentialTicket = null; finish(ticket, false, "Could not open the phone screen lock. Try again.") }
    }

    private fun finish(ticket: Long, authenticated: Boolean, error: String?) {
        if (disposed) return
        val attempt = policy.pending?.takeIf { it.id == ticket } ?: return
        val foreground = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        var success = authenticated && foreground
        if (success && attempt.action != LockAction.UNLOCK) {
            // Persist only the preference, never biometric data or the unlocked session.
            success = preferences.edit().putBoolean("enabled", attempt.action == LockAction.ENABLE).commit()
        }
        policy.finish(ticket, success, foreground)
        if (!foreground) policy.background()
        message = if (success) null else error ?: "Authentication was not completed. Tap Unlock to try again."
        if (!success) suppressNextResume = foreground
        publish()
    }

    fun dispose() {
        disposed = true
        policy.abandon()
        credentialTicket = null
        runCatching { prompt?.cancelAuthentication() }
    }
}

package com.quarry.congregationscheduler

internal enum class LockAction { UNLOCK, ENABLE, DISABLE }
internal data class LockAttempt(val id: Long, val action: LockAction)

/** Only successful system authentication may unlock or change the saved preference. */
internal class AppLockPolicy(initiallyEnabled: Boolean) {
    var enabled = initiallyEnabled
        private set
    var locked = initiallyEnabled
        private set
    var hasOpenedSession = !initiallyEnabled
        private set
    var pending: LockAttempt? = null
        private set
    private var sequence = 0L

    fun begin(action: LockAction): LockAttempt? {
        if (pending != null) return null
        if (action == LockAction.UNLOCK && (!enabled || !locked)) return null
        if (action == LockAction.ENABLE && enabled) return null
        if (action == LockAction.DISABLE && !enabled) return null
        return LockAttempt(++sequence, action).also { pending = it }
    }
    fun finish(id: Long, authenticated: Boolean, foreground: Boolean = true): Boolean {
        val attempt = pending?.takeIf { it.id == id } ?: return false
        pending = null
        if (!authenticated || !foreground) return false
        when (attempt.action) {
            LockAction.ENABLE -> enabled = true
            LockAction.DISABLE -> enabled = false
            LockAction.UNLOCK -> Unit
        }
        locked = false
        hasOpenedSession = true
        return true
    }
    fun background() { locked = enabled }
    fun abandon() { pending = null }
}

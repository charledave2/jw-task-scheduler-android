package com.quarry.congregationscheduler

/** A Later choice expires when the app enters its next foreground session. */
internal class UpdatePromptSession {
    var generation = 0
        private set
    var dismissed = false
        private set
    fun begin() { generation += 1; dismissed = false }
    fun dismiss() { dismissed = true }
    fun accepts(requestGeneration: Int) = !dismissed && requestGeneration == generation
}

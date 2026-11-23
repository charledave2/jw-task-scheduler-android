package com.quarry.congregationscheduler

/** One refresh rotation per rejected access token, shared by foreground and worker clients. */
internal class SessionRefreshGate {
    val monitor = Any()
    fun access(rejected: String?, current: () -> String?, rotate: () -> String): String = synchronized(monitor) {
        val saved = current()
        if (saved != null && saved != rejected) saved else rotate()
    }
}

internal fun Throwable.hasPermanentSessionKeyFailure(): Boolean = generateSequence(this) { it.cause }
    .take(12).any { it.javaClass.simpleName in setOf("KeyPermanentlyInvalidatedException", "UnrecoverableKeyException", "AEADBadTagException", "InvalidProtocolBufferException") }

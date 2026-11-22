package com.quarry.congregationscheduler

import java.util.UUID

/** IDs are never derived from the remaining directory, including after a restore. */
internal fun newLocalPersonId(): String = UUID.randomUUID().toString()

/** Used on the main thread: a late response must not publish over a newer request. */
internal class ScheduleLoadGate {
    private var generation = 0L
    fun begin(): Long = ++generation
    fun accepts(ticket: Long): Boolean = ticket == generation
    fun invalidate() { generation++ }
}

internal data class PosterSectionRow(val departments: List<String>, val height: Float)
internal fun posterSectionRows(departments: List<String>, roleCounts: Map<String, Int>): List<PosterSectionRow> =
    departments.chunked(3).map { ids ->
        val roles = ids.maxOfOrNull { roleCounts[it] ?: 0 } ?: 0
        // Last name baseline: 80 + (roles - 1) * 80 + 27. Keep 33 px below it.
        PosterSectionRow(ids, maxOf(380f, 140f + (roles - 1).coerceAtLeast(0) * 80f))
    }

internal interface BackupDocuments<T> {
    fun find(name: String): T?
    fun create(name: String): T
    fun write(document: T, bytes: ByteArray)
    fun read(document: T): ByteArray
    fun rename(document: T, name: String): T
    fun delete(document: T)
}

/** Never truncate the only usable backup. Providers without rename support fail safely. */
internal fun <T> replaceVerifiedBackup(documents: BackupDocuments<T>, name: String, bytes: ByteArray) {
    val suffix = UUID.randomUUID().toString()
    var staging = documents.create("$name.pending-$suffix")
    var promoted = false
    try {
        documents.write(staging, bytes)
        check(documents.read(staging).contentEquals(bytes)) { "Backup verification failed. The previous backup has been kept." }
        val previous = documents.find(name)?.let { documents.rename(it, "$name.previous-$suffix") }
        try {
            staging = documents.rename(staging, name)
            promoted = true
        } catch (error: Exception) {
            // If rollback is unsupported, the verified old file remains under .previous-*.
            if (previous != null) runCatching { documents.rename(previous, name) }
            throw IllegalStateException("Could not replace backup. The previous backup has been kept in the selected folder.", error)
        }
        if (previous != null) runCatching { documents.delete(previous) }
    } finally {
        if (!promoted) runCatching { documents.delete(staging) }
    }
}

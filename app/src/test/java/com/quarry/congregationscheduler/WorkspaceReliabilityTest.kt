package com.quarry.congregationscheduler

import org.junit.Assert.*
import org.junit.Test

class WorkspaceReliabilityTest {
    @Test fun olderMonthResponseCannotPublishAfterNewerRequestOrSignOut() {
        val gate = ScheduleLoadGate()
        val may = gate.begin()
        val june = gate.begin()
        assertFalse(gate.accepts(may))
        assertTrue(gate.accepts(june))
        gate.invalidate()
        assertFalse(gate.accepts(june))
    }

    @Test fun fifthAndFiftiethRolesFitInsideTheirCard() {
        for (count in listOf(0, 4, 5, 50)) {
            val row = posterSectionRows(listOf("mic"), mapOf("mic" to count)).single()
            val lastNameBaseline = 80 + (count - 1).coerceAtLeast(0) * 80 + 27
            assertTrue(row.height >= lastNameBaseline + 24)
        }
        val rows = posterSectionRows((1..7).map(Int::toString), mapOf("4" to 50))
        assertEquals(listOf(3, 3, 1), rows.map { it.departments.size })
        assertTrue(rows[1].height > rows[0].height)
    }

    private class MemoryDocuments : BackupDocuments<Int> {
        data class Entry(var name: String, var bytes: ByteArray)
        val files = mutableMapOf(1 to Entry("backup.bcdv", "old".toByteArray()))
        var nextId = 2
        var failure = ""
        override fun find(name: String) = files.entries.firstOrNull { it.value.name == name }?.key
        override fun create(name: String) = nextId++.also { files[it] = Entry(name, byteArrayOf()) }
        override fun write(document: Int, bytes: ByteArray) {
            files.getValue(document).bytes = bytes.copyOf()
            if (failure == "write") error("storage full")
        }
        override fun read(document: Int) = if (failure == "verify") byteArrayOf() else files.getValue(document).bytes
        override fun rename(document: Int, name: String): Int {
            if (failure == "rename" || (failure == "promote" && document != 1 && name == "backup.bcdv")) error("rename failed")
            files.getValue(document).name = name
            return document
        }
        override fun delete(document: Int) { if (failure == "cleanup") error("cleanup failed"); files.remove(document) }
    }

    @Test fun partialWriteBadReadAndRenameFailurePreservePreviousBackup() {
        for (failure in listOf("write", "verify", "rename", "promote")) {
            val documents = MemoryDocuments().apply { this.failure = failure }
            try { replaceVerifiedBackup(documents, "backup.bcdv", "new".toByteArray()); fail("Expected $failure failure") } catch (_: IllegalStateException) { }
            assertArrayEquals("old".toByteArray(), documents.files.getValue(1).bytes)
            assertEquals("backup.bcdv", documents.files.getValue(1).name)
        }
    }

    @Test fun successfulReplacementAndCleanupFailureKeepNewVerifiedBackup() {
        for (failure in listOf("", "cleanup")) {
            val documents = MemoryDocuments().apply { this.failure = failure }
            replaceVerifiedBackup(documents, "backup.bcdv", "new".toByteArray())
            val id = documents.find("backup.bcdv")!!
            assertArrayEquals("new".toByteArray(), documents.read(id))
        }
    }
}

package com.quarry.congregationscheduler

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class LocalIdentityTest {
    @Test fun deletedHighestIdAndRestoredHistoryCannotBeInheritedByNewPersonnel() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "identity-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("identity-$name-${directory.name}", mode)
        }
        try {
            val store = LocalWorkspaceStore(context)
            val old = store.savePerson(Person("1", "Old", 1, "active", false, true, false, false, false))
            store.review(TaskReview("2026-11-01", "meeting", "mic", "role", old.id, old.name, "completed"))
            store.savePhoto("person:${old.id}", byteArrayOf(1, 2, 3))
            store.deletePerson(old.id)
            val restored = store.export()
            store.import(restored)
            val new = store.savePerson(old.copy(id = "", name = "New"))
            assertNotEquals(old.id, new.id)
            assertFalse(store.allReviews().any { it.personId == new.id })
            assertNull(store.photo("person:${new.id}"))
            assertNull(store.photo("person:${old.id}"))
        } finally { directory.deleteRecursively() }
    }
}

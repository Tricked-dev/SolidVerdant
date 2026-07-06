package dev.tricked.solidverdant.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimeEntryDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: TimeEntryDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.timeEntryDao()
    }

    @After fun teardown() = db.close()

    private fun entry(id: String, org: String = "org1", end: String? = "2026-01-01T10:00:00Z") =
        TimeEntryEntity(
            id = id, description = "d", userId = "u", start = "2026-01-01T09:00:00Z",
            end = end, duration = 3600, taskId = null, projectId = null,
            billable = false, organizationId = org, updatedAt = 1L,
            syncState = SyncState.SYNCED, pendingDelete = false
        )

    @Test fun upsert_and_observe_visible_excludes_pending_delete() = runTest {
        dao.upsert(entry("a"))
        dao.upsert(entry("b").copy(pendingDelete = true))
        val visible = dao.observeVisibleEntries("org1").first()
        assertEquals(listOf("a"), visible.map { it.id })
    }

    @Test fun observe_active_returns_entry_with_null_end() = runTest {
        dao.upsert(entry("a", end = "2026-01-01T10:00:00Z"))
        dao.upsert(entry("b", end = null))
        assertEquals("b", dao.observeActive("org1").first()?.id)
    }

    @Test fun rekey_moves_row_to_new_id() = runTest {
        dao.upsert(entry("local-1"))
        dao.rekey("local-1", "server-1")
        assertNull(dao.getById("local-1"))
        assertEquals("server-1", dao.getById("server-1")?.id)
    }

    @Test fun replace_tag_refs_round_trips() = runTest {
        dao.upsert(entry("a"))
        dao.replaceTagRefs("a", listOf("t1", "t2"))
        assertEquals(setOf("t1", "t2"), dao.tagIdsFor("a").toSet())
        dao.replaceTagRefs("a", listOf("t3"))
        assertEquals(listOf("t3"), dao.tagIdsFor("a"))
    }
}

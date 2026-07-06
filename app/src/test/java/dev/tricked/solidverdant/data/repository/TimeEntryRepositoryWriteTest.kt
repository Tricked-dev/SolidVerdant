package dev.tricked.solidverdant.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.remote.FakeRemoteDataSource
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimeEntryRepositoryWriteTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TimeEntryRepository
    private val clock = object : Clock { override fun nowMs() = 1234L }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = TimeEntryRepository(
            db.timeEntryDao(), db.catalogDao(), db.outboxDao(), db.syncMetaDao(),
            FakeRemoteDataSource(), clock, Json { encodeDefaults = true }
        )
    }
    @After fun teardown() = db.close()

    @Test fun start_writes_optimistic_local_entry_and_enqueues_outbox() = runTest {
        val entry = repo.startEntry("org1", "member1", "u1", projectId = "p1", taskId = null, description = "work", tagIds = emptyList())
        assertTrue(entry.id.startsWith("local-"))
        assertEquals(entry.id, repo.observeActiveEntry("org1").first()?.id)
        val ops = db.outboxDao().peekAll()
        assertEquals(listOf(OutboxOpType.START), ops.map { it.opType })
        assertEquals(entry.id, ops.first().timeEntryId)
    }

    @Test fun delete_soft_deletes_locally_and_enqueues() = runTest {
        val e = repo.startEntry("org1", "m", "u", null, null, "x", emptyList())
        repo.deleteEntry(e)
        assertEquals(null, repo.observeActiveEntry("org1").first())
        assertTrue(db.outboxDao().peekAll().any { it.opType == OutboxOpType.DELETE })
    }
}

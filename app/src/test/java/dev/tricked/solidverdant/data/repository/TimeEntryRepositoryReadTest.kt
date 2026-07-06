package dev.tricked.solidverdant.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.SyncState
import dev.tricked.solidverdant.data.local.db.toEntity
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.remote.FakeRemoteDataSource
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimeEntryRepositoryReadTest {
    private lateinit var db: AppDatabase
    private lateinit var remote: FakeRemoteDataSource
    private lateinit var repo: TimeEntryRepository
    private val clock = object : Clock { var t = 1000L; override fun nowMs() = t }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        remote = FakeRemoteDataSource()
        repo = TimeEntryRepository(
            db.timeEntryDao(), db.catalogDao(), db.outboxDao(), db.syncMetaDao(),
            remote, clock, Json { encodeDefaults = true }
        )
    }
    @After fun teardown() = db.close()

    private fun srv(id: String) = TimeEntry(
        id = id, userId = "u", start = "2026-01-01T09:00:00Z",
        end = "2026-01-01T10:00:00Z", organizationId = "org1"
    )

    @Test fun refresh_upserts_remote_entries_into_room() = runTest {
        remote.entries = listOf(srv("a"), srv("b"))
        repo.refreshAll("org1", "member1")
        val observed = repo.observeTimeEntries("org1").first()
        assertEquals(setOf("a", "b"), observed.map { it.id }.toSet())
    }

    @Test fun refresh_does_not_clobber_newer_pending_local_edit() = runTest {
        // Local edit made at t=5000 (newer)
        db.timeEntryDao().upsert(srv("a").copy(description = "local").toEntity(updatedAt = 5000L, syncState = SyncState.PENDING))
        // Server version is older (t=1000)
        remote.entries = listOf(srv("a").copy(description = "server"))
        clock.t = 1000L
        repo.refreshAll("org1", "member1")
        assertEquals("local", repo.observeTimeEntries("org1").first().first { it.id == "a" }.description)
    }
}

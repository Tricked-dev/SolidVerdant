/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.OutboxOpType
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimeEntryRepositoryWriteTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TimeEntryRepository
    private val clock = object : Clock {
        override fun nowMs() = 1234L
    }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = TimeEntryRepository(
            db.timeEntryDao(),
            db.catalogDao(),
            db.outboxDao(),
            db.syncMetaDao(),
            FakeRemoteDataSource(),
            clock,
            Json { encodeDefaults = true },
        )
    }

    @After fun teardown() = db.close()

    @Test fun refreshAll_does_not_clobber_pending_local_edit() = runTest {
        val serverVersion = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "server text",
        )
        val fake = FakeRemoteDataSource(entries = listOf(serverVersion))
        val repo2 = TimeEntryRepository(
            db.timeEntryDao(),
            db.catalogDao(),
            db.outboxDao(),
            db.syncMetaDao(),
            fake,
            clock,
            Json { encodeDefaults = true },
        )
        // Local PENDING edit (an unsynced correction) for the same id.
        val localEdit = serverVersion.copy(description = "my local edit")
        db.timeEntryDao().upsert(localEdit.toEntity(updatedAt = 1L, syncState = SyncState.PENDING))

        repo2.refreshAll("org1", "member")

        val stored = db.timeEntryDao().getById("server-1")
        assertEquals(SyncState.PENDING, stored?.syncState)
        assertEquals("my local edit", stored?.description)
    }

    @Test fun refreshAll_does_not_resurrect_pending_delete() = runTest {
        val serverVersion = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "server text",
        )
        val fake = FakeRemoteDataSource(entries = listOf(serverVersion))
        val repo2 = TimeEntryRepository(
            db.timeEntryDao(),
            db.catalogDao(),
            db.outboxDao(),
            db.syncMetaDao(),
            fake,
            clock,
            Json { encodeDefaults = true },
        )
        // Soft-deleted row awaiting server delete must not be resurrected by a pull.
        db.timeEntryDao().upsert(
            serverVersion.toEntity(updatedAt = 1L, syncState = SyncState.PENDING, pendingDelete = true),
        )

        repo2.refreshAll("org1", "member")

        assertEquals(true, db.timeEntryDao().getById("server-1")?.pendingDelete)
    }

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

    @Test fun undo_delete_restores_entry_and_cancels_delete_operation() = runTest {
        val entry = repo.startEntry("org1", "m", "u", null, null, "x", emptyList())
        repo.deleteEntry(entry)

        assertTrue(repo.undoDelete(entry, "m"))
        assertEquals(entry.id, repo.observeActiveEntry("org1").first()?.id)
        assertTrue(db.outboxDao().peekAll().none { it.opType == OutboxOpType.DELETE })
    }

    @Test fun undo_after_server_delete_enqueues_offline_recreation() = runTest {
        val entry = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "work",
        )
        db.timeEntryDao().upsert(entry.toEntity(1L, SyncState.SYNCED))
        repo.deleteEntry(entry)
        db.outboxDao().peekAll().forEach { db.outboxDao().delete(it) }
        db.timeEntryDao().deleteById(entry.id)

        assertTrue(repo.undoDelete(entry, "member"))
        val recreated = repo.observeTimeEntries("org1").first().single()
        assertTrue(recreated.id.startsWith("local-restore-"))
        assertEquals(OutboxOpType.CREATE, db.outboxDao().peekAll().single().opType)
    }
}

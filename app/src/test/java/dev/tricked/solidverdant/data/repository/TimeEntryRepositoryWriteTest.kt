/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.OutboxEntity
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.local.db.SyncState
import dev.tricked.solidverdant.data.local.db.toEntity
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.remote.FakeRemoteDataSource
import dev.tricked.solidverdant.sync.ConflictSnapshot
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private val testJson = Json { encodeDefaults = true }

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
            db,
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
            db,
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
            db,
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

    @Test fun delete_of_never_synced_entry_cancels_create_and_enqueues_no_delete() = runTest {
        // SV-008: deleting an entry that never reached the server (local- id) must cancel its queued
        // START/CREATE and enqueue NO server DELETE - otherwise the START uploads first on the next
        // drain and resurrects the "deleted" entry on the server.
        val e = repo.startEntry("org1", "m", "u", null, null, "x", emptyList())
        repo.deleteEntry(e)
        assertEquals(null, repo.observeActiveEntry("org1").first())
        val ops = db.outboxDao().peekAll()
        assertTrue(ops.none { it.opType == OutboxOpType.DELETE })
        assertTrue(ops.none { it.opType == OutboxOpType.START })
    }

    @Test fun delete_of_synced_entry_enqueues_server_delete() = runTest {
        val entry = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "x",
        )
        db.timeEntryDao().upsert(entry.toEntity(1L, SyncState.SYNCED))
        repo.deleteEntry(entry)
        assertTrue(db.outboxDao().peekAll().any { it.opType == OutboxOpType.DELETE })
    }

    @Test fun undo_within_window_restores_entry_and_never_enqueues_delete() = runTest {
        // SV-019: within the undo window a delete is only a local soft-delete (no outbox op), so undo
        // is a pure local restore and no DELETE can race the SyncWorker to the server.
        val entry = repo.startEntry("org1", "m", "u", null, null, "x", emptyList())
        repo.softDeleteLocal(entry)

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

    @Test fun refreshAll_tombstones_server_deleted_entry_within_range() = runTest {
        // SV-020: a SYNCED row with no pending outbox op, whose server counterpart is gone from the
        // fetched page, must be deleted locally once its start falls inside the observed min/max range.
        val serverOne = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "will be deleted on server",
        )
        db.timeEntryDao().upsert(serverOne.toEntity(updatedAt = 1L, syncState = SyncState.SYNCED))

        // The fetched page brackets server-1's start with an earlier and a later entry, so the
        // min/max range computed from `entries` covers server-1's start even though it's absent
        // from the page itself.
        val earlier = TimeEntry(
            id = "server-earlier",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-01T00:00:00Z",
            end = "2026-07-01T01:00:00Z",
            description = "earlier",
        )
        val later = TimeEntry(
            id = "server-later",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-14T00:00:00Z",
            end = "2026-07-14T01:00:00Z",
            description = "later",
        )
        val fake = FakeRemoteDataSource(entries = listOf(earlier, later))
        val repo2 = TimeEntryRepository(
            db.timeEntryDao(),
            db.catalogDao(),
            db.outboxDao(),
            db.syncMetaDao(),
            fake,
            clock,
            Json { encodeDefaults = true },
            db,
        )

        repo2.refreshAll("org1", "member")

        assertEquals(null, db.timeEntryDao().getById("server-1"))
    }

    @Test fun refreshAll_does_not_tombstone_pending_local_row_within_range() = runTest {
        // SV-020: a PENDING (unsynced) row must survive tombstoning even though it's missing from
        // the server page and its start falls within the observed range.
        val localPending = TimeEntry(
            id = "local-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "unsynced local entry",
        )
        db.timeEntryDao().upsert(localPending.toEntity(updatedAt = 1L, syncState = SyncState.PENDING))

        val earlier = TimeEntry(
            id = "server-earlier",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-01T00:00:00Z",
            end = "2026-07-01T01:00:00Z",
            description = "earlier",
        )
        val later = TimeEntry(
            id = "server-later",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-14T00:00:00Z",
            end = "2026-07-14T01:00:00Z",
            description = "later",
        )
        val fake = FakeRemoteDataSource(entries = listOf(earlier, later))
        val repo2 = TimeEntryRepository(
            db.timeEntryDao(),
            db.catalogDao(),
            db.outboxDao(),
            db.syncMetaDao(),
            fake,
            clock,
            Json { encodeDefaults = true },
            db,
        )

        repo2.refreshAll("org1", "member")

        assertEquals("local-1", db.timeEntryDao().getById("local-1")?.id)
        assertEquals(SyncState.PENDING, db.timeEntryDao().getById("local-1")?.syncState)
    }

    @Test fun refreshAll_does_not_tombstone_synced_row_outside_range() = runTest {
        // SV-020: a SYNCED row whose start falls outside the fetched page's observed [min,max]
        // range must not be touched by tombstoning, even though it's absent from the page.
        val outOfRange = TimeEntry(
            id = "server-out-of-range",
            userId = "u",
            organizationId = "org1",
            start = "2026-08-20T00:00:00Z",
            end = "2026-08-20T01:00:00Z",
            description = "far in the future, outside fetched range",
        )
        db.timeEntryDao().upsert(outOfRange.toEntity(updatedAt = 1L, syncState = SyncState.SYNCED))

        val earlier = TimeEntry(
            id = "server-earlier",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-01T00:00:00Z",
            end = "2026-07-01T01:00:00Z",
            description = "earlier",
        )
        val later = TimeEntry(
            id = "server-later",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-14T00:00:00Z",
            end = "2026-07-14T01:00:00Z",
            description = "later",
        )
        val fake = FakeRemoteDataSource(entries = listOf(earlier, later))
        val repo2 = TimeEntryRepository(
            db.timeEntryDao(),
            db.catalogDao(),
            db.outboxDao(),
            db.syncMetaDao(),
            fake,
            clock,
            Json { encodeDefaults = true },
            db,
        )

        repo2.refreshAll("org1", "member")

        assertEquals("server-out-of-range", db.timeEntryDao().getById("server-out-of-range")?.id)
    }

    @Test fun undoDelete_of_synced_entry_restores_to_synced() = runTest {
        // SV-024: undoing a delete of a server-known (synced) entry within the soft-delete window
        // must restore it to SyncState.SYNCED, not PENDING, since no local edit occurred.
        val entry = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "x",
        )
        db.timeEntryDao().upsert(entry.toEntity(1L, SyncState.SYNCED))

        repo.deleteEntry(entry)
        assertTrue(repo.undoDelete(entry, "m"))

        val restored = db.timeEntryDao().getById("server-1")
        assertEquals(SyncState.SYNCED, restored?.syncState)
        assertEquals(false, restored?.pendingDelete)
        assertTrue(db.outboxDao().peekAll().none { it.opType == OutboxOpType.DELETE })
    }

    @Test fun discardFailedSync_removes_dead_lettered_ops_for_entry() = runTest {
        // SV-029: discardFailedSync should delete only the dead-lettered outbox op(s) for the given
        // entry, and report success via its Boolean return value.
        val entryId = "server-1"
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = "org1",
                timeEntryId = entryId,
                payloadJson = "{}",
                createdAtMs = 1L,
                attemptCount = 5,
                lastError = "boom",
                deadLettered = true,
            ),
        )

        assertTrue(repo.discardFailedSync(entryId))
        assertTrue(db.outboxDao().peekAll().none { it.timeEntryId == entryId })
    }

    @Test fun update_on_synced_entry_captures_pre_mutation_base() = runTest {
        // SV-027 rule 3: no queued op yet, no queued START/CREATE -> base = pre-mutation content.
        val entry = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "old text",
        )
        db.timeEntryDao().upsert(entry.toEntity(1L, SyncState.SYNCED))

        repo.updateEntry(entry.copy(description = "new text"), tagIds = emptyList())

        val op = db.outboxDao().peekAll().single { it.opType == OutboxOpType.UPDATE }
        val base = Json.decodeFromString<ConflictSnapshot>(op.baseSnapshotJson!!)
        assertEquals("old text", base.description)
    }

    @Test fun stop_captures_pre_stop_base() = runTest {
        // SV-027 rule 3: base for a STOP op must reflect the entry before `end` was written.
        val entry = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = null,
            description = "running",
        )
        db.timeEntryDao().upsert(entry.toEntity(1L, SyncState.SYNCED))

        repo.stopEntry(entry, "u")

        val op = db.outboxDao().peekAll().single { it.opType == OutboxOpType.STOP }
        val base = Json.decodeFromString<ConflictSnapshot>(op.baseSnapshotJson!!)
        assertNull(base.endMs)
    }

    @Test fun delete_captures_base_despite_pending_flag() = runTest {
        // SV-027 rule 3: softDeleteLocal flips syncState to PENDING before commitDelete enqueues the
        // DELETE op, but the base must still be the untouched (pre-delete) server-acked content.
        val entry = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "untouched content",
        )
        db.timeEntryDao().upsert(entry.toEntity(1L, SyncState.SYNCED))

        repo.softDeleteLocal(entry)
        repo.commitDelete(entry)

        val op = db.outboxDao().peekAll().single { it.opType == OutboxOpType.DELETE }
        val base = Json.decodeFromString<ConflictSnapshot>(op.baseSnapshotJson!!)
        assertEquals("untouched content", base.description)
    }

    @Test fun chained_ops_reuse_oldest_base() = runTest {
        // SV-027 rule 1: an offline STOP -> UPDATE chain must share the pre-stop base, not
        // re-snapshot the just-written (post-stop) content.
        val entry = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = null,
            description = "pre-stop text",
        )
        db.timeEntryDao().upsert(entry.toEntity(1L, SyncState.SYNCED))

        repo.stopEntry(entry, "u")
        val stopped = entry.copy(end = "2026-07-07T09:00:00Z")
        repo.updateEntry(stopped.copy(description = "edited offline"), tagIds = emptyList())

        val ops = db.outboxDao().peekAll()
        val stopBase = Json.decodeFromString<ConflictSnapshot>(ops.single { it.opType == OutboxOpType.STOP }.baseSnapshotJson!!)
        val updateBase = Json.decodeFromString<ConflictSnapshot>(ops.single { it.opType == OutboxOpType.UPDATE }.baseSnapshotJson!!)
        assertEquals(stopBase, updateBase)
        assertEquals("pre-stop text", updateBase.description)
        assertNull(updateBase.endMs)
    }

    @Test fun ops_on_locally_created_entry_have_null_base() = runTest {
        // SV-027 rule 2: a queued START/CREATE for the entry means it was born locally - nothing on
        // the server to diverge from, so subsequent ops get a null base.
        val entry = repo.startEntry("org1", "m", "u", null, null, "x", emptyList())

        repo.updateEntry(entry.copy(description = "edited"), tagIds = emptyList())

        val updateOp = db.outboxDao().peekAll().single { it.opType == OutboxOpType.UPDATE }
        assertNull(updateOp.baseSnapshotJson)
    }

    @Test fun refresh_marks_diverged_pending_edit_as_conflict_and_drops_outbox() = runTest {
        val base = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "before",
        )
        val server = base.copy(description = "changed on web")
        val fake = FakeRemoteDataSource(entries = listOf(server))
        val repo2 =
            TimeEntryRepository(
                db.timeEntryDao(),
                db.catalogDao(),
                db.outboxDao(),
                db.syncMetaDao(),
                fake,
                clock,
                Json {
                    encodeDefaults =
                        true
                },
                db,
            )
        db.timeEntryDao().upsert(base.copy(description = "mine").toEntity(2L, SyncState.PENDING))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = "org1",
                timeEntryId = base.id,
                createdAtMs = 2L,
                payloadJson = "{}",
                baseSnapshotJson = testJson.encodeToString(
                    ConflictSnapshot.of(base.start, base.end, base.description, base.projectId, base.taskId, base.billable, emptyList()),
                ),
            ),
        )

        repo2.refreshAll("org1", "member")

        val stored = db.timeEntryDao().getById(base.id)
        assertEquals(SyncState.CONFLICT, stored?.syncState)
        assertEquals("mine", stored?.description)
        assertTrue(db.outboxDao().peekAll().isEmpty())
        assertTrue(stored?.conflictServerJson?.contains("changed on web") == true)
    }

    @Test fun refresh_keeps_pending_edit_when_server_still_matches_base() = runTest {
        val server = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "before",
        )
        val fake = FakeRemoteDataSource(entries = listOf(server))
        val repo2 =
            TimeEntryRepository(
                db.timeEntryDao(),
                db.catalogDao(),
                db.outboxDao(),
                db.syncMetaDao(),
                fake,
                clock,
                Json {
                    encodeDefaults =
                        true
                },
                db,
            )
        db.timeEntryDao().upsert(server.copy(description = "mine").toEntity(2L, SyncState.PENDING))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = "org1",
                timeEntryId = server.id,
                createdAtMs = 2L,
                payloadJson = "{}",
                baseSnapshotJson = testJson.encodeToString(
                    ConflictSnapshot.of(
                        server.start,
                        server.end,
                        server.description,
                        server.projectId,
                        server.taskId,
                        server.billable,
                        emptyList(),
                    ),
                ),
            ),
        )

        repo2.refreshAll("org1", "member")

        assertEquals(SyncState.PENDING, db.timeEntryDao().getById(server.id)?.syncState)
        assertEquals("mine", db.timeEntryDao().getById(server.id)?.description)
        assertEquals(1, db.outboxDao().peekAll().size)
    }

    @Test fun keep_mine_requeues_update_against_conflicting_server_copy() = runTest {
        val server = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "server",
        )
        val local = server.copy(description = "mine", tags = listOf(Tag("tag-1")))
        val json = Json { encodeDefaults = true }
        db.timeEntryDao().upsert(
            local.toEntity(2L, SyncState.CONFLICT).copy(conflictServerJson = json.encodeToString(server)),
        )
        db.timeEntryDao().replaceTagRefs(local.id, listOf("tag-1"))

        assertTrue(repo.resolveKeepMine(local.id, "member"))

        val stored = db.timeEntryDao().getById(local.id)
        val op = db.outboxDao().peekAll().single()
        assertEquals(SyncState.PENDING, stored?.syncState)
        assertNull(stored?.conflictServerJson)
        assertEquals(OutboxOpType.UPDATE, op.opType)
        assertEquals("server", Json.decodeFromString<ConflictSnapshot>(op.baseSnapshotJson!!).description)
    }

    @Test fun keep_theirs_restores_server_copy_and_clears_conflict() = runTest {
        val local = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "mine",
        )
        val server = local.copy(description = "server")
        val json = Json { encodeDefaults = true }
        db.timeEntryDao().upsert(local.toEntity(2L, SyncState.CONFLICT).copy(conflictServerJson = json.encodeToString(server)))

        assertTrue(repo.resolveKeepTheirs(local.id))

        val stored = db.timeEntryDao().getById(local.id)
        assertEquals(SyncState.SYNCED, stored?.syncState)
        assertEquals("server", stored?.description)
        assertNull(stored?.conflictServerJson)
        assertTrue(db.outboxDao().peekAll().isEmpty())
    }

    @Test fun conflicted_entry_can_stop_but_other_mutations_are_locked() = runTest {
        val entry = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = null,
            description = "mine",
        )
        db.timeEntryDao().upsert(
            entry.toEntity(2L, SyncState.CONFLICT).copy(conflictServerJson = ConflictSnapshot.DELETED_MARKER),
        )

        repo.stopEntry(entry, "u")

        assertEquals(SyncState.CONFLICT, db.timeEntryDao().getById(entry.id)?.syncState)
        assertTrue(db.timeEntryDao().getById(entry.id)?.end != null)
        assertTrue(db.outboxDao().peekAll().isEmpty())
        runCatching { repo.updateEntry(entry.copy(description = "blocked"), emptyList()) }
            .onSuccess { error("Expected conflicted edit to be rejected") }
            .onFailure { assertTrue(it is IllegalStateException) }
    }

    @Test fun keep_mine_recreates_an_entry_deleted_on_server() = runTest {
        val entry = TimeEntry(
            id = "server-1",
            userId = "u",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "mine",
        )
        db.timeEntryDao().upsert(
            entry.toEntity(2L, SyncState.CONFLICT).copy(conflictServerJson = ConflictSnapshot.DELETED_MARKER),
        )

        assertTrue(repo.resolveKeepMine(entry.id, "member"))

        val stored = db.timeEntryDao().getById(entry.id)
        val op = db.outboxDao().peekAll().single()
        assertEquals(SyncState.PENDING, stored?.syncState)
        assertEquals(OutboxOpType.CREATE, op.opType)
        assertEquals(entry.id, op.timeEntryId)
        assertNull(op.baseSnapshotJson)
    }
}

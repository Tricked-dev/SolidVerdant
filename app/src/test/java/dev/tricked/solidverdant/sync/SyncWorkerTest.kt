/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.OutboxEntity
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.local.db.SyncState
import dev.tricked.solidverdant.data.local.db.toEntity
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Organization
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.remote.FakeRemoteDataSource
import dev.tricked.solidverdant.util.Clock
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
class SyncWorkerTest {
    private lateinit var db: AppDatabase
    private lateinit var remote: FakeRemoteDataSource
    private val json = Json { encodeDefaults = true }
    private val clock = object : Clock {
        override fun nowMs() = 1L
    }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        remote = FakeRemoteDataSource()
    }

    @After fun teardown() = db.close()

    private fun buildWorker(status: SyncStatusReporter = SyncStatusReporter()) =
        TestListenableWorkerBuilder<SyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: android.content.Context,
                    workerClassName: String,
                    params: androidx.work.WorkerParameters,
                ) = SyncWorker(appContext, params, db.outboxDao(), db.timeEntryDao(), db, remote, json, clock, status)
            }).build()

    @Test fun start_op_reconciles_temp_id_to_server_id() = runTest {
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.START,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 1L,
                payloadJson = json.encodeToString(
                    StartPayload("m1", "u1", "p1", null, "work", emptyList()),
                ),
            ),
        )
        remote.startResult = { it.copy(id = "server-1") }

        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(db.outboxDao().peekAll().isEmpty())
        // temp id row was rekeyed
        assertNull(db.timeEntryDao().getById("local-1"))
    }

    @Test fun transient_failure_returns_retry_and_keeps_op() = runTest {
        remote.failNextWrite = true
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.DELETE,
                organizationId = "org1",
                timeEntryId = "server-1",
                createdAtMs = 1L,
                payloadJson = "{}",
            ),
        )
        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(1, db.outboxDao().peekAll().size)
    }

    @Test fun rejected_op_is_dead_lettered_and_not_reattempted() = runTest {
        remote.writeError = IllegalStateException("rejected") // non-IOException -> FAIL
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.DELETE,
                organizationId = "org1",
                timeEntryId = "server-1",
                createdAtMs = 1L,
                payloadJson = "{}",
            ),
        )

        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        // The op remains (visible for user retry) but is dead-lettered and no longer drained.
        val stored = db.outboxDao().peekAll().single()
        assertTrue(stored.deadLettered)
        assertTrue(db.outboxDao().peekPending().isEmpty())

        // A subsequent run must not touch the server again.
        remote.writeError = null
        buildWorker().doWork()
        assertTrue(remote.deleted.isEmpty())
    }

    @Test fun transient_failures_are_dead_lettered_after_attempt_cap() = runTest {
        remote.failNextWrite = true // IOException -> RETRY
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.STOP,
                organizationId = "org1",
                timeEntryId = "server-1",
                createdAtMs = 1L,
                attemptCount = SyncWorker.MAX_ATTEMPTS - 1,
                payloadJson = json.encodeToString(StopPayload("u1", "2026-07-07T08:00:00Z")),
            ),
        )

        val result = buildWorker().doWork()
        // Cap reached: dead-lettered instead of endless retry, worker completes successfully.
        assertEquals(ListenableWorker.Result.success(), result)
        val stored = db.outboxDao().peekAll().single()
        assertTrue(stored.deadLettered)
        assertTrue(db.outboxDao().peekPending().isEmpty())
    }

    @Test fun failed_create_cascades_dead_letter_to_dependent_ops() = runTest {
        remote.writeError = IllegalStateException("rejected")
        // START creates the entry; STOP depends on the not-yet-rekeyed local id.
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.START,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 1L,
                payloadJson = json.encodeToString(
                    StartPayload("m1", "u1", null, null, "work", emptyList()),
                ),
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.STOP,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 2L,
                payloadJson = json.encodeToString(StopPayload("u1", "s")),
            ),
        )

        buildWorker().doWork()
        // Both the failed create and its dependent are dead-lettered; none re-attempted.
        assertTrue(db.outboxDao().peekPending().isEmpty())
        assertTrue(db.outboxDao().peekAll().all { it.deadLettered })
        assertEquals(2, db.outboxDao().peekAll().size)
    }

    @Test fun start_retry_adopts_existing_active_entry_without_duplicate() = runTest {
        // A prior attempt already created the entry on the server (attemptCount > 0).
        remote.active = TimeEntry(
            id = "server-9",
            userId = "u1",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = null,
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.START,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 1L,
                attemptCount = 1,
                payloadJson = json.encodeToString(StartPayload("m1", "u1", null, null, "work", emptyList())),
            ),
        )

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
        // No duplicate start POST; op cleared and reconciled to the server entry.
        assertTrue(remote.started.isEmpty())
        assertTrue(db.outboxDao().peekAll().isEmpty())
        assertNull(db.timeEntryDao().getById("local-1"))
        assertEquals(SyncState.SYNCED, db.timeEntryDao().getById("server-9")?.syncState)
    }

    @Test fun stop_success_persists_authoritative_server_entry_as_synced() = runTest {
        val local = TimeEntry(
            id = "server-1",
            userId = "u1",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
        )
        db.timeEntryDao().upsert(local.toEntity(updatedAt = 1L, syncState = SyncState.PENDING))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.STOP,
                organizationId = "org1",
                timeEntryId = local.id,
                createdAtMs = 1L,
                payloadJson = json.encodeToString(StopPayload("u1", local.start)),
            ),
        )
        remote.stopResult = { local.copy(duration = 3600) }

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
        val stored = db.timeEntryDao().getById(local.id)
        assertEquals(SyncState.SYNCED, stored?.syncState)
        assertEquals(3600, stored?.duration)
    }

    @Test fun update_conflict_preserves_mine_and_does_not_write_server() = runTest {
        val base = TimeEntry(
            id = "server-1",
            userId = "u1",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "before",
        )
        val server = base.copy(description = "web edit")
        remote.entries = listOf(server)
        remote.memberships = listOf(Membership("m1", "member", Organization("org1", "Org", "USD")))
        val local = base.copy(description = "mine")
        db.timeEntryDao().upsert(local.toEntity(2L, SyncState.PENDING))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = "org1",
                timeEntryId = local.id,
                createdAtMs = 2L,
                payloadJson = json.encodeToString(
                    UpdatePayload(
                        "u1",
                        local.start,
                        local.end,
                        local.description,
                        local.projectId,
                        local.taskId,
                        local.billable,
                        emptyList(),
                    ),
                ),
                baseSnapshotJson = json.encodeToString(
                    ConflictSnapshot.of(
                        base.start,
                        base.end,
                        base.description,
                        base.projectId,
                        base.taskId,
                        base.billable,
                        emptyList(),
                    ),
                ),
            ),
        )

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())

        assertEquals(SyncState.CONFLICT, db.timeEntryDao().getById(local.id)?.syncState)
        assertEquals("mine", db.timeEntryDao().getById(local.id)?.description)
        assertTrue(db.outboxDao().peekAll().isEmpty())
    }

    @Test fun update_pushes_when_server_still_matches_base() = runTest {
        val base = TimeEntry(
            id = "server-1",
            userId = "u1",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "before",
        )
        remote.entries = listOf(base)
        remote.memberships = listOf(Membership("m1", "member", Organization("org1", "Org", "USD")))
        remote.updateResult = { it.copy(description = "server ack") }
        val local = base.copy(description = "mine")
        db.timeEntryDao().upsert(local.toEntity(2L, SyncState.PENDING))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = "org1",
                timeEntryId = local.id,
                createdAtMs = 2L,
                payloadJson = json.encodeToString(
                    UpdatePayload(
                        "u1",
                        local.start,
                        local.end,
                        local.description,
                        local.projectId,
                        local.taskId,
                        local.billable,
                        emptyList(),
                    ),
                ),
                baseSnapshotJson = json.encodeToString(
                    ConflictSnapshot.of(
                        base.start,
                        base.end,
                        base.description,
                        base.projectId,
                        base.taskId,
                        base.billable,
                        emptyList(),
                    ),
                ),
            ),
        )

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())

        assertEquals(SyncState.SYNCED, db.timeEntryDao().getById(local.id)?.syncState)
        assertEquals("server ack", db.timeEntryDao().getById(local.id)?.description)
        assertTrue(db.outboxDao().peekAll().isEmpty())
    }

    @Test fun absent_server_entry_becomes_deleted_conflict() = runTest {
        val local = TimeEntry(
            id = "server-1",
            userId = "u1",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "mine",
        )
        remote.entries = emptyList()
        remote.memberships = listOf(Membership("m1", "member", Organization("org1", "Org", "USD")))
        db.timeEntryDao().upsert(local.toEntity(2L, SyncState.PENDING))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.DELETE,
                organizationId = "org1",
                timeEntryId = local.id,
                createdAtMs = 2L,
                payloadJson = "{}",
                baseSnapshotJson = json.encodeToString(
                    ConflictSnapshot.of(
                        local.start,
                        local.end,
                        local.description,
                        local.projectId,
                        local.taskId,
                        local.billable,
                        emptyList(),
                    ),
                ),
            ),
        )

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())

        assertEquals(SyncState.CONFLICT, db.timeEntryDao().getById(local.id)?.syncState)
        assertEquals(ConflictSnapshot.DELETED_MARKER, db.timeEntryDao().getById(local.id)?.conflictServerJson)
        assertTrue(db.outboxDao().peekAll().isEmpty())
    }

    // SV-017: an offline START must transmit the timestamp captured when the user actually
    // pressed start, not a value recomputed at sync time (which would be whenever connectivity
    // happened to return).
    @Test fun offline_start_sends_captured_start_time_not_sync_time() = runTest {
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.START,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 1L,
                payloadJson = json.encodeToString(
                    StartPayload("m1", "u1", null, null, "work", emptyList(), start = "2020-01-01T09:00:00Z"),
                ),
            ),
        )

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
        assertEquals("2020-01-01T09:00:00Z", remote.lastStartTime)
    }

    // SV-017: an offline STOP must transmit the capture-time end timestamp, not a sync-time value.
    @Test fun offline_stop_sends_captured_end_time_not_sync_time() = runTest {
        val local = TimeEntry(
            id = "server-1",
            userId = "u1",
            organizationId = "org1",
            start = "2020-01-01T09:00:00Z",
            end = "2020-01-01T10:00:00Z",
        )
        db.timeEntryDao().upsert(local.toEntity(updatedAt = 1L, syncState = SyncState.PENDING))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.STOP,
                organizationId = "org1",
                timeEntryId = local.id,
                createdAtMs = 1L,
                payloadJson = json.encodeToString(
                    StopPayload("u1", local.start, end = "2020-01-01T10:00:00Z"),
                ),
            ),
        )

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
        assertEquals("2020-01-01T10:00:00Z", remote.lastEndTime)
    }

    // SV-018: a START and its dependent STOP for the same local- id, drained in the same run, must
    // have the STOP rekeyed to the server id the START reconciled to - not 404 (or dead-letter) on
    // the retired local- id. Mirrors failed_create_cascades_dead_letter_to_dependent_ops but for the
    // success path: both ops must clear from the outbox and the STOP must actually reach the server.
    @Test fun start_then_stop_in_one_drain_rekeys_stop_to_server_id() = runTest {
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.START,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 1L,
                payloadJson = json.encodeToString(
                    StartPayload("m1", "u1", null, null, "work", emptyList(), start = "2020-01-01T09:00:00Z"),
                ),
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.STOP,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 2L,
                payloadJson = json.encodeToString(
                    StopPayload("u1", "2020-01-01T09:00:00Z", end = "2020-01-01T10:00:00Z"),
                ),
            ),
        )
        remote.startResult = { it.copy(id = "server-42") }

        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        // Both ops drained cleanly - no dead-letter cascade like the failure-path test asserts.
        assertTrue(db.outboxDao().peekAll().isEmpty())
        // The STOP actually reached the server (for the rekeyed id), proving it did not 404 on the
        // retired local- id.
        assertEquals("2020-01-01T10:00:00Z", remote.lastEndTime)
        assertNull(db.timeEntryDao().getById("local-1"))
        assertEquals(SyncState.SYNCED, db.timeEntryDao().getById("server-42")?.syncState)
    }

    // SV-025: a dead-lettered UPDATE that is revived (deadLettered reset for retry) but has since
    // been superseded by a newer synced state for the same entry must be dropped as Outcome.Superseded
    // rather than replayed over the newer data.
    @Test fun superseded_revived_update_is_dropped_without_server_call() = runTest {
        val newer = TimeEntry(
            id = "server-1",
            userId = "u1",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "newer",
        )
        // The entry already reflects newer, already-synced state (updatedAt is after the stale
        // op's createdAtMs below), so countNewerPending/newerSynced must classify the revived
        // UPDATE as superseded.
        db.timeEntryDao().upsert(newer.toEntity(updatedAt = 100L, syncState = SyncState.SYNCED))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = "org1",
                timeEntryId = "server-1",
                createdAtMs = 1L, // older than the entry's updatedAt = 100L above
                // Already revived: deadLettered reset to false by resetForRetry, so it re-enters
                // peekPending() and must be re-evaluated for staleness.
                deadLettered = false,
                payloadJson = json.encodeToString(
                    UpdatePayload("u1", "2026-07-07T08:00:00Z", "2026-07-07T09:00:00Z", "stale", null, null, true, emptyList()),
                ),
            ),
        )

        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        // Dropped outright: no server call was made to overwrite the newer state.
        assertTrue(db.outboxDao().peekAll().isEmpty())
        val stored = db.timeEntryDao().getById("server-1")
        assertEquals("newer", stored?.description)
        assertEquals(SyncState.SYNCED, stored?.syncState)
    }

    // SV-023: START duplicate-adoption runs unconditionally, not only when a prior attempt already
    // bumped attemptCount. A brand-new (attemptCount = 0) START op must still adopt a matching
    // already-active server entry instead of re-POSTing a duplicate start.
    @Test fun fresh_start_adopts_existing_active_entry_unconditionally() = runTest {
        remote.active = TimeEntry(
            id = "server-9",
            userId = "u1",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = null,
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.START,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 1L,
                attemptCount = 0, // first attempt, not a retry - adoption must still run
                payloadJson = json.encodeToString(StartPayload("m1", "u1", null, null, "work", emptyList())),
            ),
        )

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
        // No duplicate start POST; op cleared and reconciled to the already-active server entry.
        assertTrue(remote.started.isEmpty())
        assertTrue(db.outboxDao().peekAll().isEmpty())
        assertNull(db.timeEntryDao().getById("local-1"))
        assertEquals(SyncState.SYNCED, db.timeEntryDao().getById("server-9")?.syncState)
    }
}

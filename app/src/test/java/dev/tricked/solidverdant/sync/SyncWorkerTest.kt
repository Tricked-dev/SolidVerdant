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
import dev.tricked.solidverdant.data.remote.RemoteDataSource
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.util.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Suppress("LargeClass")
class SyncWorkerTest {
    private lateinit var db: AppDatabase
    private lateinit var remote: FakeRemoteDataSource
    private val json = Json { encodeDefaults = true }
    private var nowMs = 1L
    private val clock = object : Clock {
        override fun nowMs() = nowMs
    }

    @Before fun setup() {
        nowMs = 1L
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        remote = FakeRemoteDataSource()
    }

    @After fun teardown() = db.close()

    private fun buildWorker(status: SyncStatusReporter = SyncStatusReporter(), remoteDataSource: RemoteDataSource = remote) =
        TestListenableWorkerBuilder<SyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: android.content.Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters,
                ) = SyncWorker(
                    appContext,
                    workerParameters,
                    db.outboxDao(),
                    db.timeEntryDao(),
                    db.syncMetaDao(),
                    db,
                    remoteDataSource,
                    json,
                    clock,
                    status,
                )
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

    @Test fun intermittent_active_lookup_failure_retries_without_posting_a_duplicate_start() = runTest {
        val server = TimeEntry(
            id = "server-1",
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
                payloadJson = json.encodeToString(
                    StartPayload("m1", "u1", null, null, "work", emptyList(), start = server.start),
                ),
            ),
        )
        val syncRemote = mockk<RemoteDataSource>()
        coEvery { syncRemote.getActiveTimeEntry() } returnsMany listOf(
            Result.failure(IOException("network unavailable")),
            Result.success(null),
        )
        coEvery {
            syncRemote.startTimeEntry(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(server)

        assertEquals(ListenableWorker.Result.retry(), buildWorker(remoteDataSource = syncRemote).doWork())
        assertEquals(1, db.outboxDao().peekAll().single().attemptCount)
        coVerify(exactly = 0) {
            syncRemote.startTimeEntry(any(), any(), any(), any(), any(), any(), any())
        }

        assertEquals(ListenableWorker.Result.success(), buildWorker(remoteDataSource = syncRemote).doWork())
        coVerify(exactly = 1) {
            syncRemote.startTimeEntry(any(), any(), any(), any(), any(), any(), any())
        }
        assertTrue(db.outboxDao().peekAll().isEmpty())
    }

    @Test fun cancellation_during_active_lookup_is_not_converted_to_a_retry_result() = runTest {
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
        val lookupGate = CompletableDeferred<Result<TimeEntry?>>()
        val syncRemote = mockk<RemoteDataSource>()
        coEvery { syncRemote.getActiveTimeEntry() } coAnswers { lookupGate.await() }

        val failure = runCatching {
            withTimeout(100L) { buildWorker(remoteDataSource = syncRemote).doWork() }
        }.exceptionOrNull()
        assertTrue(failure is TimeoutCancellationException)
        assertEquals(0, db.outboxDao().peekAll().single().attemptCount)
    }

    @Test fun start_does_not_adopt_an_active_entry_from_another_organization() = runTest {
        val activeInOtherOrganization = TimeEntry(
            id = "server-other",
            userId = "u1",
            organizationId = "org2",
            start = "2026-07-07T08:00:00Z",
            end = null,
        )
        val startedInCurrentOrganization = activeInOtherOrganization.copy(
            id = "server-current",
            organizationId = "org1",
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.START,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 1L,
                payloadJson = json.encodeToString(
                    StartPayload("m1", "u1", null, null, "work", emptyList(), start = activeInOtherOrganization.start),
                ),
            ),
        )
        val syncRemote = mockk<RemoteDataSource>()
        coEvery { syncRemote.getActiveTimeEntry() } returns Result.success(activeInOtherOrganization)
        coEvery {
            syncRemote.startTimeEntry(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(startedInCurrentOrganization)

        assertEquals(ListenableWorker.Result.success(), buildWorker(remoteDataSource = syncRemote).doWork())
        coVerify(exactly = 1) {
            syncRemote.startTimeEntry("org1", "m1", "u1", null, null, "work", activeInOtherOrganization.start)
        }
        assertEquals(SyncState.SYNCED, db.timeEntryDao().getById(startedInCurrentOrganization.id)?.syncState)
        assertNull(db.timeEntryDao().getById(activeInOtherOrganization.id))
    }

    @Test fun concurrent_workers_post_one_outbox_start_only_once() = runTest {
        val enteredStart = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val startCalls = AtomicInteger(0)
        val server = TimeEntry(
            id = "server-concurrent",
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
                payloadJson = json.encodeToString(
                    StartPayload("m1", "u1", null, null, "work", emptyList(), start = server.start),
                ),
            ),
        )
        val syncRemote = mockk<RemoteDataSource>()
        coEvery { syncRemote.getActiveTimeEntry() } returns Result.success(null)
        coEvery {
            syncRemote.startTimeEntry(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            startCalls.incrementAndGet()
            enteredStart.countDown()
            check(releaseStart.await(2, TimeUnit.SECONDS))
            Result.success(server)
        }

        coroutineScope {
            val first = async(Dispatchers.Default) { buildWorker(remoteDataSource = syncRemote).doWork() }
            assertTrue(enteredStart.await(2, TimeUnit.SECONDS))
            val second = async(Dispatchers.Default) { buildWorker(remoteDataSource = syncRemote).doWork() }
            releaseStart.countDown()

            assertEquals(ListenableWorker.Result.success(), first.await())
            assertEquals(ListenableWorker.Result.success(), second.await())
        }

        assertEquals(1, startCalls.get())
        assertTrue(db.outboxDao().peekAll().isEmpty())
    }

    @Test fun fresh_duplicate_create_does_not_adopt_the_identical_source_entry() = runTest {
        val source = TimeEntry(
            id = "server-source",
            userId = "u1",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "meeting",
        )
        val localCopy = source.copy(id = "local-copy")
        db.timeEntryDao().upsert(source.toEntity(updatedAt = 1L, syncState = SyncState.SYNCED))
        db.timeEntryDao().upsert(localCopy.toEntity(updatedAt = 2L, syncState = SyncState.PENDING))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.CREATE,
                organizationId = "org1",
                timeEntryId = localCopy.id,
                createdAtMs = 2L,
                payloadJson = json.encodeToString(
                    CreatePayload(
                        memberId = "m1",
                        userId = source.userId,
                        start = source.start,
                        end = source.end!!,
                        description = source.description!!,
                        projectId = null,
                        taskId = null,
                        billable = false,
                        tagIds = emptyList(),
                    ),
                ),
            ),
        )
        remote.entries = listOf(source)
        remote.startResult = { it.copy(id = "server-copy") }

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
        assertEquals(1, remote.created.size)
        assertEquals(SyncState.SYNCED, db.timeEntryDao().getById(source.id)?.syncState)
        assertEquals(SyncState.SYNCED, db.timeEntryDao().getById("server-copy")?.syncState)
        assertNull(db.timeEntryDao().getById(localCopy.id))
        assertTrue(db.outboxDao().peekAll().isEmpty())
    }

    @Test fun retried_duplicate_adopts_only_a_matching_entry_created_after_its_baseline() = runTest {
        val source = TimeEntry(
            id = "server-source",
            userId = "u1",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "meeting",
        )
        val createdWhileResponseWasLost = source.copy(id = "server-copy")
        val localCopy = source.copy(id = "local-copy")
        db.timeEntryDao().upsert(source.toEntity(updatedAt = 1L, syncState = SyncState.SYNCED))
        db.timeEntryDao().upsert(localCopy.toEntity(updatedAt = 2L, syncState = SyncState.PENDING))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.CREATE,
                organizationId = "org1",
                timeEntryId = localCopy.id,
                createdAtMs = 2L,
                payloadJson = json.encodeToString(
                    CreatePayload(
                        memberId = "m1",
                        userId = source.userId,
                        start = source.start,
                        end = source.end!!,
                        description = source.description!!,
                        projectId = null,
                        taskId = null,
                        billable = false,
                        tagIds = emptyList(),
                        recoveryBaselineEntryIds = listOf(source.id),
                    ),
                ),
            ),
        )
        remote.entries = listOf(source, createdWhileResponseWasLost)

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
        assertTrue("Retry should adopt the committed response instead of posting twice", remote.created.isEmpty())
        assertEquals(SyncState.SYNCED, db.timeEntryDao().getById(source.id)?.syncState)
        assertEquals(SyncState.SYNCED, db.timeEntryDao().getById(createdWhileResponseWasLost.id)?.syncState)
        assertNull(db.timeEntryDao().getById(localCopy.id))
        assertTrue(db.outboxDao().peekAll().isEmpty())
    }

    @Test fun successful_drain_stamps_push_timestamp() = runTest {
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

        buildWorker().doWork()

        val meta = db.syncMetaDao().get("org1")
        assertEquals(1L, meta?.lastPushAtMs)
        // Push stamping must not invent a pull moment: lastFullSyncAtMs stays at its seed default.
        assertEquals(0L, meta?.lastFullSyncAtMs)
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
        // No op reached the server, so no push timestamp is written.
        assertNull(db.syncMetaDao().get("org1"))
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

    @Test fun solidtime_validation_and_domain_errors_are_dead_lettered() = runTest {
        listOf(
            400 to """{"error":true,"key":"overlapping_time_entry","message":"The time entry overlaps."}""",
            422 to """{"message":"The given data was invalid.","errors":{"project_id":["Invalid project."]}}""",
        ).forEachIndexed { index, (code, body) ->
            remote.writeError = httpException(code, body)
            val entryId = "server-$index"
            db.outboxDao().insert(
                OutboxEntity(
                    opType = OutboxOpType.DELETE,
                    organizationId = "org1",
                    timeEntryId = entryId,
                    createdAtMs = index.toLong() + 1,
                    payloadJson = "{}",
                ),
            )

            assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
            assertTrue(db.outboxDao().peekAll().single { it.timeEntryId == entryId }.deadLettered)
        }
    }

    @Test fun timeout_rate_limit_and_server_errors_remain_retryable() = runTest {
        listOf(408 to 1, 429 to 0, 503 to 1).forEachIndexed { index, (code, expectedAttempts) ->
            remote.writeError = httpException(code, """{"message":"temporary"}""")
            val entryId = "server-$index"
            db.outboxDao().insert(
                OutboxEntity(
                    opType = OutboxOpType.DELETE,
                    organizationId = "org1",
                    timeEntryId = entryId,
                    createdAtMs = index.toLong() + 1,
                    payloadJson = "{}",
                ),
            )

            assertEquals(ListenableWorker.Result.retry(), buildWorker().doWork())
            val stored = db.outboxDao().peekAll().single { it.timeEntryId == entryId }
            assertEquals(expectedAttempts, stored.attemptCount)
            assertEquals(false, stored.deadLettered)
            db.outboxDao().delete(stored)
        }
    }

    @Test fun repeated_rate_limits_never_dead_letter_the_change() = runTest {
        remote.writeError = httpException(429, """{"message":"Too Many Attempts."}""")
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.DELETE,
                organizationId = "org1",
                timeEntryId = "server-rate-limited",
                createdAtMs = 1L,
                payloadJson = "{}",
            ),
        )

        repeat(SyncWorker.MAX_ATTEMPTS + 1) {
            assertEquals(ListenableWorker.Result.retry(), buildWorker().doWork())
        }

        val stored = db.outboxDao().peekAll().single()
        assertFalse(stored.deadLettered)
        assertTrue(db.outboxDao().peekPending().contains(stored))
    }

    @Test fun repeated_rate_limits_during_conflict_preflight_never_dead_letter_the_change() = runTest {
        val local = TimeEntry(
            id = "server-rate-limited-preflight",
            userId = "u1",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = "2026-07-07T09:00:00Z",
            description = "edited locally",
        )
        db.timeEntryDao().upsert(local.toEntity(updatedAt = 1L, syncState = SyncState.PENDING))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = local.organizationId,
                timeEntryId = local.id,
                createdAtMs = 1L,
                payloadJson = json.encodeToString(
                    UpdatePayload(
                        userId = local.userId,
                        start = local.start,
                        end = local.end,
                        description = local.description,
                        projectId = null,
                        taskId = null,
                        billable = false,
                        tagIds = emptyList(),
                    ),
                ),
                baseSnapshotJson = json.encodeToString(
                    ConflictSnapshot.of(
                        start = local.start,
                        end = local.end,
                        description = "before",
                        projectId = null,
                        taskId = null,
                        billable = false,
                        tagIds = emptyList(),
                    ),
                ),
            ),
        )
        remote.memberships = listOf(Membership("m1", "member", Organization("org1", "Org", "USD")))
        remote.timeEntriesQueryValidator = {
            httpException(429, """{"message":"Too Many Attempts."}""")
        }

        repeat(SyncWorker.MAX_ATTEMPTS + 1) {
            assertEquals(ListenableWorker.Result.retry(), buildWorker().doWork())
        }

        val stored = db.outboxDao().peekAll().single()
        assertFalse(stored.deadLettered)
        assertTrue(db.outboxDao().peekPending().contains(stored))
    }

    private fun httpException(code: Int, body: String): HttpException = HttpException(
        Response.error<Unit>(code, body.toResponseBody()),
    )

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

    @Test fun stop_is_not_blocked_by_history_conflict_preflight() = runTest {
        val serverBase = TimeEntry(
            id = "server-1",
            userId = "u1",
            organizationId = "org1",
            start = "2026-08-07T12:00:00Z",
            end = null,
        )
        val stopped = serverBase.copy(end = "2026-08-08T12:00:00Z")
        db.timeEntryDao().upsert(stopped.toEntity(updatedAt = 1L, syncState = SyncState.PENDING))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.STOP,
                organizationId = "org1",
                timeEntryId = serverBase.id,
                createdAtMs = 1L,
                payloadJson = json.encodeToString(StopPayload("u1", serverBase.start, stopped.end!!)),
                baseSnapshotJson = json.encodeToString(
                    ConflictSnapshot.of(
                        serverBase.start,
                        serverBase.end,
                        serverBase.description,
                        serverBase.projectId,
                        serverBase.taskId,
                        serverBase.billable,
                        emptyList(),
                    ),
                ),
            ),
        )
        remote.timeEntriesQueryValidator = { java.io.IOException("history endpoint unavailable") }
        remote.stopResult = { stopped }

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
        assertTrue(db.outboxDao().peekAll().isEmpty())
        assertEquals(stopped.end, db.timeEntryDao().getById(serverBase.id)?.end)
        assertNull(remote.lastTimeEntriesQuery)
    }

    @Test fun stop_reaches_server_without_discarding_existing_metadata_conflict() = runTest {
        val localConflict = TimeEntry(
            id = "server-1",
            userId = "u1",
            organizationId = "org1",
            start = "2026-08-07T12:00:00Z",
            end = "2026-08-08T12:00:00Z",
            description = "mine",
        )
        db.timeEntryDao().upsert(
            localConflict.toEntity(updatedAt = 1L, syncState = SyncState.CONFLICT).copy(
                conflictServerJson = json.encodeToString(localConflict.copy(description = "theirs", end = null)),
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.STOP,
                organizationId = "org1",
                timeEntryId = localConflict.id,
                createdAtMs = 1L,
                payloadJson = json.encodeToString(StopPayload("u1", localConflict.start, localConflict.end!!)),
            ),
        )
        remote.stopResult = {
            localConflict.copy(description = "theirs", end = localConflict.end, duration = 86_400)
        }

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())

        val stored = db.timeEntryDao().getById(localConflict.id)
        assertEquals(SyncState.CONFLICT, stored?.syncState)
        assertEquals("mine", stored?.description)
        val serverCopy = json.decodeFromString<TimeEntry>(stored!!.conflictServerJson!!)
        assertEquals("theirs", serverCopy.description)
        assertEquals(localConflict.end, serverCopy.end)
        assertTrue(db.outboxDao().peekAll().isEmpty())
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

    @Test fun conflict_preflight_uses_solidtime_compatible_whole_second_timestamps() = runTest {
        nowMs = java.time.Instant.parse("2026-08-08T10:00:00Z").toEpochMilli() + 123L
        val base = TimeEntry(
            id = "server-1",
            userId = "u1",
            organizationId = "org1",
            start = "2026-07-01T08:00:00Z",
            end = "2026-07-01T09:00:00Z",
            description = "before",
        )
        remote.entries = listOf(base)
        remote.memberships = listOf(Membership("m1", "member", Organization("org1", "Org", "USD")))
        remote.timeEntriesQueryValidator = { query ->
            val wholeSecondUtc = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")
            if (query.start?.matches(wholeSecondUtc) == true && query.end?.matches(wholeSecondUtc) == true) {
                null
            } else {
                IllegalArgumentException("Solidtime rejects fractional-second time-entry filters")
            }
        }
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
        assertTrue(db.outboxDao().peekAll().isEmpty())
        assertEquals("mine", db.timeEntryDao().getById(local.id)?.description)
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

    // A common offline multi-action experience can enqueue START -> STOP -> UPDATE before the
    // worker gets a network window. The final UPDATE must survive the intermediate synced writes
    // produced while START and STOP are reconciled in this same drain.
    @Test fun start_stop_update_in_one_drain_rekeys_every_op_and_keeps_the_latest_metadata() = runTest {
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.START,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 1L,
                payloadJson = json.encodeToString(
                    StartPayload("m1", "u1", null, null, "initial", emptyList(), start = "2020-01-01T09:00:00Z"),
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
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 3L,
                payloadJson = json.encodeToString(
                    UpdatePayload(
                        userId = "u1",
                        start = "2020-01-01T09:00:00Z",
                        end = "2020-01-01T10:00:00Z",
                        description = "latest metadata",
                        projectId = "project-2",
                        taskId = "task-2",
                        billable = true,
                        tagIds = listOf("tag-2"),
                    ),
                ),
            ),
        )
        remote.startResult = { it.copy(id = "server-42") }
        // Make reconciliation timestamps newer than every queued op. Same-drain writes are not
        // evidence that the later queued UPDATE was superseded.
        nowMs = 100L

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())

        assertTrue(db.outboxDao().peekAll().isEmpty())
        assertNull(db.timeEntryDao().getById("local-1"))
        val stored = requireNotNull(db.timeEntryDao().getById("server-42"))
        assertEquals("latest metadata", stored.description)
        assertEquals("project-2", stored.projectId)
        assertEquals("task-2", stored.taskId)
        assertTrue(stored.billable)
        assertEquals(listOf("tag-2"), db.timeEntryDao().tagIdsFor(stored.id))
        assertEquals(SyncState.SYNCED, stored.syncState)
    }

    // Editing a running timer and then stopping it is another common offline ordering. STOP only
    // narrows the end timestamp, so it must not make the earlier metadata UPDATE redundant.
    @Test fun start_update_stop_in_one_drain_applies_metadata_before_the_narrow_stop() = runTest {
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.START,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 1L,
                payloadJson = json.encodeToString(
                    StartPayload("m1", "u1", null, null, "initial", emptyList(), start = "2020-01-01T09:00:00Z"),
                ),
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 2L,
                payloadJson = json.encodeToString(
                    UpdatePayload(
                        userId = "u1",
                        start = "2020-01-01T09:00:00Z",
                        end = null,
                        description = "edited while running",
                        projectId = "project-2",
                        taskId = "task-2",
                        billable = true,
                        tagIds = listOf("tag-2"),
                    ),
                ),
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.STOP,
                organizationId = "org1",
                timeEntryId = "local-1",
                createdAtMs = 3L,
                payloadJson = json.encodeToString(
                    StopPayload("u1", "2020-01-01T09:00:00Z", end = "2020-01-01T10:00:00Z"),
                ),
            ),
        )
        remote.startResult = { it.copy(id = "server-42") }
        var updateCalls = 0
        remote.updateResult = {
            updateCalls += 1
            it
        }
        remote.stopResult = {
            it.copy(
                description = "edited while running",
                projectId = "project-2",
                taskId = "task-2",
                billable = true,
            )
        }
        nowMs = 100L

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())

        assertEquals(1, updateCalls)
        assertTrue(db.outboxDao().peekAll().isEmpty())
        val stored = requireNotNull(db.timeEntryDao().getById("server-42"))
        assertEquals("edited while running", stored.description)
        assertEquals("2020-01-01T10:00:00Z", stored.end)
        assertEquals(SyncState.SYNCED, stored.syncState)
    }

    @Test fun transient_start_failure_defers_its_stop_and_update_but_not_independent_work() = runTest {
        val local = TimeEntry(
            id = "local-1",
            userId = "u1",
            organizationId = "org1",
            start = "2020-01-01T09:00:00Z",
            end = "2020-01-01T10:00:00Z",
            description = "edited offline",
        )
        db.timeEntryDao().upsert(local.toEntity(updatedAt = 4L, syncState = SyncState.PENDING))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.START,
                organizationId = "org1",
                timeEntryId = local.id,
                createdAtMs = 1L,
                payloadJson = json.encodeToString(
                    StartPayload("m1", "u1", null, null, "initial", emptyList(), start = local.start),
                ),
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.STOP,
                organizationId = "org1",
                timeEntryId = local.id,
                createdAtMs = 2L,
                payloadJson = json.encodeToString(StopPayload("u1", local.start, end = checkNotNull(local.end))),
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = "org1",
                timeEntryId = local.id,
                createdAtMs = 3L,
                payloadJson = json.encodeToString(
                    UpdatePayload("u1", local.start, local.end, local.description.orEmpty(), null, null, true, emptyList()),
                ),
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.DELETE,
                organizationId = "org1",
                timeEntryId = "independent-server-entry",
                createdAtMs = 4L,
                payloadJson = "{}",
            ),
        )
        val server = local.copy(id = "server-42", end = null, description = "initial")
        val syncRemote = mockk<RemoteDataSource>()
        coEvery { syncRemote.getActiveTimeEntry() } returns Result.success(null)
        coEvery {
            syncRemote.startTimeEntry(any(), any(), any(), any(), any(), any(), any())
        } returnsMany listOf(Result.failure(java.io.IOException("offline")), Result.success(server))
        coEvery { syncRemote.stopTimeEntry(any(), any(), any(), any(), any()) } returns Result.success(
            server.copy(end = local.end),
        )
        coEvery { syncRemote.updateTimeEntry(any(), any(), any()) } coAnswers {
            Result.success(secondArg())
        }
        coEvery { syncRemote.deleteTimeEntry(any(), any()) } returns Result.success(Unit)

        assertEquals(ListenableWorker.Result.retry(), buildWorker(remoteDataSource = syncRemote).doWork())

        val deferred = db.outboxDao().peekAll()
        assertEquals(listOf(OutboxOpType.START, OutboxOpType.STOP, OutboxOpType.UPDATE), deferred.map { it.opType })
        assertEquals(listOf(1, 0, 0), deferred.map { it.attemptCount })
        coVerify(exactly = 0) { syncRemote.stopTimeEntry(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { syncRemote.updateTimeEntry(any(), any(), any()) }
        coVerify(exactly = 1) { syncRemote.deleteTimeEntry("org1", "independent-server-entry") }

        assertEquals(ListenableWorker.Result.success(), buildWorker(remoteDataSource = syncRemote).doWork())

        assertTrue(db.outboxDao().peekAll().isEmpty())
        coVerify(exactly = 2) { syncRemote.startTimeEntry(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { syncRemote.stopTimeEntry(any(), "server-42", any(), any(), any()) }
        coVerify(exactly = 1) { syncRemote.updateTimeEntry("org1", match { it.id == "server-42" }, any()) }
        val stored = requireNotNull(db.timeEntryDao().getById("server-42"))
        assertEquals("edited offline", stored.description)
        assertTrue(stored.billable)
        assertEquals(SyncState.SYNCED, stored.syncState)
    }

    @Test fun rate_limited_start_preserves_its_retry_budget_and_defers_the_dependent_stop() = runTest {
        val start = "2020-01-01T09:00:00Z"
        val end = "2020-01-01T10:00:00Z"
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.START,
                organizationId = "org1",
                timeEntryId = "local-rate-limited",
                createdAtMs = 1L,
                payloadJson = json.encodeToString(
                    StartPayload("m1", "u1", null, null, "work", emptyList(), start = start),
                ),
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.STOP,
                organizationId = "org1",
                timeEntryId = "local-rate-limited",
                createdAtMs = 2L,
                payloadJson = json.encodeToString(StopPayload("u1", start, end)),
            ),
        )
        val server = TimeEntry(
            id = "server-rate-limited",
            userId = "u1",
            organizationId = "org1",
            start = start,
            end = null,
        )
        val syncRemote = mockk<RemoteDataSource>()
        coEvery { syncRemote.getActiveTimeEntry() } returns Result.success(null)
        coEvery {
            syncRemote.startTimeEntry(any(), any(), any(), any(), any(), any(), any())
        } returnsMany listOf(
            Result.failure(httpException(429, "rate limited")),
            Result.success(server),
        )
        coEvery { syncRemote.stopTimeEntry(any(), any(), any(), any(), any()) } returns Result.success(server.copy(end = end))

        assertEquals(ListenableWorker.Result.retry(), buildWorker(remoteDataSource = syncRemote).doWork())

        val deferred = db.outboxDao().peekAll()
        assertEquals(listOf(OutboxOpType.START, OutboxOpType.STOP), deferred.map { it.opType })
        assertEquals(listOf(0, 0), deferred.map { it.attemptCount })
        assertTrue(deferred.first().lastError?.contains("Rate limited") == true)
        coVerify(exactly = 0) { syncRemote.stopTimeEntry(any(), any(), any(), any(), any()) }

        assertEquals(ListenableWorker.Result.success(), buildWorker(remoteDataSource = syncRemote).doWork())

        assertTrue(db.outboxDao().peekAll().isEmpty())
        coVerify(exactly = 1) { syncRemote.stopTimeEntry(any(), "server-rate-limited", any(), any(), end) }
        assertEquals(end, db.timeEntryDao().getById(server.id)?.end)
    }

    @Test fun exhausted_start_retry_budget_dead_letters_its_chain_without_blocking_other_entries() = runTest {
        val start = "2020-01-01T09:00:00Z"
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.START,
                organizationId = "org1",
                timeEntryId = "local-exhausted",
                createdAtMs = 1L,
                attemptCount = SyncWorker.MAX_ATTEMPTS - 1,
                payloadJson = json.encodeToString(
                    StartPayload("m1", "u1", null, null, "work", emptyList(), start = start),
                ),
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.STOP,
                organizationId = "org1",
                timeEntryId = "local-exhausted",
                createdAtMs = 2L,
                payloadJson = json.encodeToString(StopPayload("u1", start, "2020-01-01T10:00:00Z")),
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = "org1",
                timeEntryId = "local-exhausted",
                createdAtMs = 3L,
                payloadJson = json.encodeToString(
                    UpdatePayload("u1", start, null, "latest", null, null, false, emptyList()),
                ),
            ),
        )
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.DELETE,
                organizationId = "org1",
                timeEntryId = "independent-server-entry",
                createdAtMs = 4L,
                payloadJson = "{}",
            ),
        )
        val syncRemote = mockk<RemoteDataSource>()
        coEvery { syncRemote.getActiveTimeEntry() } returns Result.success(null)
        coEvery {
            syncRemote.startTimeEntry(any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(java.io.IOException("still offline"))
        coEvery { syncRemote.deleteTimeEntry(any(), any()) } returns Result.success(Unit)

        assertEquals(ListenableWorker.Result.success(), buildWorker(remoteDataSource = syncRemote).doWork())

        val failedChain = db.outboxDao().peekAll()
        assertEquals(3, failedChain.size)
        assertTrue(failedChain.all { it.timeEntryId == "local-exhausted" && it.deadLettered })
        coVerify(exactly = 0) { syncRemote.stopTimeEntry(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { syncRemote.updateTimeEntry(any(), any(), any()) }
        coVerify(exactly = 1) { syncRemote.deleteTimeEntry("org1", "independent-server-entry") }
    }

    @Test fun adopted_start_merges_when_the_server_row_was_already_pulled_into_room() = runTest {
        val local = TimeEntry(
            id = "local-1",
            userId = "u1",
            organizationId = "org1",
            start = "2026-07-07T08:00:00Z",
            end = null,
            description = "local optimistic timer",
        )
        val server = local.copy(id = "server-9", description = "server timer")
        db.timeEntryDao().upsert(local.toEntity(updatedAt = 2L, syncState = SyncState.PENDING))
        db.timeEntryDao().upsert(server.toEntity(updatedAt = 3L, syncState = SyncState.SYNCED))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.START,
                organizationId = "org1",
                timeEntryId = local.id,
                createdAtMs = 1L,
                payloadJson = json.encodeToString(
                    StartPayload("m1", "u1", null, null, local.description.orEmpty(), emptyList(), start = local.start),
                ),
            ),
        )
        remote.active = server

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())

        assertTrue("The adopted operation must not be dead-lettered by a primary-key collision", db.outboxDao().peekAll().isEmpty())
        assertNull(db.timeEntryDao().getById(local.id))
        val stored = requireNotNull(db.timeEntryDao().getById(server.id))
        assertEquals(server.description, stored.description)
        assertEquals(SyncState.SYNCED, stored.syncState)
        assertTrue("Adopting an already-known active entry must not POST a duplicate", remote.started.isEmpty())
    }

    @Test fun server_pull_then_start_sync_rekeys_the_optimistic_room_row_without_a_duplicate() = runTest {
        val repository = TimeEntryRepository(
            db.timeEntryDao(),
            db.catalogDao(),
            db.outboxDao(),
            db.syncMetaDao(),
            remote,
            clock,
            json,
            db,
        )
        val local = repository.startEntry(
            organizationId = "org1",
            memberId = "m1",
            userId = "u1",
            projectId = null,
            taskId = null,
            description = "started in the app",
            tagIds = emptyList(),
        )
        val server = local.copy(id = "server-active", description = "started elsewhere", end = null)
        remote.entries = listOf(server)
        remote.active = server

        assertTrue(repository.refreshAll("org1", "m1").isSuccess)
        assertEquals(
            setOf(local.id, server.id),
            db.timeEntryDao().observeEntries("org1").first().map { it.id }.toSet(),
        )

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())

        val roomRows = db.timeEntryDao().observeEntries("org1").first()
        assertEquals(listOf(server.id), roomRows.map { it.id })
        assertEquals(SyncState.SYNCED, roomRows.single().syncState)
        assertTrue("The worker should adopt the pulled server timer", remote.started.isEmpty())
        assertTrue(db.outboxDao().peekAll().isEmpty())
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

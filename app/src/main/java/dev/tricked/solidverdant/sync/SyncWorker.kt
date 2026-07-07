/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.tricked.solidverdant.data.local.db.OutboxDao
import dev.tricked.solidverdant.data.local.db.OutboxEntity
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.local.db.SyncState
import dev.tricked.solidverdant.data.local.db.TimeEntryDao
import dev.tricked.solidverdant.data.local.db.toEntity
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.remote.RemoteDataSource
import dev.tricked.solidverdant.util.Clock
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val outboxDao: OutboxDao,
    private val timeEntryDao: TimeEntryDao,
    private val remote: RemoteDataSource,
    private val json: Json,
    private val clock: Clock,
    private val syncStatus: SyncStatusReporter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        syncStatus.set(SyncStatus.Syncing)
        // Drain across all organizations: a single background worker is responsible for flushing
        // the whole outbox, and each op already carries its own organizationId for the API call.
        // (The per-org filtering in observeSyncOperations is only for scoping the UI display.)
        // Dead-lettered ops are excluded so permanently-failed work is never re-attempted.
        val ops = outboxDao.peekPending() // id ASC
        // Entries whose creating op has been dead-lettered in this run; their dependent ops
        // (still referencing the local- id) can never succeed and are skipped/cascaded.
        val failedEntryIds = mutableSetOf<String>()
        for (op in ops) {
            if (op.timeEntryId in failedEntryIds) continue
            when (process(op)) {
                Outcome.SUCCESS -> outboxDao.delete(op)
                Outcome.RETRY -> {
                    val attempts = op.attemptCount + 1
                    if (attempts >= MAX_ATTEMPTS) {
                        // Transient retries exhausted -> move to dead-letter and keep draining.
                        deadLetter(op, "Sync failed after $MAX_ATTEMPTS attempts", failedEntryIds)
                    } else {
                        outboxDao.update(
                            op.copy(
                                attemptCount = attempts,
                                lastError = "Temporary server or network error; retry scheduled",
                            ),
                        )
                        syncStatus.set(SyncStatus.Idle)
                        return Result.retry()
                    }
                }
                Outcome.FAIL -> {
                    // Server rejected the change: this will never succeed, so dead-letter it now.
                    deadLetter(op, "Server rejected this change", failedEntryIds)
                    syncStatus.set(SyncStatus.Error("A change could not be synced"))
                }
            }
        }
        if (syncStatus.status.value !is SyncStatus.Error) syncStatus.set(SyncStatus.Idle)
        return Result.success()
    }

    /**
     * Move [op] to the terminal dead-letter state. When a CREATE/START that never obtained a
     * server id fails permanently, its dependent ops (STOP/UPDATE/DELETE still keyed by the
     * `local-` id) can never succeed either, so the whole chain is dead-lettered together.
     */
    private suspend fun deadLetter(op: OutboxEntity, error: String, failedEntryIds: MutableSet<String>) {
        val isUnresolvedCreate =
            (op.opType == OutboxOpType.START || op.opType == OutboxOpType.CREATE) &&
                op.timeEntryId.startsWith("local-")
        if (isUnresolvedCreate) {
            failedEntryIds += op.timeEntryId
            outboxDao.deadLetterByEntryId(op.timeEntryId, error)
        } else {
            outboxDao.update(op.copy(attemptCount = op.attemptCount + 1, lastError = error, deadLettered = true))
        }
    }

    private enum class Outcome { SUCCESS, RETRY, FAIL }

    private suspend fun process(op: OutboxEntity): Outcome = try {
        when (op.opType) {
            OutboxOpType.START -> {
                val p = json.decodeFromString<StartPayload>(op.payloadJson)
                // Idempotency: if a previous attempt already committed on the server but its
                // response was lost, the server now has our single active entry. Adopt it instead
                // of starting a duplicate.
                val adopted = if (op.attemptCount > 0) remote.getActiveTimeEntry().getOrNull() else null
                val server = adopted
                    ?: remote.startTimeEntry(op.organizationId, p.memberId, p.userId, p.projectId, p.taskId, p.description).getOrThrow()
                reconcile(op.timeEntryId, server, fallbackTagIds = p.tagIds)
                Outcome.SUCCESS
            }
            OutboxOpType.CREATE -> {
                val p = json.decodeFromString<CreatePayload>(op.payloadJson)
                // Idempotency: on a retry, a matching completed entry may already exist from a
                // committed-but-unacked prior attempt; adopt it rather than creating a duplicate.
                val adopted = if (op.attemptCount > 0) findCreatedDuplicate(op.organizationId, p) else null
                val server = adopted ?: run {
                    val local = TimeEntry(
                        id = op.timeEntryId, userId = p.userId, organizationId = op.organizationId,
                        start = p.start, end = p.end, description = p.description,
                        projectId = p.projectId, taskId = p.taskId, billable = p.billable,
                    )
                    remote.createTimeEntry(op.organizationId, p.memberId, p.userId, local, p.tagIds).getOrThrow()
                }
                reconcile(op.timeEntryId, server, fallbackTagIds = p.tagIds)
                Outcome.SUCCESS
            }
            OutboxOpType.STOP -> {
                val p = json.decodeFromString<StopPayload>(op.payloadJson)
                val server = remote.stopTimeEntry(op.organizationId, op.timeEntryId, p.userId, p.start).getOrThrow()
                persistSynced(server)
                Outcome.SUCCESS
            }
            OutboxOpType.UPDATE -> {
                val p = json.decodeFromString<UpdatePayload>(op.payloadJson)
                val entry = TimeEntry(
                    id = op.timeEntryId, description = p.description, userId = p.userId,
                    start = p.start, end = p.end, projectId = p.projectId, taskId = p.taskId,
                    billable = p.billable, organizationId = op.organizationId,
                )
                val server = remote.updateTimeEntry(op.organizationId, entry, p.tagIds).getOrThrow()
                persistSynced(server, p.tagIds)
                Outcome.SUCCESS
            }
            OutboxOpType.DELETE -> {
                // A purely-local entry never reached the server; treat as done.
                if (op.timeEntryId.startsWith("local-")) {
                    Outcome.SUCCESS
                } else {
                    remote.deleteTimeEntry(op.organizationId, op.timeEntryId).getOrThrow()
                    timeEntryDao.deleteById(op.timeEntryId)
                    Outcome.SUCCESS
                }
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Outbox op ${op.id} failed")
        classify(e)
    }

    /**
     * Best-effort duplicate detection for CREATE retries: look for a server entry that matches the
     * queued payload on the immutable fields (start/end/project/task/description). Used only when a
     * prior attempt may have committed but lost its response.
     */
    private suspend fun findCreatedDuplicate(orgId: String, p: CreatePayload): TimeEntry? {
        val page = remote.getTimeEntries(
            orgId,
            p.memberId,
            limit = 250,
            offset = 0,
            onlyFullDates = false,
        ).getOrNull() ?: return null
        return page.data.firstOrNull { e ->
            e.start == p.start &&
                e.end == p.end &&
                e.projectId == p.projectId &&
                e.taskId == p.taskId &&
                (e.description ?: "") == p.description
        }
    }

    private suspend fun reconcile(localId: String, server: TimeEntry, fallbackTagIds: List<String>? = null) {
        if (localId != server.id) {
            timeEntryDao.rekey(localId, server.id)
            outboxDao.rekeyReferences(localId, server.id)
        }
        timeEntryDao.upsert(server.toEntity(updatedAt = clock.nowMs(), syncState = SyncState.SYNCED))
        // Preserve the server's authoritative tag set; only fall back to the queued tags when the
        // server returned none (avoids clobbering a server-side tag merge).
        val tagIds = server.tags.map { it.id }.ifEmpty { fallbackTagIds.orEmpty() }
        timeEntryDao.replaceTagRefs(server.id, tagIds)
    }

    private suspend fun persistSynced(server: TimeEntry, fallbackTagIds: List<String>? = null) {
        timeEntryDao.upsert(server.toEntity(updatedAt = clock.nowMs(), syncState = SyncState.SYNCED))
        val tagIds = server.tags.map { it.id }.ifEmpty { fallbackTagIds.orEmpty() }
        timeEntryDao.replaceTagRefs(server.id, tagIds)
    }

    private fun classify(e: Exception): Outcome = when {
        e is IOException -> Outcome.RETRY
        e is HttpException && e.code() == 429 -> Outcome.RETRY
        e is HttpException && e.code() >= 500 -> Outcome.RETRY
        else -> Outcome.FAIL
    }

    companion object {
        /** Cap on transient retries before an op is moved to the dead-letter state. */
        const val MAX_ATTEMPTS = 5
    }
}

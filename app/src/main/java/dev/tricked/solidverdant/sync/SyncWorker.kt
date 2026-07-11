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
        // A previous op in this same drain may rekey an entry's id (local- id -> server id) after a
        // successful START/CREATE. The `ops` list above is a snapshot taken once at the top of the
        // run, so later entries in that list still hold the dead local id in memory even though
        // `rekeyReferences` already rewrote the DB rows. Track the mapping here and apply it to each
        // op immediately before it is processed (and again before any write-back), so a STOP/UPDATE
        // in the same drain targets the real server id instead of 404ing on the retired local one.
        val rekeyed = mutableMapOf<String, String>()
        var retryResult: Result? = null
        for (rawOp in ops) {
            val op = rawOp.rekeyedWith(rekeyed)
            if (op.timeEntryId in failedEntryIds) continue
            when (val outcome = process(op)) {
                is Outcome.Success -> {
                    outboxDao.delete(op)
                    // Record the rekey so every remaining op for this entry in this same drain
                    // (still holding the old id in its in-memory snapshot) is remapped before use.
                    outcome.rekeyedTo?.let { rekeyed[op.timeEntryId] = it }
                }
                Outcome.Retry -> {
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
                        // Don't abort the whole drain on one transient failure: keep flushing the
                        // remaining independent ops and only ask WorkManager to retry this run
                        // (which will re-attempt the failed op) once the rest have been tried.
                        retryResult = Result.retry()
                    }
                }
                Outcome.Fail -> {
                    // Server rejected the change: this will never succeed, so dead-letter it now.
                    deadLetter(op, "Server rejected this change", failedEntryIds)
                    syncStatus.set(SyncStatus.Error("A change could not be synced"))
                }
                Outcome.Superseded -> {
                    // A revived dead-lettered op that a later, already-applied write has made
                    // stale; drop it rather than reverting the entry to older state.
                    outboxDao.delete(op)
                }
            }
        }
        if (retryResult != null) {
            syncStatus.set(SyncStatus.Idle)
            return retryResult
        }
        if (syncStatus.status.value !is SyncStatus.Error) syncStatus.set(SyncStatus.Idle)
        return Result.success()
    }

    /** Rewrite [OutboxEntity.timeEntryId] through the in-run rekey map, following chained hops. */
    private fun OutboxEntity.rekeyedWith(rekeyed: Map<String, String>): OutboxEntity {
        var id = timeEntryId
        var next = rekeyed[id]
        while (next != null && next != id) {
            id = next
            next = rekeyed[id]
        }
        return if (id == timeEntryId) this else copy(timeEntryId = id)
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
            // op.timeEntryId already reflects any in-run rekey (see rekeyedWith above), so this
            // write-back can't clobber a rekeyed id back to a retired local- id.
            outboxDao.update(op.copy(attemptCount = op.attemptCount + 1, lastError = error, deadLettered = true))
        }
    }

    private sealed class Outcome {
        /** [rekeyedTo] is set only when this op reconciled a local- id to a new server id. */
        data class Success(val rekeyedTo: String? = null) : Outcome()
        data object Retry : Outcome()
        data object Fail : Outcome()

        /** A revived dead-lettered op superseded by a later write; drop without touching the server. */
        data object Superseded : Outcome()
    }

    private suspend fun process(op: OutboxEntity): Outcome = try {
        when (op.opType) {
            OutboxOpType.START -> {
                val p = json.decodeFromString<StartPayload>(op.payloadJson)
                // Idempotency: if a previous attempt already committed on the server but its
                // response was lost, the server now has our single active entry. Adopt it instead
                // of starting a duplicate. Run this scan unconditionally (not just on retries) so a
                // process death after the server call committed but before this op was deleted still
                // adopts the existing entry on the next run instead of re-POSTing a duplicate.
                val adopted = remote.getActiveTimeEntry().getOrNull()
                    ?.takeIf { it.userId == p.userId }
                val server = adopted
                    ?: remote.startTimeEntry(
                        op.organizationId,
                        p.memberId,
                        p.userId,
                        p.projectId,
                        p.taskId,
                        p.description,
                        startTime = p.start,
                    ).getOrThrow()
                reconcile(op.timeEntryId, server, fallbackTagIds = p.tagIds)
            }
            OutboxOpType.CREATE -> {
                val p = json.decodeFromString<CreatePayload>(op.payloadJson)
                // Idempotency: a matching completed entry may already exist from a committed-but-
                // unacked prior attempt (including a hard process death right after the server
                // commit); adopt it rather than creating a duplicate. Run unconditionally, not just
                // when attemptCount > 0 - attemptCount is only bumped on a *transient* failure, so it
                // never reflects "we may have already committed this on the server".
                val adopted = findCreatedDuplicate(op.organizationId, p)
                val server = adopted ?: run {
                    val local = TimeEntry(
                        id = op.timeEntryId, userId = p.userId, organizationId = op.organizationId,
                        start = p.start, end = p.end, description = p.description,
                        projectId = p.projectId, taskId = p.taskId, billable = p.billable,
                    )
                    remote.createTimeEntry(op.organizationId, p.memberId, p.userId, local, p.tagIds).getOrThrow()
                }
                reconcile(op.timeEntryId, server, fallbackTagIds = p.tagIds)
            }
            OutboxOpType.STOP -> {
                val p = json.decodeFromString<StopPayload>(op.payloadJson)
                val server = remote.stopTimeEntry(op.organizationId, op.timeEntryId, p.userId, p.start, endTime = p.end).getOrThrow()
                persistSynced(server)
                Outcome.Success()
            }
            OutboxOpType.UPDATE -> {
                // A dead-lettered UPDATE revived via resetForRetry re-enters peekPending() with
                // deadLettered already cleared, so that flag can't be used here to gate this check -
                // it must run for every UPDATE. Guard against a revived-but-superseded op: a later
                // write for the same entry may have already synced (and been deleted from the
                // outbox) or may still be queued behind this one. Detect the "already synced newer
                // state" case by comparing this op's enqueue time against the entry's last-synced
                // time; detect the "still queued newer op" case via a row-count check. Either way,
                // applying this stale UPDATE now would revert the entry to older data.
                val newerQueued = outboxDao.countNewerPending(op.timeEntryId, op.id) > 0
                val current = timeEntryDao.getById(op.timeEntryId)
                val newerSynced = current != null && current.syncState == SyncState.SYNCED && current.updatedAt > op.createdAtMs
                if (newerQueued || newerSynced) {
                    Outcome.Superseded
                } else {
                    val p = json.decodeFromString<UpdatePayload>(op.payloadJson)
                    val entry = TimeEntry(
                        id = op.timeEntryId, description = p.description, userId = p.userId,
                        start = p.start, end = p.end, projectId = p.projectId, taskId = p.taskId,
                        billable = p.billable, organizationId = op.organizationId,
                    )
                    val server = remote.updateTimeEntry(op.organizationId, entry, p.tagIds).getOrThrow()
                    persistSynced(server, p.tagIds)
                    Outcome.Success()
                }
            }
            OutboxOpType.DELETE -> {
                // A purely-local entry never reached the server; treat as done.
                if (op.timeEntryId.startsWith("local-")) {
                    Outcome.Success()
                } else {
                    remote.deleteTimeEntry(op.organizationId, op.timeEntryId).getOrThrow()
                    timeEntryDao.deleteById(op.timeEntryId)
                    Outcome.Success()
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

    private suspend fun reconcile(localId: String, server: TimeEntry, fallbackTagIds: List<String>? = null): Outcome.Success {
        val rekeyedTo = if (localId != server.id) {
            timeEntryDao.rekey(localId, server.id)
            outboxDao.rekeyReferences(localId, server.id)
            server.id
        } else {
            null
        }
        timeEntryDao.upsert(server.toEntity(updatedAt = clock.nowMs(), syncState = SyncState.SYNCED))
        // Preserve the server's authoritative tag set; only fall back to the queued tags when the
        // server returned none (avoids clobbering a server-side tag merge).
        val tagIds = server.tags.map { it.id }.ifEmpty { fallbackTagIds.orEmpty() }
        timeEntryDao.replaceTagRefs(server.id, tagIds)
        return Outcome.Success(rekeyedTo)
    }

    private suspend fun persistSynced(server: TimeEntry, fallbackTagIds: List<String>? = null) {
        timeEntryDao.upsert(server.toEntity(updatedAt = clock.nowMs(), syncState = SyncState.SYNCED))
        val tagIds = server.tags.map { it.id }.ifEmpty { fallbackTagIds.orEmpty() }
        timeEntryDao.replaceTagRefs(server.id, tagIds)
    }

    private fun classify(e: Exception): Outcome = when {
        e is IOException -> Outcome.Retry
        e is HttpException && e.code() == 429 -> Outcome.Retry
        e is HttpException && e.code() >= 500 -> Outcome.Retry
        else -> Outcome.Fail
    }

    companion object {
        /** Cap on transient retries before an op is moved to the dead-letter state. */
        const val MAX_ATTEMPTS = 5
    }
}

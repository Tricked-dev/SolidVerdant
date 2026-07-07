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
    private val syncStatus: SyncStatusReporter
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        syncStatus.set(SyncStatus.Syncing)
        val ops = outboxDao.peekAll() // id ASC
        for (op in ops) {
            when (process(op)) {
                Outcome.SUCCESS -> outboxDao.delete(op)
                Outcome.RETRY -> {
                    outboxDao.update(
                        op.copy(
                            attemptCount = op.attemptCount + 1,
                            lastError = "Temporary server or network error; retry scheduled",
                        )
                    )
                    syncStatus.set(SyncStatus.Idle)
                    return Result.retry()
                }
                Outcome.FAIL -> {
                    outboxDao.update(op.copy(attemptCount = op.attemptCount + 1, lastError = "Server rejected this change"))
                    syncStatus.set(SyncStatus.Error("A change could not be synced"))
                    // Skip this op so it does not wedge the queue; continue draining.
                }
            }
        }
        if (syncStatus.status.value !is SyncStatus.Error) syncStatus.set(SyncStatus.Idle)
        return Result.success()
    }

    private enum class Outcome { SUCCESS, RETRY, FAIL }

    private suspend fun process(op: OutboxEntity): Outcome = try {
        when (op.opType) {
            OutboxOpType.START -> {
                val p = json.decodeFromString<StartPayload>(op.payloadJson)
                val server = remote.startTimeEntry(op.organizationId, p.memberId, p.userId, p.projectId, p.taskId, p.description).getOrThrow()
                reconcile(op.timeEntryId, server)
                Outcome.SUCCESS
            }
            OutboxOpType.CREATE -> {
                val p = json.decodeFromString<CreatePayload>(op.payloadJson)
                val local = TimeEntry(
                    id = op.timeEntryId, userId = p.userId, organizationId = op.organizationId,
                    start = p.start, end = p.end, description = p.description,
                    projectId = p.projectId, taskId = p.taskId, billable = p.billable,
                )
                val server = remote.createTimeEntry(
                    op.organizationId, p.memberId, p.userId, local, p.tagIds,
                ).getOrThrow()
                reconcile(op.timeEntryId, server)
                timeEntryDao.replaceTagRefs(server.id, p.tagIds)
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
                    billable = p.billable, organizationId = op.organizationId
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

    private suspend fun reconcile(localId: String, server: TimeEntry) {
        if (localId != server.id) {
            timeEntryDao.rekey(localId, server.id)
            outboxDao.rekeyReferences(localId, server.id)
        }
        timeEntryDao.upsert(server.toEntity(updatedAt = clock.nowMs(), syncState = SyncState.SYNCED))
        timeEntryDao.replaceTagRefs(server.id, server.tags.map { it.id })
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
}

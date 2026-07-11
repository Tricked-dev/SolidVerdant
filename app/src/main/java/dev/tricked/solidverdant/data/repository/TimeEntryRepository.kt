/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.repository

import androidx.room.withTransaction
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.CatalogDao
import dev.tricked.solidverdant.data.local.db.OutboxDao
import dev.tricked.solidverdant.data.local.db.OutboxEntity
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.local.db.SyncMetaDao
import dev.tricked.solidverdant.data.local.db.SyncMetaEntity
import dev.tricked.solidverdant.data.local.db.SyncState
import dev.tricked.solidverdant.data.local.db.TimeEntryDao
import dev.tricked.solidverdant.data.local.db.toEntity
import dev.tricked.solidverdant.data.local.db.toModel
import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.remote.RemoteDataSource
import dev.tricked.solidverdant.sync.CreatePayload
import dev.tricked.solidverdant.sync.StartPayload
import dev.tricked.solidverdant.sync.StopPayload
import dev.tricked.solidverdant.sync.UpdatePayload
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeEntryRepository @Inject constructor(
    private val timeEntryDao: TimeEntryDao,
    private val catalogDao: CatalogDao,
    private val outboxDao: OutboxDao,
    private val syncMetaDao: SyncMetaDao,
    private val remote: RemoteDataSource,
    private val clock: Clock,
    private val json: Json,
    private val database: AppDatabase,
) : TimeEntryReader {
    enum class EntrySyncStatus { SYNCED, PENDING, RETRYING, FAILED }

    data class SyncOperation(
        val entryId: String,
        val type: OutboxOpType,
        val status: EntrySyncStatus,
        val attemptCount: Int,
        val error: String?,
    )
    fun observeProjects(orgId: String): Flow<List<Project>> = catalogDao.observeProjects(orgId).map { list -> list.map { it.toModel() } }

    fun observeClients(orgId: String): Flow<List<Client>> = catalogDao.observeClients(orgId).map { list -> list.map { it.toModel() } }

    fun observeTasks(orgId: String): Flow<List<Task>> = catalogDao.observeTasks(orgId).map { list -> list.map { it.toModel() } }

    fun observeTags(orgId: String): Flow<List<Tag>> = catalogDao.observeTags(orgId).map { list -> list.map { it.toModel() } }

    override fun observeTimeEntries(organizationId: String): Flow<List<TimeEntry>> = combine(
        timeEntryDao.observeVisibleEntries(organizationId),
        catalogDao.observeTags(organizationId),
        timeEntryDao.observeTagRefs(organizationId),
    ) { entities, tagEntities, tagRefs ->
        val tagsById = tagEntities.associate { it.id to it.toModel() }
        val tagIdsByEntry = tagRefs.groupBy({ it.timeEntryId }, { it.tagId })
        entities.map { entity ->
            val tags = tagIdsByEntry[entity.id].orEmpty().mapNotNull { tagsById[it] }
            entity.toModel(tags)
        }
    }

    override suspend fun loadMonth(organizationId: String, memberId: String, month: YearMonth) {
        val pageSize = 250
        var offset = 0
        val targetStart = month.atDay(1)
        // Tombstoning (SV-020) must be scoped to exactly what was fetched: the union of every
        // returned id, bounded by the tightest [minStart, maxStart] actually observed across all
        // pages of this call. Widening either bound risks deleting a local row the fetch never
        // covered; narrowing risks leaving a server-side deletion stuck locally.
        val allServerIds = mutableListOf<String>()
        var minStart: String? = null
        var maxStart: String? = null
        while (offset < 15_000) {
            val response = remote.getTimeEntries(
                organizationId,
                memberId,
                pageSize,
                offset,
                onlyFullDates = false,
            ).getOrElse {
                Timber.e(it, "Failed loading calendar month %s", month)
                return
            }
            val now = clock.nowMs()
            timeEntryDao.applyServerEntries(
                response.data.map { it.toEntity(updatedAt = now, syncState = SyncState.SYNCED) },
                response.data.associate { entry -> entry.id to entry.tags.map { it.id } },
            )
            allServerIds += response.data.map { it.id }
            response.data.forEach { entry ->
                if (minStart == null || entry.start < minStart!!) minStart = entry.start
                if (maxStart == null || entry.start > maxStart!!) maxStart = entry.start
            }
            val dates = response.data.mapNotNull { entry ->
                runCatching {
                    ZonedDateTime.parse(entry.start, DateTimeFormatter.ISO_DATE_TIME).toLocalDate()
                }.getOrNull()
            }
            if (response.data.isEmpty() || dates.minOrNull()?.isBefore(targetStart) == true) break
            offset += response.data.size
            if (response.data.size < pageSize || offset >= (response.meta?.total ?: Int.MAX_VALUE)) break
        }
        val rangeStart = minStart
        val rangeEnd = maxStart
        if (rangeStart != null && rangeEnd != null) {
            timeEntryDao.tombstoneMissing(organizationId, rangeStart, rangeEnd, allServerIds)
        }
    }

    fun observeActiveEntry(orgId: String): Flow<TimeEntry?> = timeEntryDao.observeActive(orgId).map { entity ->
        entity?.toModel(timeEntryDao.tagIdsFor(entity.id).map { Tag(it) })
    }

    fun observeOutboxCount(): Flow<Int> = outboxDao.observeCount()

    fun observeSyncOperations(orgId: String): Flow<List<SyncOperation>> = outboxDao.observeAll().map { operations ->
        operations.filter { it.organizationId == orgId }.map { op ->
            SyncOperation(
                entryId = op.timeEntryId,
                type = op.opType,
                status = when {
                    op.deadLettered -> EntrySyncStatus.FAILED
                    op.attemptCount > 0 -> EntrySyncStatus.RETRYING
                    else -> EntrySyncStatus.PENDING
                },
                attemptCount = op.attemptCount,
                error = op.lastError,
            )
        }
    }

    /** Pull the full first frame for an org and upsert into Room (last-write-wins). */
    suspend fun refreshAll(organizationId: String, memberId: String): Result<Unit> = try {
        val projects = remote.getProjects(organizationId).getOrThrow()
        val clients = remote.getClients(organizationId).getOrThrow()
        val tasks = remote.getTasks(organizationId).getOrThrow()
        val tags = remote.getTags(organizationId).getOrThrow()
        val entries = remote.getTimeEntries(organizationId, memberId, limit = 250, offset = 0, onlyFullDates = false)
            .getOrThrow().data

        catalogDao.upsertProjects(projects.map { it.toEntity(organizationId) })
        catalogDao.upsertClients(clients.map { it.toEntity(organizationId) })
        catalogDao.upsertTasks(tasks.map { it.toEntity(organizationId) })
        catalogDao.upsertTags(tags.map { it.toEntity(organizationId) })

        // Memberships/organizations cache so auth-adjacent screens can read offline.
        remote.getMyMemberships().getOrNull()?.let { memberships ->
            catalogDao.upsertMemberships(memberships.map { it.toEntity() })
            catalogDao.upsertOrganizations(memberships.map { it.organization.toEntity() })
        }

        val now = clock.nowMs()
        // Single transaction; the pending-edit/soft-delete guard lives in applyServerEntries.
        // (The previous `updatedAt > now` guard was dead code: updatedAt is always stamped in
        // the past, so it never held and pending edits/deletes were silently overwritten.)
        timeEntryDao.applyServerEntries(
            entries.map { it.toEntity(updatedAt = now, syncState = SyncState.SYNCED) },
            entries.associate { entry -> entry.id to entry.tags.map { it.id } },
        )
        // SV-020: propagate server-side deletions. This page's sort order isn't guaranteed, so
        // scope the tombstone strictly to the observed [minStart, maxStart] of what actually came
        // back rather than "0 to now" — that keeps it a safe subset of the real fetch no matter
        // how the server orders results, at the cost of not catching a deletion outside this
        // single page on orgs with more than one page of entries.
        val starts = entries.map { it.start }
        if (starts.isNotEmpty()) {
            timeEntryDao.tombstoneMissing(
                organizationId,
                starts.min(),
                starts.max(),
                entries.map { it.id },
            )
        }
        syncMetaDao.upsert(SyncMetaEntity(organizationId, now))
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "refreshAll failed")
        Result.failure(e)
    }

    // ---- Optimistic writes + outbox enqueue ----

    suspend fun startEntry(
        organizationId: String,
        memberId: String,
        userId: String,
        projectId: String?,
        taskId: String?,
        description: String,
        tagIds: List<String>,
    ): TimeEntry {
        val now = clock.nowMs()
        val localId = "local-" + java.util.UUID.randomUUID().toString()
        val start = nowIso()
        val entry = TimeEntry(
            id = localId, description = description, userId = userId, start = start,
            end = null, duration = null, taskId = taskId, projectId = projectId,
            billable = false, organizationId = organizationId,
        )
        // SV-026: the optimistic Room write and its outbox enqueue must commit atomically, or a
        // crash between them yields an entry Room shows but the outbox never learns to sync (or
        // vice versa).
        database.withTransaction {
            timeEntryDao.upsert(entry.toEntity(updatedAt = now, syncState = SyncState.PENDING))
            timeEntryDao.replaceTagRefs(localId, tagIds)
            outboxDao.insert(
                OutboxEntity(
                    opType = OutboxOpType.START,
                    organizationId = organizationId,
                    timeEntryId = localId,
                    createdAtMs = now,
                    clientId = newClientId(),
                    payloadJson = json.encodeToString(
                        StartPayload(memberId, userId, projectId, taskId, description, tagIds, start = start),
                    ),
                ),
            )
        }
        return entry
    }

    suspend fun stopEntry(entry: TimeEntry, userId: String) {
        val now = clock.nowMs()
        val end = nowIso()
        database.withTransaction {
            timeEntryDao.upsert(entry.copy(end = end).toEntity(updatedAt = now, syncState = SyncState.PENDING))
            outboxDao.insert(
                OutboxEntity(
                    opType = OutboxOpType.STOP,
                    organizationId = entry.organizationId,
                    timeEntryId = entry.id,
                    createdAtMs = now,
                    clientId = newClientId(),
                    payloadJson = json.encodeToString(StopPayload(userId, entry.start, end = end)),
                ),
            )
        }
    }

    suspend fun updateEntry(entry: TimeEntry, tagIds: List<String>) {
        val now = clock.nowMs()
        database.withTransaction {
            timeEntryDao.upsert(entry.toEntity(updatedAt = now, syncState = SyncState.PENDING))
            timeEntryDao.replaceTagRefs(entry.id, tagIds)
            outboxDao.insert(
                OutboxEntity(
                    opType = OutboxOpType.UPDATE,
                    organizationId = entry.organizationId,
                    timeEntryId = entry.id,
                    createdAtMs = now,
                    clientId = newClientId(),
                    payloadJson = json.encodeToString(
                        UpdatePayload(
                            entry.userId,
                            entry.start,
                            entry.end,
                            entry.description,
                            entry.projectId,
                            entry.taskId,
                            entry.billable,
                            tagIds,
                        ),
                    ),
                ),
            )
        }
    }

    suspend fun createCompletedEntry(
        organizationId: String,
        memberId: String,
        userId: String,
        description: String,
        projectId: String?,
        taskId: String?,
        tagIds: List<String>,
        billable: Boolean,
        start: String,
        end: String,
    ): TimeEntry {
        val now = clock.nowMs()
        val localId = "local-create-${java.util.UUID.randomUUID()}"
        val entry = TimeEntry(
            id = localId,
            description = description,
            userId = userId,
            start = start,
            end = end,
            duration = null,
            taskId = taskId,
            projectId = projectId,
            tags = tagIds.map { Tag(it) },
            billable = billable,
            organizationId = organizationId,
        )
        database.withTransaction {
            timeEntryDao.upsert(entry.toEntity(updatedAt = now, syncState = SyncState.PENDING))
            timeEntryDao.replaceTagRefs(localId, tagIds)
            outboxDao.insert(
                OutboxEntity(
                    opType = OutboxOpType.CREATE,
                    organizationId = organizationId,
                    timeEntryId = localId,
                    createdAtMs = now,
                    clientId = newClientId(),
                    payloadJson = json.encodeToString(
                        CreatePayload(
                            memberId,
                            userId,
                            start,
                            end,
                            description,
                            projectId,
                            taskId,
                            billable,
                            tagIds,
                        ),
                    ),
                ),
            )
        }
        return entry
    }

    /**
     * SV-019 step 1 of 2: hide the entry locally right away, without telling the server anything
     * yet. No outbox op is created here, so a caller's undo window (see [TrackingViewModel]) can
     * still cancel the delete for free — there is nothing queued to race against the sync worker.
     * Safe to call for both server-synced and never-synced (`local-`) entries; [commitDelete] (or
     * [undoDelete]) decides what happens once the window closes.
     */
    suspend fun softDeleteLocal(entry: TimeEntry) {
        val now = clock.nowMs()
        timeEntryDao.upsert(entry.toEntity(updatedAt = now, syncState = SyncState.PENDING, pendingDelete = true))
    }

    /**
     * SV-019 step 2 of 2: commit the delete once the undo window has elapsed without an undo.
     *
     * SV-008: for an entry that never reached the server (`local-` id) there is nothing to tell
     * the server to delete — instead, cancel the entry's own queued START/CREATE and any dependent
     * STOP/UPDATE outbox ops (all keyed by the same local id) and drop the Room row outright. If a
     * server DELETE were enqueued here instead, the still-queued START/CREATE would upload first
     * and resurrect the "deleted" entry on the server.
     *
     * For an already-synced entry (server id), the Room row stays soft-deleted (from
     * [softDeleteLocal]) and a DELETE op is enqueued for the sync worker to apply.
     */
    suspend fun commitDelete(entry: TimeEntry) {
        val now = clock.nowMs()
        database.withTransaction {
            if (entry.id.startsWith("local-")) {
                timeEntryDao.deleteById(entry.id)
                outboxDao.deleteByTimeEntryId(entry.id)
            } else {
                outboxDao.insert(
                    OutboxEntity(
                        opType = OutboxOpType.DELETE,
                        organizationId = entry.organizationId,
                        timeEntryId = entry.id,
                        createdAtMs = now,
                        clientId = newClientId(),
                        payloadJson = "{}",
                    ),
                )
            }
        }
    }

    /**
     * Convenience wrapper combining [softDeleteLocal] and an immediate [commitDelete], for
     * non-interactive callers (e.g. [ReviewDayViewModel]) that have no undo affordance and want
     * the previous one-shot delete behaviour.
     */
    suspend fun deleteEntry(entry: TimeEntry) {
        softDeleteLocal(entry)
        commitDelete(entry)
    }

    suspend fun undoDelete(entry: TimeEntry, memberId: String?): Boolean {
        val current = timeEntryDao.getById(entry.id)
        // Window still open: softDeleteLocal ran but commitDelete never enqueued anything (no
        // DELETE op exists for this id yet), so a plain local restore is enough - there is nothing
        // server-facing to cancel. (A never-synced local- entry whose window already closed has no
        // Room row left at all - commitDelete hard-deletes it - so `current` alone rules that out.)
        if (current != null && current.pendingDelete && !outboxDao.hasPendingDelete(entry.id)) {
            database.withTransaction {
                // SV-024: restore to the entry's real sync state, not the PENDING the soft-delete
                // stamped it with - a synced (server-id) row must go back to SYNCED so it isn't
                // silently skipped by applyServerEntries forever with no outbox op to fix it.
                val state = if (entry.id.startsWith("local-")) SyncState.PENDING else SyncState.SYNCED
                timeEntryDao.restoreDeleted(entry.id, state)
            }
            return true
        }
        if (outboxDao.cancelLatestDelete(entry.id) == 0) {
            val end = entry.end ?: return false
            val membership = memberId ?: return false
            val now = clock.nowMs()
            val restored = entry.copy(id = "local-restore-${java.util.UUID.randomUUID()}")
            database.withTransaction {
                timeEntryDao.upsert(restored.toEntity(updatedAt = now, syncState = SyncState.PENDING))
                timeEntryDao.replaceTagRefs(restored.id, entry.tags.map { it.id })
                outboxDao.insert(
                    OutboxEntity(
                        opType = OutboxOpType.CREATE,
                        organizationId = entry.organizationId,
                        timeEntryId = restored.id,
                        createdAtMs = now,
                        clientId = newClientId(),
                        payloadJson = json.encodeToString(
                            CreatePayload(
                                membership, entry.userId, entry.start, end, entry.description.orEmpty(),
                                entry.projectId, entry.taskId, entry.billable, entry.tags.map { it.id },
                            ),
                        ),
                    ),
                )
            }
            return true
        }
        val existing = timeEntryDao.getById(entry.id)
        // SV-024: restore to the entry's real sync state - SYNCED for a server-id row (mirroring
        // the window-still-open branch above), regardless of what a stale local snapshot's
        // syncState column says - never leave a synced entry stuck PENDING with no outbox op.
        val state = if (entry.id.startsWith("local-")) SyncState.PENDING else SyncState.SYNCED
        database.withTransaction {
            if (existing == null) {
                timeEntryDao.upsert(entry.toEntity(updatedAt = clock.nowMs(), syncState = state))
                timeEntryDao.replaceTagRefs(entry.id, entry.tags.map { it.id })
            } else {
                timeEntryDao.restoreDeleted(entry.id, state)
            }
        }
        return true
    }

    suspend fun prepareRetry(entryId: String): Boolean = outboxDao.resetForRetry(entryId) > 0

    /**
     * SV-029: discard a dead-lettered (permanently failed) outbox operation without retrying it.
     * Used when the user acknowledges a failed-sync review item as intentional ("keep as is") -
     * otherwise the op keeps surfacing in Track's Sync center forever with only a re-failing Retry.
     */
    suspend fun discardFailedSync(entryId: String): Boolean = outboxDao.deleteDeadLetteredByEntryId(entryId) > 0

    /** Stable idempotency key for a new outbox operation; persisted on the row (see OutboxEntity). */
    private fun newClientId(): String = java.util.UUID.randomUUID().toString()

    private fun nowIso(): String = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
}

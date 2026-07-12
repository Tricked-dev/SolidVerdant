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
import dev.tricked.solidverdant.data.local.db.SyncState
import dev.tricked.solidverdant.data.local.db.TimeEntryDao
import dev.tricked.solidverdant.data.local.db.TimeEntryEntity
import dev.tricked.solidverdant.data.local.db.toEntity
import dev.tricked.solidverdant.data.local.db.toModel
import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.remote.RemoteDataSource
import dev.tricked.solidverdant.data.remote.TimeEntriesQuery
import dev.tricked.solidverdant.sync.ConflictSnapshot
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

private const val MONTH_PAGE_SIZE = 250
private const val MAX_MONTH_ENTRIES = 15_000

@Singleton
@Suppress("LargeClass")
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
    enum class EntrySyncStatus { SYNCED, PENDING, RETRYING, FAILED, CONFLICT }

    data class SyncConflict(val local: TimeEntry, val server: TimeEntry?, val serverDeleted: Boolean, val localDeleted: Boolean)

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

    fun observeConflicts(organizationId: String): Flow<List<SyncConflict>> = combine(
        timeEntryDao.observeConflicts(organizationId),
        catalogDao.observeTags(organizationId),
        timeEntryDao.observeTagRefs(organizationId),
    ) { entities, tagEntities, tagRefs ->
        val tagsById = tagEntities.associate { it.id to it.toModel() }
        val tagIdsByEntry = tagRefs.groupBy({ it.timeEntryId }, { it.tagId })
        entities.mapNotNull { entity ->
            val localTags = tagIdsByEntry[entity.id].orEmpty().mapNotNull { tagsById[it] }
            val serverJson = entity.conflictServerJson
            val serverDeleted = serverJson == ConflictSnapshot.DELETED_MARKER
            val server = if (serverDeleted || serverJson == null) {
                null
            } else {
                runCatching { json.decodeFromString<TimeEntry>(serverJson) }.getOrNull()
            }
            SyncConflict(
                local = entity.toModel(localTags),
                server = server,
                serverDeleted = serverDeleted,
                localDeleted = entity.pendingDelete,
            )
        }
    }

    @Suppress("LoopWithTooManyJumpStatements")
    override suspend fun loadMonth(organizationId: String, memberId: String, month: YearMonth) {
        val pageSize = MONTH_PAGE_SIZE
        var offset = 0
        val targetStart = month.atDay(1)
        // Tombstoning (SV-020) must be scoped to exactly what was fetched: the union of every
        // returned id, bounded by the tightest [minStart, maxStart] actually observed across all
        // pages of this call. Widening either bound risks deleting a local row the fetch never
        // covered; narrowing risks leaving a server-side deletion stuck locally.
        val allServerIds = mutableListOf<String>()
        var minStart: String? = null
        var maxStart: String? = null
        while (offset < MAX_MONTH_ENTRIES) {
            val response = remote.getTimeEntries(
                TimeEntriesQuery(
                    organizationId = organizationId,
                    memberId = memberId,
                    limit = pageSize,
                    offset = offset,
                    onlyFullDates = false,
                ),
            ).getOrElse {
                Timber.e(it, "Failed loading calendar month %s", month)
                return
            }
            val now = clock.nowMs()
            applyServerEntries(
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

    fun observeSyncOperations(orgId: String): Flow<List<SyncOperation>> = combine(
        outboxDao.observeAll(),
        timeEntryDao.observeConflicts(orgId),
    ) { operations, conflicts ->
        val conflictIds = conflicts.map { it.id }.toSet()
        val queued = operations.filter { it.organizationId == orgId && it.timeEntryId !in conflictIds }.map { op ->
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
        queued + conflicts.map { conflict ->
            SyncOperation(
                entryId = conflict.id,
                type = OutboxOpType.UPDATE,
                status = EntrySyncStatus.CONFLICT,
                attemptCount = 0,
                error = null,
            )
        }
    }

    /** Pull the full first frame for an org and upsert into Room (last-write-wins). */
    suspend fun refreshAll(organizationId: String, memberId: String): Result<Unit> = try {
        val projects = remote.getProjects(organizationId).getOrThrow()
        val clients = remote.getClients(organizationId).getOrThrow()
        val tasks = remote.getTasks(organizationId).getOrThrow()
        val tags = remote.getTags(organizationId).getOrThrow()
        val entries = remote.getTimeEntries(
            TimeEntriesQuery(organizationId, memberId, limit = 250, offset = 0, onlyFullDates = false),
        )
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
        applyServerEntries(
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
        // Stamp the pull-refresh moment without clobbering the push timestamp (a concurrent
        // SyncWorker flush may have written lastPushAtMs); stampFullSync updates that column alone.
        syncMetaDao.stampFullSync(organizationId, now)
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
            val current = timeEntryDao.getById(entry.id)
            if (current?.syncState == SyncState.CONFLICT) {
                // Stopping is the one mutation allowed on a conflicted row: time capture must not
                // be blocked. Keep the row conflicted and preserve the server recovery copy.
                timeEntryDao.upsert(
                    current.copy(end = end, updatedAt = now, pendingDelete = false),
                )
                return@withTransaction
            }
            val base = captureBaseSnapshot(entry.id)
            timeEntryDao.upsert(entry.copy(end = end).toEntity(updatedAt = now, syncState = SyncState.PENDING))
            outboxDao.insert(
                OutboxEntity(
                    opType = OutboxOpType.STOP,
                    organizationId = entry.organizationId,
                    timeEntryId = entry.id,
                    createdAtMs = now,
                    clientId = newClientId(),
                    payloadJson = json.encodeToString(StopPayload(userId, entry.start, end = end)),
                    baseSnapshotJson = base,
                ),
            )
        }
    }

    suspend fun updateEntry(entry: TimeEntry, tagIds: List<String>) {
        val now = clock.nowMs()
        database.withTransaction {
            check(timeEntryDao.getById(entry.id)?.syncState != SyncState.CONFLICT) {
                "Resolve the sync conflict in Review before editing this entry"
            }
            val base = captureBaseSnapshot(entry.id)
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
                    baseSnapshotJson = base,
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
        val current = timeEntryDao.getById(entry.id)
        if (current?.syncState == SyncState.CONFLICT) return
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
            if (timeEntryDao.getById(entry.id)?.syncState == SyncState.CONFLICT) return@withTransaction
            if (entry.id.startsWith("local-")) {
                timeEntryDao.deleteById(entry.id)
                outboxDao.deleteByTimeEntryId(entry.id)
            } else {
                // softDeleteLocal has already flipped this row to PENDING by the time we get here,
                // but the content fields are still the last server-acked ones - captureBaseSnapshot
                // reads them from the entity, not from `entry`, so the PENDING flag flip is
                // irrelevant to what gets snapshotted (SV-027 rule 3).
                val base = captureBaseSnapshot(entry.id)
                outboxDao.insert(
                    OutboxEntity(
                        opType = OutboxOpType.DELETE,
                        organizationId = entry.organizationId,
                        timeEntryId = entry.id,
                        createdAtMs = now,
                        clientId = newClientId(),
                        payloadJson = "{}",
                        baseSnapshotJson = base,
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

    suspend fun resolveKeepMine(conflict: SyncConflict, memberId: String?): Boolean {
        val current = timeEntryDao.getById(conflict.local.id)
        val canResolve = current?.syncState == SyncState.CONFLICT &&
            current.conflictServerJson != null &&
            (!conflict.serverDeleted || memberId != null) &&
            (conflict.serverDeleted || conflict.server != null)
        if (!canResolve) return false
        val local = current!!
        val serverBaseSnapshot = conflict.server?.let { server ->
            json.encodeToString(
                ConflictSnapshot.of(
                    server.start,
                    server.end,
                    server.description,
                    server.projectId,
                    server.taskId,
                    server.billable,
                    server.tags.map { it.id },
                ),
            )
        }
        val now = clock.nowMs()
        database.withTransaction {
            outboxDao.deleteByTimeEntryId(local.id)
            when {
                conflict.serverDeleted -> enqueueRecreate(local, conflict, requireNotNull(memberId), now)
                local.pendingDelete -> enqueueDelete(local, serverBaseSnapshot, now)
                else -> enqueueUpdate(local, conflict, serverBaseSnapshot, now)
            }
        }
        return true
    }

    private suspend fun enqueueRecreate(current: TimeEntryEntity, conflict: SyncConflict, memberId: String, now: Long) {
        timeEntryDao.upsert(
            current.copy(
                syncState = SyncState.PENDING,
                pendingDelete = false,
                conflictServerJson = null,
                updatedAt = now,
            ),
        )
        timeEntryDao.replaceTagRefs(current.id, conflict.local.tags.map { it.id })
        outboxDao.insert(
            OutboxEntity(
                opType = OutboxOpType.CREATE,
                organizationId = current.organizationId,
                timeEntryId = current.id,
                createdAtMs = now,
                clientId = newClientId(),
                payloadJson = json.encodeToString(
                    CreatePayload(
                        memberId,
                        current.userId,
                        current.start,
                        current.end ?: nowIso(),
                        current.description.orEmpty(),
                        current.projectId,
                        current.taskId,
                        current.billable,
                        conflict.local.tags.map { it.id },
                    ),
                ),
            ),
        )
    }

    private suspend fun enqueueDelete(current: TimeEntryEntity, baseSnapshot: String?, now: Long) {
        timeEntryDao.upsert(
            current.copy(syncState = SyncState.PENDING, conflictServerJson = null, updatedAt = now),
        )
        outboxDao.insert(
            OutboxEntity(
                opType = OutboxOpType.DELETE,
                organizationId = current.organizationId,
                timeEntryId = current.id,
                createdAtMs = now,
                clientId = newClientId(),
                payloadJson = "{}",
                baseSnapshotJson = baseSnapshot,
            ),
        )
    }

    private suspend fun enqueueUpdate(current: TimeEntryEntity, conflict: SyncConflict, baseSnapshot: String?, now: Long) {
        timeEntryDao.upsert(
            current.copy(syncState = SyncState.PENDING, conflictServerJson = null, updatedAt = now),
        )
        timeEntryDao.replaceTagRefs(current.id, conflict.local.tags.map { it.id })
        outboxDao.insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = current.organizationId,
                timeEntryId = current.id,
                createdAtMs = now,
                clientId = newClientId(),
                payloadJson = json.encodeToString(
                    UpdatePayload(
                        current.userId,
                        current.start,
                        current.end,
                        current.description,
                        current.projectId,
                        current.taskId,
                        current.billable,
                        conflict.local.tags.map { it.id },
                    ),
                ),
                baseSnapshotJson = baseSnapshot,
            ),
        )
    }

    suspend fun resolveKeepMine(entryId: String, memberId: String?): Boolean {
        return resolveKeepMine(conflictFor(entryId) ?: return false, memberId)
    }

    suspend fun resolveKeepTheirs(conflict: SyncConflict): Boolean {
        val current = timeEntryDao.getById(conflict.local.id)
        val canResolve = current?.syncState == SyncState.CONFLICT &&
            (conflict.server != null || current.conflictServerJson == ConflictSnapshot.DELETED_MARKER)
        if (!canResolve) return false
        val server = conflict.server
        return if (server == null) {
            timeEntryDao.clearTagRefs(current!!.id)
            timeEntryDao.deleteById(current.id)
            true
        } else {
            val now = clock.nowMs()
            database.withTransaction {
                timeEntryDao.upsert(server.toEntity(updatedAt = now, syncState = SyncState.SYNCED))
                timeEntryDao.replaceTagRefs(server.id, server.tags.map { it.id })
                outboxDao.deleteByTimeEntryId(current!!.id)
            }
            true
        }
    }

    suspend fun resolveKeepTheirs(entryId: String): Boolean {
        return resolveKeepTheirs(conflictFor(entryId) ?: return false)
    }

    private suspend fun conflictFor(entryId: String): SyncConflict? {
        val entity = timeEntryDao.getById(entryId)
        return if (entity == null || entity.syncState != SyncState.CONFLICT) {
            null
        } else {
            buildConflict(entity, entryId)
        }
    }

    private suspend fun buildConflict(entity: TimeEntryEntity, entryId: String): SyncConflict? {
        val serverJson = entity.conflictServerJson ?: return null
        val localTags = timeEntryDao.tagIdsFor(entryId).map { Tag(it) }
        val serverDeleted = serverJson == ConflictSnapshot.DELETED_MARKER
        val server = if (serverDeleted) {
            null
        } else {
            runCatching { json.decodeFromString<TimeEntry>(serverJson) }.getOrNull()
        }
        return SyncConflict(
            local = entity.toModel(localTags),
            server = server,
            serverDeleted = serverDeleted,
            localDeleted = entity.pendingDelete,
        )
    }

    private suspend fun applyServerEntries(entries: List<TimeEntryEntity>, tagIdsByEntry: Map<String, List<String>>) {
        val bases = outboxDao.peekAll()
            .filter { it.baseSnapshotJson != null }
            .groupBy { it.timeEntryId }
            .mapValues { (_, ops) -> ops.minBy { it.id }.baseSnapshotJson!! }
        val serverJsonByEntry = entries.associate { entity ->
            val tags = tagIdsByEntry[entity.id].orEmpty().map { Tag(it) }
            entity.id to json.encodeToString(
                TimeEntry(
                    entity.id,
                    entity.description,
                    entity.userId,
                    entity.start,
                    entity.end,
                    entity.duration,
                    entity.taskId,
                    entity.projectId,
                    tags,
                    entity.billable,
                    entity.organizationId,
                ),
            )
        }
        database.withTransaction {
            timeEntryDao.applyServerEntries(
                entries = entries,
                tagIdsByEntry = tagIdsByEntry,
                baseSnapshotsByEntry = bases,
                serverJsonByEntry = serverJsonByEntry,
            )
            // Pull-side conflicts no longer have a meaningful queued write. Clear them in the
            // same transaction so Review immediately becomes the sole recovery surface.
            entries.forEach { entity ->
                if (timeEntryDao.getById(entity.id)?.syncState == SyncState.CONFLICT) {
                    outboxDao.deleteByTimeEntryId(entity.id)
                }
            }
        }
    }

    /**
     * SV-027 base-snapshot capture for a STOP/UPDATE/DELETE enqueue, applied inside the same
     * transaction that enqueues the op, before the local mutation is written. Rules, in order:
     * 1. a queued op for this entry already carries a base -> reuse the oldest such base (an
     *    offline STOP->UPDATE chain shares the pre-stop base);
     * 2. else a queued START/CREATE exists for the entry -> null (born locally, nothing on the
     *    server to diverge from);
     * 3. else -> snapshot the entity's pre-mutation content (the last server-acked content).
     */
    private suspend fun captureBaseSnapshot(entryId: String): String? = outboxDao.oldestBaseSnapshot(entryId)
        ?: if (outboxDao.hasPendingCreateOrStart(entryId)) {
            null
        } else {
            timeEntryDao.getById(entryId)?.let { current ->
                json.encodeToString(
                    ConflictSnapshot.of(
                        start = current.start,
                        end = current.end,
                        description = current.description,
                        projectId = current.projectId,
                        taskId = current.taskId,
                        billable = current.billable,
                        tagIds = timeEntryDao.tagIdsFor(entryId),
                    ),
                )
            }
        }

    /** Stable idempotency key for a new outbox operation; persisted on the row (see OutboxEntity). */
    private fun newClientId(): String = java.util.UUID.randomUUID().toString()

    private fun nowIso(): String = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
}

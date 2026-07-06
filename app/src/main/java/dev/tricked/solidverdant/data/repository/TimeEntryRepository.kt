package dev.tricked.solidverdant.data.repository

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
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.remote.RemoteDataSource
import dev.tricked.solidverdant.sync.StartPayload
import dev.tricked.solidverdant.sync.StopPayload
import dev.tricked.solidverdant.sync.UpdatePayload
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Singleton
class TimeEntryRepository @Inject constructor(
    private val timeEntryDao: TimeEntryDao,
    private val catalogDao: CatalogDao,
    private val outboxDao: OutboxDao,
    private val syncMetaDao: SyncMetaDao,
    private val remote: RemoteDataSource,
    private val clock: Clock,
    private val json: Json
) : TimeEntryReader {
    fun observeProjects(orgId: String): Flow<List<Project>> =
        catalogDao.observeProjects(orgId).map { list -> list.map { it.toModel() } }

    fun observeTasks(orgId: String): Flow<List<Task>> =
        catalogDao.observeTasks(orgId).map { list -> list.map { it.toModel() } }

    fun observeTags(orgId: String): Flow<List<Tag>> =
        catalogDao.observeTags(orgId).map { list -> list.map { it.toModel() } }

    override fun observeTimeEntries(organizationId: String): Flow<List<TimeEntry>> =
        combine(
            timeEntryDao.observeVisibleEntries(organizationId),
            catalogDao.observeTags(organizationId)
        ) { entities, tagEntities ->
            val tagsById = tagEntities.associate { it.id to it.toModel() }
            entities.map { entity ->
                val tags = timeEntryDao.tagIdsFor(entity.id).mapNotNull { tagsById[it] }
                entity.toModel(tags)
            }
        }

    override suspend fun loadMonth(organizationId: String, memberId: String, month: YearMonth) {
        val pageSize = 250
        var offset = 0
        val targetStart = month.atDay(1)
        while (offset < 15_000) {
            val response = remote.getTimeEntries(
                organizationId, memberId, pageSize, offset, onlyFullDates = false
            ).getOrElse {
                Timber.e(it, "Failed loading calendar month %s", month)
                return
            }
            val now = clock.nowMs()
            response.data.forEach { entry ->
                val local = timeEntryDao.getById(entry.id)
                if (local == null || local.syncState != SyncState.PENDING) {
                    timeEntryDao.upsert(entry.toEntity(updatedAt = now, syncState = SyncState.SYNCED))
                    timeEntryDao.replaceTagRefs(entry.id, entry.tags.map { it.id })
                }
            }
            val dates = response.data.mapNotNull { entry ->
                runCatching {
                    ZonedDateTime.parse(entry.start, DateTimeFormatter.ISO_DATE_TIME).toLocalDate()
                }.getOrNull()
            }
            if (response.data.isEmpty() || dates.minOrNull()?.isBefore(targetStart) == true) return
            offset += response.data.size
            if (response.data.size < pageSize || offset >= (response.meta?.total ?: Int.MAX_VALUE)) return
        }
    }

    fun observeActiveEntry(orgId: String): Flow<TimeEntry?> =
        timeEntryDao.observeActive(orgId).map { entity ->
            entity?.toModel(timeEntryDao.tagIdsFor(entity.id).map { Tag(it) })
        }

    fun observeOutboxCount(): Flow<Int> = outboxDao.observeCount()

    /** Pull the full first frame for an org and upsert into Room (last-write-wins). */
    suspend fun refreshAll(organizationId: String, memberId: String): Result<Unit> = try {
        val projects = remote.getProjects(organizationId).getOrThrow()
        val tasks = remote.getTasks(organizationId).getOrThrow()
        val tags = remote.getTags(organizationId).getOrThrow()
        val entries = remote.getTimeEntries(organizationId, memberId, limit = 250, offset = 0, onlyFullDates = false)
            .getOrThrow().data

        catalogDao.upsertProjects(projects.map { it.toEntity(organizationId) })
        catalogDao.upsertTasks(tasks.map { it.toEntity(organizationId) })
        catalogDao.upsertTags(tags.map { it.toEntity(organizationId) })

        // Memberships/organizations cache so auth-adjacent screens can read offline.
        remote.getMyMemberships().getOrNull()?.let { memberships ->
            catalogDao.upsertMemberships(memberships.map { it.toEntity() })
            catalogDao.upsertOrganizations(memberships.map { it.organization.toEntity() })
        }

        val now = clock.nowMs()
        entries.forEach { remoteEntry ->
            val local = timeEntryDao.getById(remoteEntry.id)
            // Last-write-wins: keep a strictly-newer PENDING local edit.
            if (local != null && local.syncState == SyncState.PENDING && local.updatedAt > now) {
                return@forEach
            }
            timeEntryDao.upsert(remoteEntry.toEntity(updatedAt = now, syncState = SyncState.SYNCED))
            timeEntryDao.replaceTagRefs(remoteEntry.id, remoteEntry.tags.map { it.id })
        }
        syncMetaDao.upsert(SyncMetaEntity(organizationId, now))
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "refreshAll failed")
        Result.failure(e)
    }

    // ---- Optimistic writes + outbox enqueue ----

    suspend fun startEntry(
        organizationId: String, memberId: String, userId: String,
        projectId: String?, taskId: String?, description: String, tagIds: List<String>
    ): TimeEntry {
        val now = clock.nowMs()
        val localId = "local-" + java.util.UUID.randomUUID().toString()
        val start = nowIso()
        val entry = TimeEntry(
            id = localId, description = description, userId = userId, start = start,
            end = null, duration = null, taskId = taskId, projectId = projectId,
            billable = false, organizationId = organizationId
        )
        timeEntryDao.upsert(entry.toEntity(updatedAt = now, syncState = SyncState.PENDING))
        timeEntryDao.replaceTagRefs(localId, tagIds)
        outboxDao.insert(
            OutboxEntity(
                opType = OutboxOpType.START, organizationId = organizationId,
                timeEntryId = localId, createdAtMs = now,
                payloadJson = json.encodeToString(
                    StartPayload(memberId, userId, projectId, taskId, description, tagIds)
                )
            )
        )
        return entry
    }

    suspend fun stopEntry(entry: TimeEntry, userId: String) {
        val now = clock.nowMs()
        val end = nowIso()
        timeEntryDao.upsert(entry.copy(end = end).toEntity(updatedAt = now, syncState = SyncState.PENDING))
        outboxDao.insert(
            OutboxEntity(
                opType = OutboxOpType.STOP, organizationId = entry.organizationId,
                timeEntryId = entry.id, createdAtMs = now,
                payloadJson = json.encodeToString(StopPayload(userId, entry.start))
            )
        )
    }

    suspend fun updateEntry(entry: TimeEntry, tagIds: List<String>) {
        val now = clock.nowMs()
        timeEntryDao.upsert(entry.toEntity(updatedAt = now, syncState = SyncState.PENDING))
        timeEntryDao.replaceTagRefs(entry.id, tagIds)
        outboxDao.insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE, organizationId = entry.organizationId,
                timeEntryId = entry.id, createdAtMs = now,
                payloadJson = json.encodeToString(
                    UpdatePayload(entry.userId, entry.start, entry.end, entry.description,
                        entry.projectId, entry.taskId, entry.billable, tagIds)
                )
            )
        )
    }

    suspend fun deleteEntry(entry: TimeEntry) {
        val now = clock.nowMs()
        // If the entry was never synced (still local-*), just drop it; otherwise soft-delete.
        if (entry.id.startsWith("local-")) {
            timeEntryDao.deleteById(entry.id)
        } else {
            timeEntryDao.upsert(entry.toEntity(updatedAt = now, syncState = SyncState.PENDING, pendingDelete = true))
        }
        outboxDao.insert(
            OutboxEntity(
                opType = OutboxOpType.DELETE, organizationId = entry.organizationId,
                timeEntryId = entry.id, createdAtMs = now, payloadJson = "{}"
            )
        )
    }

    private fun nowIso(): String = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
}

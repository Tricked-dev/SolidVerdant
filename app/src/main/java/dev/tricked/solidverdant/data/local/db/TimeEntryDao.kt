/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.sync.ConflictSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

private val conflictJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entries WHERE organizationId = :orgId ORDER BY start DESC")
    fun observeEntries(orgId: String): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entries WHERE organizationId = :orgId AND pendingDelete = 0 ORDER BY start DESC")
    fun observeVisibleEntries(orgId: String): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entries WHERE organizationId = :orgId AND end IS NULL AND pendingDelete = 0 ORDER BY start DESC LIMIT 1")
    fun observeActive(orgId: String): Flow<TimeEntryEntity?>

    @Query("SELECT * FROM time_entries WHERE id = :id")
    suspend fun getById(id: String): TimeEntryEntity?

    @Query("SELECT * FROM time_entries WHERE organizationId = :orgId AND syncState = 'CONFLICT' ORDER BY start DESC")
    fun observeConflicts(orgId: String): Flow<List<TimeEntryEntity>>

    @Upsert
    suspend fun upsert(entry: TimeEntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<TimeEntryEntity>)

    @Query("DELETE FROM time_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE time_entries SET pendingDelete = 0, syncState = :syncState WHERE id = :id")
    suspend fun restoreDeleted(id: String, syncState: SyncState)

    @Query("UPDATE time_entries SET id = :newId WHERE id = :oldId")
    suspend fun updateId(oldId: String, newId: String)

    @Transaction
    suspend fun rekey(oldId: String, newId: String) {
        updateId(oldId, newId)
        updateTagRefEntryId(oldId, newId)
    }

    @Query("SELECT tagId FROM time_entry_tag_cross_ref WHERE timeEntryId = :entryId")
    suspend fun tagIdsFor(entryId: String): List<String>

    /**
     * All tag refs for an organization's entries in one reactive query. The per-entry
     * [tagIdsFor] inside observeTimeEntries was an N+1: with 250 cached entries every Room
     * invalidation re-ran 250 point queries (simpleperf showed tagIdsFor alone at ~9% of total
     * CPU during history scrolling).
     */
    @Query(
        "SELECT r.timeEntryId AS timeEntryId, r.tagId AS tagId FROM time_entry_tag_cross_ref r " +
            "INNER JOIN time_entries e ON e.id = r.timeEntryId WHERE e.organizationId = :orgId",
    )
    fun observeTagRefs(orgId: String): Flow<List<TimeEntryTagRef>>

    @Query("DELETE FROM time_entry_tag_cross_ref WHERE timeEntryId = :entryId")
    suspend fun clearTagRefs(entryId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTagRefs(refs: List<TimeEntryTagCrossRef>)

    @Query("UPDATE time_entry_tag_cross_ref SET timeEntryId = :newId WHERE timeEntryId = :oldId")
    suspend fun updateTagRefEntryId(oldId: String, newId: String)

    @Transaction
    suspend fun replaceTagRefs(entryId: String, tagIds: List<String>) {
        clearTagRefs(entryId)
        insertTagRefs(tagIds.map { TimeEntryTagCrossRef(entryId, it) })
    }

    /**
     * Apply a page of server entries in ONE transaction. Per-entry upsert+replaceTagRefs used to
     * produce 2-3 transactions per entry (500+ for a 250-entry refresh), and every transaction
     * fires Room's invalidation tracker — re-running every observer (full entry list, tag refs,
     * catalog) and the whole downstream UI pipeline per write. One transaction = one invalidation.
     *
     * Never lets a server pull clobber an unsynced local edit or a pending soft-delete.
     */
    @Transaction
    suspend fun applyServerEntries(
        entries: List<TimeEntryEntity>,
        tagIdsByEntry: Map<String, List<String>>,
        baseSnapshotsByEntry: Map<String, String> = emptyMap(),
        serverJsonByEntry: Map<String, String> = emptyMap(),
    ) {
        entries.forEach { entity ->
            val local = getById(entity.id)
            val tagIds = tagIdsByEntry[entity.id].orEmpty()
            val serverJson = serverJsonByEntry[entity.id] ?: serverJson(entity, tagIds)
            if (local?.syncState == SyncState.CONFLICT) {
                // An unresolved conflict owns the local row. Pulls may refresh only "theirs";
                // replacing the row here would destroy the recovery copy shown in Review.
                if (!sameServerSnapshot(local.conflictServerJson, entity, tagIds)) {
                    upsert(local.copy(conflictServerJson = serverJson))
                }
                return@forEach
            }
            if (local != null && (local.syncState == SyncState.PENDING || local.pendingDelete)) {
                val base = baseSnapshotsByEntry[entity.id]
                if (base == null) return@forEach
                val baseSnapshot = runCatching { conflictJson.decodeFromString<ConflictSnapshot>(base) }.getOrNull()
                val serverSnapshot = ConflictSnapshot.of(
                    start = entity.start,
                    end = entity.end,
                    description = entity.description,
                    projectId = entity.projectId,
                    taskId = entity.taskId,
                    billable = entity.billable,
                    tagIds = tagIds,
                )
                if (baseSnapshot?.matches(serverSnapshot) == true) return@forEach
                upsert(local.copy(syncState = SyncState.CONFLICT, conflictServerJson = serverJson))
                return@forEach
            }
            upsert(entity)
            replaceTagRefs(entity.id, tagIds)
        }
    }

    private fun serverJson(entity: TimeEntryEntity, tagIds: List<String>): String = conflictJson.encodeToString(
        TimeEntry(
            id = entity.id,
            description = entity.description,
            userId = entity.userId,
            start = entity.start,
            end = entity.end,
            duration = entity.duration,
            taskId = entity.taskId,
            projectId = entity.projectId,
            tags = tagIds.map { dev.tricked.solidverdant.data.model.Tag(it) },
            billable = entity.billable,
            organizationId = entity.organizationId,
        ),
    )

    private fun sameServerSnapshot(existingJson: String?, entity: TimeEntryEntity, tagIds: List<String>): Boolean {
        val previous = existingJson
            ?.let { runCatching { conflictJson.decodeFromString<TimeEntry>(it) }.getOrNull() }
        return previous?.let {
            ConflictSnapshot.of(
                it.start,
                it.end,
                it.description,
                it.projectId,
                it.taskId,
                it.billable,
                it.tags.map { tag -> tag.id },
            ).matches(
                ConflictSnapshot.of(
                    entity.start,
                    entity.end,
                    entity.description,
                    entity.projectId,
                    entity.taskId,
                    entity.billable,
                    tagIds,
                ),
            )
        } ?: false
    }

    /**
     * IDs of local rows that must be tombstoned after fetching a bounded server page (SV-020):
     * rows that are `SYNCED` (never a locally-unsynced edit), have no `pendingDelete` flag, have no
     * outbox operation still queued against them (never clobber an unsynced local mutation), fall
     * within the fetched `[rangeStart, rangeEnd]` window (by `start`, inclusive), and whose id is
     * absent from the ids the server just returned for that same window. Callers must derive
     * [rangeStart]/[rangeEnd] from the actual bounds of the page(s) fetched — never a wider window
     * than what was actually pulled, or an entry outside the fetch would be wrongly deleted.
     */
    @Query(
        "SELECT id FROM time_entries WHERE organizationId = :orgId AND syncState = 'SYNCED' " +
            "AND pendingDelete = 0 AND start >= :rangeStart AND start <= :rangeEnd " +
            "AND id NOT IN (:serverIds) " +
            "AND id NOT IN (SELECT timeEntryId FROM outbox)",
    )
    suspend fun findTombstoneCandidates(orgId: String, rangeStart: String, rangeEnd: String, serverIds: List<String>): List<String>

    @Query("DELETE FROM time_entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /**
     * Remove local rows that the server no longer has, scoped to a known fetched window
     * (SV-020: server-side deletions never propagated locally before this). See
     * [findTombstoneCandidates] for exactly which rows are eligible; entries outside
     * [rangeStart, rangeEnd], still pending, or mid-flight in the outbox are never touched.
     */
    @Transaction
    suspend fun tombstoneMissing(orgId: String, rangeStart: String, rangeEnd: String, serverIds: List<String>) {
        // SQLite caps bound-variable count; the id lists here are page-sized (<= a few hundred)
        // so a single IN-clause is safe without chunking.
        val toDelete = findTombstoneCandidates(orgId, rangeStart, rangeEnd, serverIds)
        if (toDelete.isNotEmpty()) deleteByIds(toDelete)
    }
}

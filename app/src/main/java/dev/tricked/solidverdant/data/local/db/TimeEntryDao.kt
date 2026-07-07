package dev.tricked.solidverdant.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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
            "INNER JOIN time_entries e ON e.id = r.timeEntryId WHERE e.organizationId = :orgId"
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
    suspend fun applyServerEntries(entries: List<TimeEntryEntity>, tagIdsByEntry: Map<String, List<String>>) {
        entries.forEach { entity ->
            val local = getById(entity.id)
            if (local != null && (local.syncState == SyncState.PENDING || local.pendingDelete)) {
                return@forEach
            }
            upsert(entity)
            replaceTagRefs(entity.id, tagIdsByEntry[entity.id].orEmpty())
        }
    }
}

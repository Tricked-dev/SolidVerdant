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

    @Query("UPDATE time_entries SET id = :newId WHERE id = :oldId")
    suspend fun updateId(oldId: String, newId: String)

    @Transaction
    suspend fun rekey(oldId: String, newId: String) {
        updateId(oldId, newId)
        updateTagRefEntryId(oldId, newId)
    }

    @Query("SELECT tagId FROM time_entry_tag_cross_ref WHERE timeEntryId = :entryId")
    suspend fun tagIdsFor(entryId: String): List<String>

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
}

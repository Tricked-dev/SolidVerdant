package dev.tricked.solidverdant.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM outbox ORDER BY id ASC")
    suspend fun peekAll(): List<OutboxEntity>

    @Query("SELECT * FROM outbox ORDER BY id ASC")
    fun observeAll(): Flow<List<OutboxEntity>>

    @Insert
    suspend fun insert(op: OutboxEntity): Long

    @Delete
    suspend fun delete(op: OutboxEntity)

    @Update
    suspend fun update(op: OutboxEntity)

    @Query("UPDATE outbox SET timeEntryId = :newId WHERE timeEntryId = :oldId")
    suspend fun rekeyReferences(oldId: String, newId: String)

    @Query("DELETE FROM outbox WHERE id = (SELECT MAX(id) FROM outbox WHERE timeEntryId = :entryId AND opType = 'DELETE')")
    suspend fun cancelLatestDelete(entryId: String): Int

    @Query("UPDATE outbox SET attemptCount = 0, lastError = NULL WHERE timeEntryId = :entryId")
    suspend fun resetForRetry(entryId: String): Int
}

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

    /**
     * Operations still eligible for syncing, oldest first. Dead-lettered (permanently failed)
     * operations are excluded so they are never re-attempted by the background worker; they
     * remain in the table for user-visible retry.
     */
    @Query("SELECT * FROM outbox WHERE deadLettered = 0 ORDER BY id ASC")
    suspend fun peekPending(): List<OutboxEntity>

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

    @Query("UPDATE outbox SET attemptCount = 0, lastError = NULL, deadLettered = 0 WHERE timeEntryId = :entryId")
    suspend fun resetForRetry(entryId: String): Int

    /**
     * Mark every non-terminal operation for an entry as dead-lettered. Used to cascade a
     * permanently-failed CREATE/START to its dependent STOP/UPDATE/DELETE ops, which reference the
     * never-rekeyed `local-` id and can never succeed.
     */
    @Query("UPDATE outbox SET deadLettered = 1, lastError = :error WHERE timeEntryId = :entryId AND deadLettered = 0")
    suspend fun deadLetterByEntryId(entryId: String, error: String): Int
}

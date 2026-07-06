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

    @Insert
    suspend fun insert(op: OutboxEntity): Long

    @Delete
    suspend fun delete(op: OutboxEntity)

    @Update
    suspend fun update(op: OutboxEntity)

    @Query("UPDATE outbox SET timeEntryId = :newId WHERE timeEntryId = :oldId")
    suspend fun rekeyReferences(oldId: String, newId: String)
}

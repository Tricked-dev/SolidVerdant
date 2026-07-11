/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

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

    /**
     * Whether a DELETE op is currently queued for an entry. Used to distinguish, in
     * [dev.tricked.solidverdant.data.repository.TimeEntryRepository.undoDelete], an undo tapped
     * while the SV-019 undo window is still open (soft-deleted locally, nothing enqueued yet) from
     * one tapped after the window closed and a server-facing op already exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM outbox WHERE timeEntryId = :entryId AND opType = 'DELETE')")
    suspend fun hasPendingDelete(entryId: String): Boolean

    @Query("UPDATE outbox SET attemptCount = 0, lastError = NULL, deadLettered = 0 WHERE timeEntryId = :entryId")
    suspend fun resetForRetry(entryId: String): Int

    /**
     * Mark every non-terminal operation for an entry as dead-lettered. Used to cascade a
     * permanently-failed CREATE/START to its dependent STOP/UPDATE/DELETE ops, which reference the
     * never-rekeyed `local-` id and can never succeed.
     */
    @Query("UPDATE outbox SET deadLettered = 1, lastError = :error WHERE timeEntryId = :entryId AND deadLettered = 0")
    suspend fun deadLetterByEntryId(entryId: String, error: String): Int

    /**
     * Re-read a single op by id immediately before processing it. Used by the sync drain to avoid
     * acting on a stale in-memory snapshot after an earlier op in the same run rekeyed this op's
     * `timeEntryId` (local- id -> server id) via [rekeyReferences].
     */
    @Query("SELECT * FROM outbox WHERE id = :id")
    suspend fun getById(id: Long): OutboxEntity?

    /**
     * Whether a newer (higher id), still-eligible op exists for the same entry. Used to detect that
     * a just-revived dead-lettered UPDATE has been superseded by later local state (a subsequent
     * UPDATE/STOP/DELETE that already synced or is still queued) and should be dropped rather than
     * reverting the entry to older data.
     */
    @Query(
        "SELECT COUNT(*) FROM outbox WHERE timeEntryId = :entryId AND id > :afterId AND deadLettered = 0",
    )
    suspend fun countNewerPending(entryId: String, afterId: Long): Int

    /**
     * Discard every queued operation for an entry that never reached the server (still on its
     * `local-` id). Used when deleting a never-synced entry (SV-008): the entry's own
     * START/CREATE, plus any dependent STOP/UPDATE, must be cancelled outright rather than
     * followed by a server DELETE, since the server has never heard of this entry.
     */
    @Query("DELETE FROM outbox WHERE timeEntryId = :entryId")
    suspend fun deleteByTimeEntryId(entryId: String)

    /**
     * Discard a dead-lettered (permanently failed) operation for an entry. Used when the user
     * acknowledges a failed-sync review item as intentional (SV-029, "keep as is"): the op would
     * otherwise keep surfacing in Track's Sync center forever with only a re-failing Retry.
     */
    @Query("DELETE FROM outbox WHERE timeEntryId = :entryId AND deadLettered = 1")
    suspend fun deleteDeadLetteredByEntryId(entryId: String): Int
}

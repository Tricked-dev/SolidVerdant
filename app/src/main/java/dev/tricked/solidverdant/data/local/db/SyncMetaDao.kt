/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta WHERE organizationId = :orgId")
    suspend fun get(orgId: String): SyncMetaEntity?

    /** Reactive freshness for an org, for the sync-center screen (#33). Emits NULL until first sync. */
    @Query("SELECT * FROM sync_meta WHERE organizationId = :orgId")
    fun observe(orgId: String): Flow<SyncMetaEntity?>

    @Upsert
    suspend fun upsert(meta: SyncMetaEntity)

    /**
     * Ensure a row exists for [orgId] without touching any freshness column of an existing row.
     * `INSERT OR IGNORE` seeds a sentinel pull-timestamp of 0 and a NULL push-timestamp only when
     * the row is absent, so the targeted `UPDATE`s below can update one column in isolation.
     */
    @Query("INSERT OR IGNORE INTO sync_meta (organizationId, lastFullSyncAtMs) VALUES (:orgId, 0)")
    suspend fun ensureRow(orgId: String)

    @Query("UPDATE sync_meta SET lastFullSyncAtMs = :ts WHERE organizationId = :orgId")
    suspend fun updateFullSyncAt(orgId: String, ts: Long)

    @Query("UPDATE sync_meta SET lastPushAtMs = :ts WHERE organizationId = :orgId")
    suspend fun updatePushAt(orgId: String, ts: Long)

    /**
     * Stamp the last full PULL refresh moment without clobbering [SyncMetaEntity.lastPushAtMs].
     * Insert-if-absent then a single-column UPDATE guarantees the push timestamp is preserved.
     */
    @Transaction
    suspend fun stampFullSync(orgId: String, ts: Long) {
        ensureRow(orgId)
        updateFullSyncAt(orgId, ts)
    }

    /**
     * Stamp the last successful PUSH moment without clobbering [SyncMetaEntity.lastFullSyncAtMs].
     * Insert-if-absent then a single-column UPDATE guarantees the pull timestamp is preserved.
     */
    @Transaction
    suspend fun stampPush(orgId: String, ts: Long) {
        ensureRow(orgId)
        updatePushAt(orgId, ts)
    }
}

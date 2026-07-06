package dev.tricked.solidverdant.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta WHERE organizationId = :orgId")
    suspend fun get(orgId: String): SyncMetaEntity?

    @Upsert
    suspend fun upsert(meta: SyncMetaEntity)
}

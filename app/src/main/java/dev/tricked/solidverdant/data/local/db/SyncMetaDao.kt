/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

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

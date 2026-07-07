/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Access to Time Inbox dismissal decisions (inbox_dismissals). Fleshed out by the Inbox feature
 * agent; the accessor set here is the shared seam.
 */
@Dao
interface InboxDismissalDao {
    @Upsert suspend fun upsert(dismissal: InboxDismissalEntity)

    @Query("SELECT * FROM inbox_dismissals WHERE organizationId = :orgId")
    fun observeDismissals(orgId: String): Flow<List<InboxDismissalEntity>>

    @Query("SELECT issueKey FROM inbox_dismissals WHERE organizationId = :orgId")
    fun observeDismissedKeys(orgId: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM inbox_dismissals WHERE organizationId = :orgId")
    fun observeDismissedCount(orgId: String): Flow<Int>

    @Query("DELETE FROM inbox_dismissals WHERE issueKey = :issueKey")
    suspend fun deleteByKey(issueKey: String)

    @Query("DELETE FROM inbox_dismissals WHERE organizationId = :orgId")
    suspend fun clearForOrg(orgId: String)

    @Query("DELETE FROM inbox_dismissals")
    suspend fun clear()
}

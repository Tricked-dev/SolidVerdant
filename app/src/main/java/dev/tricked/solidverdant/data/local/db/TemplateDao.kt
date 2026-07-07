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
 * Access to reusable entry templates / favorites (entry_templates). Fleshed out by the templates
 * feature agent; the accessor set here is the shared seam.
 */
@Dao
interface TemplateDao {
    @Upsert suspend fun upsert(template: TemplateEntity)

    @Upsert suspend fun upsertAll(templates: List<TemplateEntity>)

    @Query(
        "SELECT * FROM entry_templates WHERE organizationId = :orgId " +
            "ORDER BY isFavorite DESC, sortOrder ASC, createdAtMs ASC",
    )
    fun observeTemplates(orgId: String): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM entry_templates WHERE id = :id")
    suspend fun getById(id: String): TemplateEntity?

    @Query("DELETE FROM entry_templates WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM entry_templates WHERE organizationId = :orgId")
    suspend fun clearForOrg(orgId: String)

    @Query("DELETE FROM entry_templates")
    suspend fun clear()
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.repository

import dev.tricked.solidverdant.data.local.db.TemplateDao
import dev.tricked.solidverdant.data.local.db.TemplateEntity
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A reusable entry template / favorite (gap analysis #1, #9, #81). Local, account/organization
 * scoped reference: it stores catalogue IDs plus locally owned fields (label, pinned order) and
 * never copies server objects. [tagIds] is decoded from the serialized [TemplateEntity.tagIds].
 */
data class EntryTemplate(
    val id: String,
    val organizationId: String,
    val name: String?,
    val projectId: String?,
    val taskId: String?,
    val description: String?,
    val tagIds: List<String>,
    val billable: Boolean,
    val isFavorite: Boolean,
    val sortOrder: Int,
    val createdAtMs: Long,
)

/**
 * The foundation [TemplateEntity.tagIds] column is a plain non-null String (empty = none); there is
 * no Room converter for lists. This codec is the single place that (de)serializes it, so mapping
 * stays consistent and testable.
 */
object TemplateTagCodec {
    fun encode(tagIds: List<String>): String = tagIds.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")

    fun decode(raw: String): List<String> = if (raw.isBlank()) {
        emptyList()
    } else {
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

fun TemplateEntity.toModel(): EntryTemplate = EntryTemplate(
    id = id,
    organizationId = organizationId,
    name = name,
    projectId = projectId,
    taskId = taskId,
    description = description,
    tagIds = TemplateTagCodec.decode(tagIds),
    billable = billable,
    isFavorite = isFavorite,
    sortOrder = sortOrder,
    createdAtMs = createdAtMs,
)

fun EntryTemplate.toEntity(): TemplateEntity = TemplateEntity(
    id = id,
    organizationId = organizationId,
    name = name,
    projectId = projectId,
    taskId = taskId,
    description = description,
    tagIds = TemplateTagCodec.encode(tagIds),
    billable = billable,
    isFavorite = isFavorite,
    sortOrder = sortOrder,
    createdAtMs = createdAtMs,
)

/**
 * Room-backed store for favorites & templates. Everything works offline: reads and writes go
 * straight to the foundation [TemplateDao] / entry_templates table, which is not cleared on logout
 * (favorites survive per-account, matching gap analysis retention #787).
 */
@Singleton
class TemplateRepository @Inject constructor(private val templateDao: TemplateDao, private val clock: Clock) {
    fun observeTemplates(organizationId: String): Flow<List<EntryTemplate>> =
        templateDao.observeTemplates(organizationId).map { list -> list.map { it.toModel() } }

    suspend fun getTemplate(id: String): EntryTemplate? = templateDao.getById(id)?.toModel()

    /**
     * Create a new template appended after existing ones (so it does not jump ahead of the user's
     * pinned order). Blank name/description collapse to null.
     */
    suspend fun createTemplate(
        organizationId: String,
        name: String?,
        projectId: String?,
        taskId: String?,
        description: String?,
        tagIds: List<String>,
        billable: Boolean,
        isFavorite: Boolean,
    ): EntryTemplate {
        val existing = templateDao.observeTemplates(organizationId).first()
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val template = EntryTemplate(
            id = "template-" + UUID.randomUUID().toString(),
            organizationId = organizationId,
            name = name?.trim()?.takeIf { it.isNotEmpty() },
            projectId = projectId,
            taskId = taskId,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            tagIds = tagIds,
            billable = billable,
            isFavorite = isFavorite,
            sortOrder = nextOrder,
            createdAtMs = clock.nowMs(),
        )
        templateDao.upsert(template.toEntity())
        return template
    }

    /** Persist edits to an existing template, keeping its id/order/creation time. */
    suspend fun updateTemplate(template: EntryTemplate) {
        val normalized = template.copy(
            name = template.name?.trim()?.takeIf { it.isNotEmpty() },
            description = template.description?.trim()?.takeIf { it.isNotEmpty() },
        )
        templateDao.upsert(normalized.toEntity())
    }

    suspend fun deleteTemplate(id: String) {
        templateDao.deleteById(id)
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        val current = templateDao.getById(id) ?: return
        templateDao.upsert(current.copy(isFavorite = favorite))
    }

    /**
     * Persist an explicit display order by assigning [EntryTemplate.sortOrder] = index. Favorites
     * are expected to sort ahead of the rest (see the DAO query), so callers pass a list where
     * favorites are already contiguous at the top.
     */
    suspend fun persistOrder(templates: List<EntryTemplate>) {
        if (templates.isEmpty()) return
        templateDao.upsertAll(
            templates.mapIndexed { index, template -> template.copy(sortOrder = index).toEntity() },
        )
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.repository

import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.db.TemplateDao
import dev.tricked.solidverdant.data.local.db.TemplateEntity
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

/**
 * Owner columns are infrastructure, not part of the [EntryTemplate] product model, so they are
 * passed in explicitly by the repository (resolved from the current account) rather than carried on
 * the model. Only the owning account can see or edit a template, so re-stamping the current owner on
 * every write is safe and keeps the row scoped to its account.
 */
fun EntryTemplate.toEntity(ownerEndpoint: String? = null, ownerUserId: String? = null): TemplateEntity = TemplateEntity(
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
    ownerEndpoint = ownerEndpoint,
    ownerUserId = ownerUserId,
)

/**
 * Room-backed store for favorites & templates. Everything works offline: reads and writes go
 * straight to the foundation [TemplateDao] / entry_templates table, which is not cleared on logout
 * (favorites survive per-account, matching gap analysis retention #787).
 */
@Singleton
class TemplateRepository @Inject constructor(
    private val templateDao: TemplateDao,
    private val clock: Clock,
    private val authDataStore: AuthDataStore,
    private val settingsDataStore: SettingsDataStore,
) {
    /** Current account = (server endpoint, Solidtime user id). Null userId = no account signed in. */
    private fun currentUserId(): String? = settingsDataStore.getCachedAuth()?.user?.id

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTemplates(organizationId: String): Flow<List<EntryTemplate>> = authDataStore.endpoint.flatMapLatest { endpoint ->
        val userId = currentUserId()
        if (userId == null) {
            flowOf(emptyList())
        } else {
            templateDao.observeTemplates(organizationId, endpoint, userId)
                .map { list -> list.map { it.toModel() } }
        }
    }

    suspend fun getTemplate(id: String): EntryTemplate? = templateDao.getById(id)?.toModel()

    /**
     * Stamp the current account onto legacy (NULL-owner) templates. Called once when the auth cache
     * is written at login. Returns rows claimed; a second claim by a different account is a no-op.
     */
    suspend fun claimUnowned(endpoint: String, userId: String): Int = templateDao.claimUnowned(endpoint, userId)

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
        val ownerEndpoint = authDataStore.endpoint.first()
        val ownerUserId = currentUserId()
        val existing = ownerUserId?.let {
            templateDao.observeTemplates(organizationId, ownerEndpoint, it).first()
        } ?: emptyList()
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
        templateDao.upsert(template.toEntity(ownerEndpoint, ownerUserId))
        return template
    }

    /** Persist edits to an existing template, keeping its id/order/creation time. */
    suspend fun updateTemplate(template: EntryTemplate) {
        val normalized = template.copy(
            name = template.name?.trim()?.takeIf { it.isNotEmpty() },
            description = template.description?.trim()?.takeIf { it.isNotEmpty() },
        )
        // Only the owning account can reach this template, so re-stamping the current owner keeps
        // ownership intact (the model carries no owner columns to preserve).
        templateDao.upsert(normalized.toEntity(authDataStore.endpoint.first(), currentUserId()))
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
        // These map from the owner-less model, so re-stamp the current account (only the owner can
        // reach these rows) to avoid nulling the owner columns on reorder.
        val ownerEndpoint = authDataStore.endpoint.first()
        val ownerUserId = currentUserId()
        templateDao.upsertAll(
            templates.mapIndexed { index, template ->
                template.copy(sortOrder = index).toEntity(ownerEndpoint, ownerUserId)
            },
        )
    }
}

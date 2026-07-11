/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SyncState { SYNCED, PENDING }

@Entity(
    tableName = "time_entries",
    indices = [Index("organizationId"), Index("start")],
)
data class TimeEntryEntity(
    @PrimaryKey val id: String,
    val description: String?,
    val userId: String,
    val start: String,
    val end: String?,
    val duration: Int?,
    val taskId: String?,
    val projectId: String?,
    val billable: Boolean,
    val organizationId: String,
    val updatedAt: Long,
    val syncState: SyncState,
    val pendingDelete: Boolean,
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val clientId: String?,
    val isArchived: Boolean,
    val billableRate: Int?,
    val isBillable: Boolean,
    val estimatedTime: Int?,
    val spentTime: Int,
    val isPublic: Boolean,
    val organizationId: String,
)

@Entity(tableName = "clients", indices = [Index("organizationId")])
data class ClientEntity(@PrimaryKey val id: String, val name: String, val isArchived: Boolean, val organizationId: String)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isDone: Boolean,
    val projectId: String,
    val estimatedTime: Int?,
    val spentTime: Int,
    val createdAt: String,
    val updatedAt: String,
    val organizationId: String,
)

@Entity(tableName = "tags")
data class TagEntity(@PrimaryKey val id: String, val name: String, val organizationId: String)

@Entity(tableName = "organizations")
data class OrganizationEntity(@PrimaryKey val id: String, val name: String, val currency: String)

@Entity(tableName = "memberships")
data class MembershipEntity(@PrimaryKey val id: String, val role: String, val organizationId: String)

@Entity(tableName = "time_entry_tag_cross_ref", primaryKeys = ["timeEntryId", "tagId"])
data class TimeEntryTagCrossRef(val timeEntryId: String, val tagId: String)

/** Projection row for [TimeEntryDao.observeTagRefs]; not a table. */
data class TimeEntryTagRef(val timeEntryId: String, val tagId: String)

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(@PrimaryKey val organizationId: String, val lastFullSyncAtMs: Long)

/**
 * Reusable entry template / favorite (gap analysis #9, #81). Account/organization scoped, local
 * only — a template references server catalogue IDs but never copies server objects. [tagIds] is a
 * serialized list of tag IDs (comma-separated, empty string when none). [name] is an optional
 * user-facing label; [description] is optional local work content and receives the same protection
 * as cached entries.
 */
@Entity(tableName = "entry_templates", indices = [Index("organizationId")])
data class TemplateEntity(
    @PrimaryKey val id: String,
    val organizationId: String,
    val name: String?,
    val projectId: String?,
    val taskId: String?,
    val description: String?,
    val tagIds: String,
    val billable: Boolean,
    val isFavorite: Boolean,
    val sortOrder: Int,
    val createdAtMs: Long,
)

/**
 * A user decision to dismiss a Time Inbox check (gap analysis #17, #76). [issueKey] is derived from
 * the factual subject and rule version (never list position) so dismissals remain stable and can be
 * invalidated when the underlying data changes.
 */
@Entity(tableName = "inbox_dismissals", indices = [Index("organizationId")])
data class InboxDismissalEntity(@PrimaryKey val issueKey: String, val organizationId: String, val dismissedAtMs: Long)

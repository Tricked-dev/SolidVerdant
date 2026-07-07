package dev.tricked.solidverdant.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SyncState { SYNCED, PENDING }

@Entity(
    tableName = "time_entries",
    indices = [Index("organizationId"), Index("start")]
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
    val pendingDelete: Boolean
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
    val organizationId: String
)

@Entity(tableName = "clients", indices = [Index("organizationId")])
data class ClientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isArchived: Boolean,
    val organizationId: String,
)

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
    val organizationId: String
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val organizationId: String
)

@Entity(tableName = "organizations")
data class OrganizationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String
)

@Entity(tableName = "memberships")
data class MembershipEntity(
    @PrimaryKey val id: String,
    val role: String,
    val organizationId: String
)

@Entity(tableName = "time_entry_tag_cross_ref", primaryKeys = ["timeEntryId", "tagId"])
data class TimeEntryTagCrossRef(
    val timeEntryId: String,
    val tagId: String
)

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val organizationId: String,
    val lastFullSyncAtMs: Long
)

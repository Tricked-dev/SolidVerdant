package dev.tricked.solidverdant.data.local.db

import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Organization
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry

fun TimeEntry.toEntity(
    updatedAt: Long,
    syncState: SyncState = SyncState.SYNCED,
    pendingDelete: Boolean = false
): TimeEntryEntity = TimeEntryEntity(
    id = id, description = description, userId = userId, start = start, end = end,
    duration = duration, taskId = taskId, projectId = projectId, billable = billable,
    organizationId = organizationId, updatedAt = updatedAt,
    syncState = syncState, pendingDelete = pendingDelete
)

fun TimeEntryEntity.toModel(tags: List<Tag>): TimeEntry = TimeEntry(
    id = id, description = description, userId = userId, start = start, end = end,
    duration = duration, taskId = taskId, projectId = projectId, tags = tags,
    billable = billable, organizationId = organizationId
)

fun Project.toEntity(orgId: String) = ProjectEntity(
    id = id, name = name, color = color, clientId = clientId, isArchived = isArchived,
    billableRate = billableRate, isBillable = isBillable, estimatedTime = estimatedTime,
    spentTime = spentTime, isPublic = isPublic, organizationId = orgId
)

fun ProjectEntity.toModel() = Project(
    id = id, name = name, color = color, clientId = clientId, isArchived = isArchived,
    billableRate = billableRate, isBillable = isBillable, estimatedTime = estimatedTime,
    spentTime = spentTime, isPublic = isPublic
)

fun Task.toEntity(orgId: String) = TaskEntity(
    id = id, name = name, isDone = isDone, projectId = projectId,
    estimatedTime = estimatedTime, spentTime = spentTime,
    createdAt = createdAt, updatedAt = updatedAt, organizationId = orgId
)

fun TaskEntity.toModel() = Task(
    id = id, name = name, isDone = isDone, projectId = projectId,
    estimatedTime = estimatedTime, spentTime = spentTime,
    createdAt = createdAt, updatedAt = updatedAt
)

fun Tag.toEntity(orgId: String) = TagEntity(id = id, name = name, organizationId = orgId)
fun TagEntity.toModel() = Tag(id = id, name = name)

fun Organization.toEntity() = OrganizationEntity(id = id, name = name, currency = currency)
fun Membership.toEntity() = MembershipEntity(id = id, role = role, organizationId = organization.id)

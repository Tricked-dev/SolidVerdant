package dev.tricked.solidverdant.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Time entry representing a tracking session
 */
@Serializable
data class TimeEntry(
    val id: String,
    val description: String? = null,
    @SerialName("user_id")
    val userId: String,
    val start: String,
    val end: String? = null,
    val duration: Int? = null,
    @SerialName("task_id")
    val taskId: String? = null,
    @SerialName("project_id")
    val projectId: String? = null,
    val tags: List<Tag> = emptyList(),
    val billable: Boolean = false,
    @SerialName("organization_id")
    val organizationId: String
)

/**
 * Tag associated with a time entry
 */
@Serializable
data class Tag(
    val id: String,
    val name: String
)

/**
 * User profile information
 */
@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String
)

/**
 * Organization membership information
 */
@Serializable
data class Membership(
    val id: String,
    val role: String,
    val organization: Organization
) {
    // Convenience property to get organization ID
    val organizationId: String
        get() = organization.id
}

/**
 * Organization information
 */
@Serializable
data class Organization(
    val id: String,
    val name: String,
    val currency: String
)

/**
 * Response wrapper for time entry API calls
 */
@Serializable
data class TimeEntryResponse(
    val data: TimeEntry? = null
)

/**
 * Response wrapper for user API calls
 */
@Serializable
data class UserResponse(
    val data: User
)

/**
 * Response wrapper for memberships API calls
 */
@Serializable
data class MembershipsResponse(
    val data: List<Membership>
)

/**
 * Request to start a new time entry
 */
@Serializable
data class StartTimeEntryRequest(
    @SerialName("member_id")
    val memberId: String,
    val start: String,
    val description: String = "",
    @SerialName("project_id")
    val projectId: String? = null,
    @SerialName("task_id")
    val taskId: String? = null,
    val billable: Boolean = false,
    val tags: List<String> = emptyList()
)

/**
 * Request to stop an active time entry
 */
@Serializable
data class StopTimeEntryRequest(
    @SerialName("user_id")
    val userId: String,
    val start: String,
    val end: String
)

/**
 * Project information
 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val color: String,
    @SerialName("client_id")
    val clientId: String? = null,
    @SerialName("is_archived")
    val isArchived: Boolean = false,
    @SerialName("billable_rate")
    val billableRate: Int? = null,
    @SerialName("is_billable")
    val isBillable: Boolean = false,
    @SerialName("estimated_time")
    val estimatedTime: Int? = null,
    @SerialName("spent_time")
    val spentTime: Int = 0,
    @SerialName("is_public")
    val isPublic: Boolean = false
)

/**
 * Task information
 */
@Serializable
data class Task(
    val id: String,
    val name: String,
    @SerialName("is_done")
    val isDone: Boolean = false,
    @SerialName("project_id")
    val projectId: String,
    @SerialName("estimated_time")
    val estimatedTime: Int? = null,
    @SerialName("spent_time")
    val spentTime: Int = 0,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String
)

/**
 * Response wrapper for projects API calls
 */
@Serializable
data class ProjectsResponse(
    val data: List<Project>
)

/**
 * Response wrapper for tasks API calls
 */
@Serializable
data class TasksResponse(
    val data: List<Task>
)

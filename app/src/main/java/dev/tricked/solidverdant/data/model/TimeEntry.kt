package dev.tricked.solidverdant.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import androidx.compose.runtime.Stable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Time entry representing a tracking session
 */
@Stable
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
    @Serializable(with = TimeEntryTagsSerializer::class)
    val tags: List<Tag> = emptyList(),
    val billable: Boolean = false,
    @SerialName("organization_id")
    val organizationId: String
)

/**
 * Tag associated with a time entry
 */
@Stable
 @Serializable
 data class Tag(
    val id: String,
    val name: String = ""
)

/** The API returns tag objects in some endpoints and tag IDs in time-entry lists. */
object TimeEntryTagsSerializer : JsonTransformingSerializer<List<Tag>>(ListSerializer(Tag.serializer())) {
    override fun transformDeserialize(element: kotlinx.serialization.json.JsonElement) =
        JsonArray(
            element.jsonArray.map { tag ->
                if (tag is JsonPrimitive) {
                    JsonObject(mapOf("id" to JsonPrimitive(tag.jsonPrimitive.content)))
                } else {
                    tag
                }
            }
        )
}

/**
 * User profile information
 */
@Stable
 @Serializable
 data class User(
    val id: String,
    val name: String,
    val email: String,
    @SerialName("profile_photo_url")
    val profilePhotoUrl: String = "",
    val timezone: String = "UTC",
    @SerialName("week_start")
    val weekStart: String = "monday"
)

/**
 * Organization membership information
 */
@Stable
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
@Stable
 @Serializable
 data class Organization(
    val id: String,
    val name: String,
    val currency: String,
    @SerialName("prevent_overlapping_time_entries")
    val preventOverlappingTimeEntries: Boolean = false
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
 * Request to start a new time entry, or create a completed one when [end] is set
 */
@Serializable
data class StartTimeEntryRequest(
    @SerialName("member_id")
    val memberId: String,
    val start: String,
    val end: String? = null,
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
@Stable
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

@Stable
 @Serializable
 data class Client(
    val id: String,
    val name: String,
    @SerialName("is_archived") val isArchived: Boolean = false,
)

/**
 * Task information
 */
@Stable
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

@Serializable data class ClientsResponse(val data: List<Client>)

/**
 * Response wrapper for tasks API calls
 */
@Serializable
data class TasksResponse(
    val data: List<Task>
)

/**
 * Response wrapper for tags API calls
 */
@Serializable
data class TagsResponse(
    val data: List<Tag>
)

/**
 * Response wrapper for multiple time entries
 */
@Serializable
data class TimeEntriesResponse(
    val data: List<TimeEntry>,
    val meta: TimeEntriesMeta? = null
)

@Serializable
data class TimeEntriesMeta(
    val total: Int? = null,
    @SerialName("current_page") val currentPage: Int? = null,
    @SerialName("last_page") val lastPage: Int? = null,
    @SerialName("per_page") val perPage: Int? = null
)

/**
 * Request to update an existing time entry
 */
@Serializable
data class UpdateTimeEntryRequest(
    @SerialName("user_id")
    val userId: String,
    val start: String,
    val end: String? = null,
    val description: String? = null,
    @SerialName("project_id")
    val projectId: String? = null,
    @SerialName("task_id")
    val taskId: String? = null,
    val billable: Boolean = false,
    val tags: List<String> = emptyList()
)

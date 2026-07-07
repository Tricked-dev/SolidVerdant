package dev.tricked.solidverdant.sync

import kotlinx.serialization.Serializable

@Serializable
data class StartPayload(
    val memberId: String,
    val userId: String,
    val projectId: String?,
    val taskId: String?,
    val description: String,
    val tagIds: List<String>
)

@Serializable
data class CreatePayload(
    val memberId: String,
    val userId: String,
    val start: String,
    val end: String,
    val description: String,
    val projectId: String?,
    val taskId: String?,
    val billable: Boolean,
    val tagIds: List<String>,
)

@Serializable
data class StopPayload(val userId: String, val start: String)

@Serializable
data class UpdatePayload(
    val userId: String,
    val start: String,
    val end: String?,
    val description: String?,
    val projectId: String?,
    val taskId: String?,
    val billable: Boolean,
    val tagIds: List<String>
)

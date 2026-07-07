/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.sync

import kotlinx.serialization.Serializable

@Serializable
data class StartPayload(
    val memberId: String,
    val userId: String,
    val projectId: String?,
    val taskId: String?,
    val description: String,
    val tagIds: List<String>,
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
    val tagIds: List<String>,
)

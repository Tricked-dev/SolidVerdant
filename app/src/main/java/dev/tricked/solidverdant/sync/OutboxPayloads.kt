/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.sync

import dev.tricked.solidverdant.data.model.TimeEntryType
import kotlinx.serialization.Serializable

@Serializable
data class StartPayload(
    val memberId: String,
    val userId: String,
    val projectId: String?,
    val taskId: String?,
    val description: String,
    val tagIds: List<String>,
    /**
     * The actual capture-time start timestamp (ISO-8601, matching [CreatePayload.start]'s
     * serialization). Must NOT be recomputed at sync time, otherwise an entry started while
     * offline is stamped with the reconnect time instead of when it was really started.
     */
    val start: String = "",
    val type: TimeEntryType = TimeEntryType.WORK,
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
    /**
     * IDs of server entries that already matched this create before its first POST. A retry may
     * adopt only a matching ID absent from this baseline, so duplicating an existing entry never
     * mistakes the source entry for a response-lost copy.
     */
    val recoveryBaselineEntryIds: List<String>? = null,
    val type: TimeEntryType = TimeEntryType.WORK,
)

@Serializable
data class StopPayload(
    val userId: String,
    val start: String,
    /**
     * The actual capture-time end timestamp (ISO-8601, matching [CreatePayload.end]'s
     * serialization). Must NOT be recomputed at sync time, otherwise an entry stopped while
     * offline is stamped with the reconnect time instead of when it was really stopped.
     */
    val end: String = "",
    val type: TimeEntryType = TimeEntryType.WORK,
)

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
    val type: TimeEntryType = TimeEntryType.WORK,
)

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.sync

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Canonical, comparable snapshot of the conflict-relevant fields of a time entry.
 *
 * The Solidtime API exposes no `updated_at`/version/ETag on time entries, so conflict
 * detection is content-based: this snapshot is captured for the last server-acked state
 * (stored on outbox ops) and compared against the server's current state via [matches].
 *
 * Timestamps are parsed leniently into epoch millis so that server-side formatting variance
 * (offset form, fractional seconds, `Z` vs `+00:00`) can never fabricate a conflict. When
 * parsing fails, the raw string is retained and compared verbatim as a fallback.
 */
@Serializable
data class ConflictSnapshot(
    val startMs: Long?,
    val endMs: Long?,
    val startRaw: String? = null,
    val endRaw: String? = null,
    val description: String,
    val projectId: String?,
    val taskId: String?,
    val billable: Boolean,
    val tagIds: List<String>,
) {
    /** True when every conflict-relevant field is equivalent to [other]. */
    fun matches(other: ConflictSnapshot): Boolean = instantsMatch(startMs, startRaw, other.startMs, other.startRaw) &&
        instantsMatch(endMs, endRaw, other.endMs, other.endRaw) &&
        description == other.description &&
        projectId == other.projectId &&
        taskId == other.taskId &&
        billable == other.billable &&
        // Sorted here (not just in [of]) so directly-constructed snapshots keep tag-order invariance.
        tagIds.sorted() == other.tagIds.sorted()

    private fun instantsMatch(ms: Long?, raw: String?, otherMs: Long?, otherRaw: String?): Boolean =
        if (ms != null && otherMs != null) ms == otherMs else raw == otherRaw

    companion object {
        /**
         * Sentinel stored *instead of* serialized snapshot JSON when the entry was deleted on
         * the server. Never a field value inside a snapshot.
         */
        const val DELETED_MARKER = "DELETED"

        fun of(
            start: String?,
            end: String?,
            description: String?,
            projectId: String?,
            taskId: String?,
            billable: Boolean,
            tagIds: List<String>,
        ): ConflictSnapshot {
            val startMs = start?.let(::parseToEpochMs)
            val endMs = end?.let(::parseToEpochMs)
            return ConflictSnapshot(
                startMs = startMs,
                endMs = endMs,
                startRaw = if (startMs == null) start else null,
                endRaw = if (endMs == null) end else null,
                description = description ?: "",
                projectId = projectId,
                taskId = taskId,
                billable = billable,
                tagIds = tagIds.sorted(),
            )
        }

        // The Instant.parse fallback is belt-and-suspenders; realistic inputs all parse as OffsetDateTime.
        private fun parseToEpochMs(raw: String): Long? = runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(raw).toEpochMilli() }
            .getOrNull()
    }
}

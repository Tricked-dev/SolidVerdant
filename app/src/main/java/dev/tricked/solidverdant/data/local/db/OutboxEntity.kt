/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OutboxOpType { START, CREATE, STOP, UPDATE, DELETE }

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opType: OutboxOpType,
    val organizationId: String,
    val timeEntryId: String,
    val payloadJson: String,
    val createdAtMs: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    /**
     * Stable per-operation idempotency key, generated once at enqueue time and preserved across
     * retries and process death. Used to correlate a committed-but-unacked write with the entry
     * the server created so a lost response does not produce a duplicate on retry.
     */
    val clientId: String = "",
    /**
     * Terminal dead-letter flag. When true the operation has permanently failed (server rejection
     * or attempt cap reached) and is no longer drained by the sync worker, but remains visible so
     * the user can inspect and explicitly retry it.
     */
    val deadLettered: Boolean = false,
    /**
     * The last server-acked TimeEntry JSON this outbox operation's local edit was based on
     * (SV-027 conflict detection "base" snapshot). Null when no snapshot was captured.
     */
    val baseSnapshotJson: String? = null,
)

/**
 * Machine-readable [OutboxEntity.lastError] written when the server answered HTTP 429. Encoded as
 * `rate_limited` or `rate_limited:<retry-after seconds>`. A rate-limited row keeps its attempt
 * budget, so this marker is the only evidence the UI has that the wait is deliberate.
 */
object RateLimitMarker {
    const val PREFIX = "rate_limited"

    fun encode(retryAfterSeconds: Long?): String = if (retryAfterSeconds == null) PREFIX else "$PREFIX:$retryAfterSeconds"

    fun matches(error: String?): Boolean = error == PREFIX || error?.startsWith("$PREFIX:") == true
}

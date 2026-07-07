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
    val deadLettered: Boolean = false
)

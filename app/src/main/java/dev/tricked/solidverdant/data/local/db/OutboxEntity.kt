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
    val lastError: String? = null
)

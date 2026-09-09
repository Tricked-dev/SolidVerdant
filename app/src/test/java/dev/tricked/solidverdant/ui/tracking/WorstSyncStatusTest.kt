/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.data.repository.TimeEntryRepository.EntrySyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class WorstSyncStatusTest {
    private fun op(entryId: String, status: EntrySyncStatus, type: OutboxOpType = OutboxOpType.UPDATE) =
        TimeEntryRepository.SyncOperation(entryId = entryId, type = type, status = status, attemptCount = 1, error = null)

    @Test fun `dead-lettered update behind a pending stop shows failed`() {
        val statuses = worstSyncStatusByEntryId(
            listOf(
                op("e1", EntrySyncStatus.FAILED),
                op("e1", EntrySyncStatus.PENDING, OutboxOpType.STOP),
            ),
        )
        assertEquals(EntrySyncStatus.FAILED, statuses["e1"])
    }

    @Test fun `severity order is failed, conflict, retrying, pending, synced`() {
        val ranked = listOf(
            EntrySyncStatus.SYNCED,
            EntrySyncStatus.PENDING,
            EntrySyncStatus.RETRYING,
            EntrySyncStatus.CONFLICT,
            EntrySyncStatus.FAILED,
        )
        ranked.indices.forEach { index ->
            val ops = ranked.take(index + 1).map { op("e", it) }
            assertEquals(ranked[index], worstSyncStatusByEntryId(ops)["e"])
            assertEquals(ranked[index], worstSyncStatusByEntryId(ops.reversed())["e"])
        }
    }

    @Test fun `entries are reduced independently`() {
        val statuses = worstSyncStatusByEntryId(
            listOf(op("a", EntrySyncStatus.RETRYING), op("b", EntrySyncStatus.SYNCED)),
        )
        assertEquals(mapOf("a" to EntrySyncStatus.RETRYING, "b" to EntrySyncStatus.SYNCED), statuses)
    }
}

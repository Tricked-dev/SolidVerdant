/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** A change that failed to reach the server can be retried from its history card. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryRetrySyncTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val failedEntry = TimeEntry(
        id = "failed-1",
        description = "unsynced",
        userId = "u1",
        start = "2026-07-06T09:00:00Z",
        end = "2026-07-06T10:00:00Z",
        duration = 3600,
        organizationId = "org1",
    )
    private val pendingEntry = failedEntry.copy(id = "pending-1", description = "queued", projectId = "p1")

    private fun operation(entry: TimeEntry, status: TimeEntryRepository.EntrySyncStatus) = TimeEntryRepository.SyncOperation(
        entryId = entry.id,
        type = OutboxOpType.CREATE,
        status = status,
        attemptCount = 1,
        error = null,
    )

    private fun setContent(onRetrySync: (TimeEntry) -> Unit) {
        val state = TrackingUiState(
            timeEntries = listOf(failedEntry, pendingEntry),
            hasLoadedTimeEntries = true,
            syncOperations = listOf(
                operation(failedEntry, TimeEntryRepository.EntrySyncStatus.FAILED),
                operation(pendingEntry, TimeEntryRepository.EntrySyncStatus.PENDING),
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                LazyColumn {
                    trackingHistoryItems(
                        uiState = state,
                        groupedEntries = mapOf(LocalDate.of(2026, 7, 6) to listOf(failedEntry, pendingEntry)),
                        onEdit = {},
                        onDelete = {},
                        onDateClick = {},
                        onRetrySync = onRetrySync,
                    )
                }
            }
        }
    }

    @Test
    fun `failed row exposes a retry button that retries that entry`() {
        val retried = mutableListOf<String>()
        setContent { retried += it.id }

        composeRule.onNodeWithTag(TrackingTestTags.entryRetrySyncButton(failedEntry.id))
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf(failedEntry.id), retried)
    }

    @Test
    fun `pending row keeps the passive chip`() {
        setContent {}

        composeRule.onNodeWithTag(TrackingTestTags.entryRetrySyncButton(pendingEntry.id)).assertDoesNotExist()
    }
}

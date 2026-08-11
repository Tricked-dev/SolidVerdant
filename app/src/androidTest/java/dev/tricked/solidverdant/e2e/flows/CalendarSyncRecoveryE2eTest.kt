/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.flows

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.data.model.TimeEntryType
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import dev.tricked.solidverdant.sync.UpdatePayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarSyncRecoveryE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @BackendPortable
    @Test
    fun failed_calendar_entry_exposes_status_and_discard_recovery() {
        // Calendar pages use the device's wall-clock date, while the harness clock is fixed for
        // deterministic sync assertions. Anchor this visual fixture to the device's current day.
        val fixture = e2e.prepare(
            E2eFixture.Completed(
                e2e.completedFixtureEntry(start = Instant.now().minusSeconds(7_200)),
            ),
        )
        val serverId = requireNotNull(fixture.serverId)
        e2e.launchApp()

        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        val entryTag = "week-entry-$serverId"
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_CONTENT_READY), WAIT_MS)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(entryTag), WAIT_MS)
        // Seed after the initial pull so the calendar observes a durable failure on the already
        // rendered Room entry instead of allowing the first refresh to race the fixture setup.
        e2e.seedFailedSync(serverId)
        e2e.composeRule.waitForIdle()
        e2e.composeRule.onNodeWithTag(entryTag, useUnmergedTree = true).performTouchInput { longClick() }
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_ENTRY_ACTIONS), WAIT_MS)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_ENTRY_SYNC_STATUS), WAIT_MS)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_ENTRY_SYNC_DISCARD), WAIT_MS)

        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_ENTRY_SYNC_DISCARD, useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilDoesNotExist(hasTestTag(TestTags.CALENDAR_ENTRY_SYNC_STATUS), WAIT_MS)

        assertNotNull("Discarding a failed local change must not delete the server entry", e2e.serverSnapshot().entry(fixture))
    }

    @BackendPortable
    @Test
    fun failed_calendar_entry_can_retry_and_sync_its_recovered_update() {
        val original = e2e.completedFixtureEntry(
            logicalId = "calendar-retry-entry",
            description = "Calendar before retry",
            start = Instant.now().minusSeconds(7_200),
        )
        val fixture = e2e.prepare(E2eFixture.Completed(original))
        val serverId = requireNotNull(fixture.serverId)
        e2e.launchApp()

        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        val entryTag = "week-entry-$serverId"
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_CONTENT_READY), WAIT_MS)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(entryTag), WAIT_MS)
        e2e.seedFailedSync(
            serverId,
            Json.encodeToString(
                UpdatePayload(
                    userId = e2e.session.userId,
                    start = original.start,
                    end = original.end,
                    description = "Calendar after retry",
                    projectId = null,
                    taskId = null,
                    billable = false,
                    tagIds = emptyList(),
                    type = TimeEntryType.WORK,
                ),
            ),
        )
        e2e.composeRule.waitForIdle()
        e2e.composeRule.onNodeWithTag(entryTag, useUnmergedTree = true).performTouchInput { longClick() }
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_ENTRY_ACTIONS), WAIT_MS)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_ENTRY_SYNC_RETRY), WAIT_MS)

        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_ENTRY_SYNC_RETRY, useUnmergedTree = true).performClick()
        e2e.awaitServer(WAIT_MS, driveSync = true) { snapshot ->
            snapshot.entry(fixture)?.description == "Calendar after retry"
        }
        e2e.composeRule.waitUntilDoesNotExist(hasTestTag(TestTags.CALENDAR_ENTRY_SYNC_STATUS), WAIT_MS)

        assertEquals("Calendar after retry", e2e.serverSnapshot().entry(fixture)?.description)
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

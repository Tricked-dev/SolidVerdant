/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.flows

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarEntryDeletionE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @BackendPortable
    @Test
    fun deletingAnEntryFromCalendarRemovesItFromTheServerAfterUndoWindow() {
        val fixture = e2e.prepare(
            E2eFixture.Completed(
                e2e.completedFixtureEntry(
                    logicalId = "calendar-delete-entry",
                    description = "Calendar delete",
                    start = Instant.now().minusSeconds(7_200),
                ),
            ),
        )
        e2e.launchApp()
        openCalendar(fixture.serverId)

        deleteEntry(fixture.serverId)
        e2e.composeRule.waitUntilDoesNotExist(
            hasTestTag("week-entry-${fixture.serverId}"),
            WAIT_MS,
        )
        e2e.awaitServer(WAIT_MS, driveSync = true) { snapshot -> snapshot.entry(fixture) == null }
    }

    @BackendPortable
    @Test
    fun calendarDeleteCanBeUndoneBeforeTheServerMutationCommits() {
        val fixture = e2e.prepare(
            E2eFixture.Completed(
                e2e.completedFixtureEntry(
                    logicalId = "calendar-undo-delete-entry",
                    description = "Calendar undo delete",
                    start = Instant.now().minusSeconds(7_200),
                ),
            ),
        )
        e2e.launchApp()
        openCalendar(fixture.serverId)

        deleteEntry(fixture.serverId)
        e2e.composeRule.waitUntilAtLeastOneExists(
            hasText(context.getString(R.string.undo), substring = false),
            WAIT_MS,
        )
        e2e.composeRule.onNodeWithText(context.getString(R.string.undo), useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(
            hasTestTag("week-entry-${fixture.serverId}"),
            WAIT_MS,
        )

        assertNotNull(
            "Undo must preserve the server entry because the deferred DELETE was cancelled",
            e2e.serverSnapshot().entry(fixture),
        )
    }

    private fun openCalendar(serverId: String?) {
        requireNotNull(serverId)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag("main_nav_calendar"), WAIT_MS)
        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_WEEK_GRID), WAIT_MS)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag("week-entry-$serverId"), WAIT_MS)
    }

    private fun deleteEntry(serverId: String?) {
        requireNotNull(serverId)
        e2e.composeRule.onNodeWithTag("week-entry-$serverId", useUnmergedTree = true)
            .performTouchInput { longClick() }
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_ENTRY_ACTIONS), WAIT_MS)
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_DELETE_ENTRY, useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_DELETE_CONFIRM), WAIT_MS)
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_DELETE_CONFIRM, useUnmergedTree = true).performClick()
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}

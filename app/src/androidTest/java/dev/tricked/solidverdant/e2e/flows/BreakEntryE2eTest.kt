/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.flows

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.data.model.TimeEntryType
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BreakEntryE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @BackendPortable
    @Test
    fun createsBreakFromCalendarAndRoundTripsTypeWithoutWorkMetadata() {
        e2e.prepare(E2eFixture.Empty)
        e2e.launchApp()

        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_ADD_BREAK), WAIT_MS)
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_ADD_BREAK, useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_ADD_BREAK_MENU), WAIT_MS)
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_ADD_BREAK_MENU, useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(EditTimeEntryTestTags.SAVE_BUTTON), WAIT_MS)
        e2e.composeRule.onNodeWithTag(EditTimeEntryTestTags.SAVE_BUTTON, useUnmergedTree = true).performClick()

        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            current.entries.any { it.type == TimeEntryType.BREAK }
        }
        val breakEntry = snapshot.entries.first { it.type == TimeEntryType.BREAK }
        assertEquals(TimeEntryType.BREAK, breakEntry.type)
        assertNull(breakEntry.projectId)
        assertNull(breakEntry.taskId)
        assertEquals(false, breakEntry.billable)
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.flows

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarEntryEditE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @BackendPortable
    @Test
    fun tappingACompletedCalendarEntryEditsAndSyncsIt() {
        val original = e2e.completedFixtureEntry(
            logicalId = "calendar-edit-entry",
            description = "Calendar before edit",
        )
        val fixture = e2e.prepare(E2eFixture.Completed(original))
        e2e.launchApp()

        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag("main_nav_calendar"), WAIT_MS)
        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_WEEK_GRID), WAIT_MS)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_CONTENT_READY), WAIT_MS)
        val entryTag = "week-entry-${requireNotNull(fixture.serverId)}"
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(entryTag), WAIT_MS)
        e2e.composeRule.onNodeWithTag(entryTag, useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.ENTRY_SAVE), WAIT_MS)
        val descriptionField = e2e.composeRule.onNodeWithTag(TestTags.ENTRY_DESCRIPTION, useUnmergedTree = true)
        descriptionField.performScrollTo()
        descriptionField.performTextClearance()
        descriptionField.performTextInput("Calendar after edit")
        e2e.composeRule.onNodeWithTag(TestTags.ENTRY_SAVE, useUnmergedTree = true)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        e2e.composeRule.waitUntilDoesNotExist(hasTestTag(TestTags.ENTRY_SAVE), WAIT_MS)
        e2e.awaitLocalEntry(requireNotNull(fixture.serverId), WAIT_MS) { entry ->
            entry.description == "Calendar after edit"
        }

        val persisted = requireNotNull(
            e2e.awaitServer(WAIT_MS, driveSync = true) { snapshot ->
                snapshot.entry(fixture)?.description == "Calendar after edit"
            }.entry(fixture),
        )
        assertEquals("Calendar after edit", persisted.description)
        assertEquals(Instant.parse(original.start), Instant.parse(persisted.start))
        assertEquals(Instant.parse(requireNotNull(original.end)), Instant.parse(requireNotNull(persisted.end)))
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}

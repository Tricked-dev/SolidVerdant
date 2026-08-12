/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.flows

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarGestureCreationE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @BackendPortable
    @Test
    fun tappingAnEmptyCalendarSlotOpensCreateAndSyncsTheEntry() {
        e2e.prepare(E2eFixture.Empty)
        e2e.launchApp()
        openCalendar()

        val description = "Calendar tap ${UUID.randomUUID()}"
        e2e.composeRule.onNodeWithTag(selectionTag(), useUnmergedTree = true)
            .performScrollTo()
            .performTouchInput {
                down(center)
                up()
            }
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.ENTRY_SAVE), WAIT_MS)
        e2e.composeRule.onNodeWithTag(TestTags.ENTRY_DESCRIPTION, useUnmergedTree = true)
            .performScrollTo()
            .performTextInput(description)
        e2e.composeRule.onNodeWithTag(TestTags.ENTRY_SAVE, useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        val persisted = e2e.awaitServer(WAIT_MS, driveSync = true) { snapshot ->
            snapshot.entries.any { it.description == description && it.end != null }
        }.entries.first { it.description == description }
        assertTrue("A tapped calendar slot should create a completed entry", persisted.end != null)
    }

    @BackendPortable
    @Test
    fun draggingAnEmptyCalendarRangeUsesTheSelectedInterval() {
        e2e.prepare(E2eFixture.Empty)
        e2e.launchApp()
        openCalendar()

        val description = "Calendar range ${UUID.randomUUID()}"
        e2e.composeRule.onNodeWithTag(selectionTag(), useUnmergedTree = true)
            .performScrollTo()
            .performTouchInput {
                val start = Offset(center.x, 80f)
                down(start)
                moveTo(Offset(start.x, start.y + 160f), delayMillis = 250)
                up()
            }
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.ENTRY_SAVE), WAIT_MS)
        e2e.composeRule.onNodeWithTag(TestTags.ENTRY_DESCRIPTION, useUnmergedTree = true)
            .performScrollTo()
            .performTextInput(description)
        e2e.composeRule.onNodeWithTag(TestTags.ENTRY_SAVE, useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        val persisted = e2e.awaitServer(WAIT_MS, driveSync = true) { snapshot ->
            snapshot.entries.any { it.description == description && it.end != null }
        }.entries.first { it.description == description }
        assertTrue(
            "A dragged calendar range should retain a useful selected duration",
            requireNotNull(persisted.duration) > 15 * 60,
        )
    }

    private fun openCalendar() {
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag("main_nav_calendar"), WAIT_MS)
        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_WEEK_GRID), WAIT_MS)
    }

    private fun selectionTag(): String = TestTags.calendarSelection(
        Instant.ofEpochMilli(e2e.testClock.nowMs).atZone(e2e.session.zone).toLocalDate(),
    )

    private companion object {
        const val WAIT_MS = 15_000L
    }
}

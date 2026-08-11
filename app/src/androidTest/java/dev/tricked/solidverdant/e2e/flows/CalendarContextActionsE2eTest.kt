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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarContextActionsE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @BackendPortable
    @Test
    fun duplicateFromCalendarPreservesTheSourceAndCreatesANewServerEntry() {
        val original = e2e.completedFixtureEntry(
            logicalId = "calendar-context-duplicate",
            description = "Calendar context duplicate",
            start = Instant.now().minusSeconds(7_200),
            durationSeconds = 3_600,
        )
        val fixture = e2e.prepare(E2eFixture.Completed(original))
        e2e.launchApp()
        openCalendar(fixture.serverId)
        openActions(fixture.serverId)

        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_DUPLICATE_ENTRY, useUnmergedTree = true).performClick()

        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            current.entries.count { it.description == original.description } == 2
        }
        val copies = snapshot.entries.filter { it.description == original.description }
        assertEquals(2, copies.size)
        assertTrue(copies.any { it.id == fixture.serverId })
        copies.forEach { copy ->
            assertNotNull(copy.end)
            assertEquals(original.start, copy.start)
            assertEquals(original.end, copy.end)
            assertEquals(original.duration, copy.duration)
        }
    }

    @BackendPortable
    @Test
    fun splitFromCalendarCreatesAdjacentServerIntervals() {
        val original = e2e.completedFixtureEntry(
            logicalId = "calendar-context-split",
            description = "Calendar context split",
            start = Instant.now().minusSeconds(10_800),
            durationSeconds = 7_200,
        )
        val fixture = e2e.prepare(E2eFixture.Completed(original))
        e2e.launchApp()
        openCalendar(fixture.serverId)
        openActions(fixture.serverId)

        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_SPLIT_ENTRY, useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_SPLIT_CONFIRM), WAIT_MS)
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_SPLIT_CONFIRM, useUnmergedTree = true).performClick()

        val midpoint = Instant.parse(original.start).plusSeconds(3_600)
        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            val halves = current.entries.filter { it.description == original.description }
            halves.size == 2 &&
                halves.any { it.id == fixture.serverId && it.end == midpoint.toString() } &&
                halves.any { it.id != fixture.serverId && it.start == midpoint.toString() && it.end == original.end }
        }
        val halves = snapshot.entries.filter { it.description == original.description }
        assertEquals(2, halves.size)
        assertEquals(
            Duration.ofHours(1),
            Duration.between(Instant.parse(halves.first { it.id == fixture.serverId }.start), midpoint),
        )
        assertEquals(
            Duration.ofHours(1),
            Duration.between(midpoint, Instant.parse(requireNotNull(halves.first { it.id != fixture.serverId }.end))),
        )
    }

    @BackendPortable
    @Test
    fun stopFromCalendarEndsTheRunningServerEntry() {
        val original = e2e.completedFixtureEntry(
            logicalId = "calendar-context-stop",
            description = "Calendar context stop",
            start = Instant.now().minusSeconds(60),
        ).copy(end = null, duration = null)
        val fixture = e2e.prepare(E2eFixture.Active(original))
        e2e.launchApp()
        openCalendar(fixture.serverId)
        openActions(fixture.serverId)

        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_STOP_ENTRY, useUnmergedTree = true).performClick()

        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            current.activeEntry == null && current.entry(fixture)?.end != null
        }
        val stopped = requireNotNull(snapshot.entry(fixture))
        assertNotNull(stopped.end)
        assertTrue(Instant.parse(stopped.end).isAfter(Instant.parse(stopped.start)))
    }

    private fun openCalendar(serverId: String?) {
        requireNotNull(serverId)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag("main_nav_calendar"), WAIT_MS)
        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_WEEK_GRID), WAIT_MS)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag("week-entry-$serverId"), WAIT_MS)
    }

    private fun openActions(serverId: String?) {
        requireNotNull(serverId)
        e2e.composeRule.onNodeWithTag("week-entry-$serverId", useUnmergedTree = true)
            .performScrollTo()
            .performTouchInput { longClick() }
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_ENTRY_ACTIONS), WAIT_MS)
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}

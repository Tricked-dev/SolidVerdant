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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarEntryMoveE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @BackendPortable
    @Test
    fun draggingACompletedCalendarEntryPreservesDurationAndSyncsItsNewStart() {
        val original = e2e.completedFixtureEntry(
            logicalId = "calendar-move-entry",
            description = "Calendar move",
            durationSeconds = 3_600,
        )
        val fixture = e2e.prepare(E2eFixture.Completed(original))
        e2e.launchApp()

        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag("main_nav_calendar"), WAIT_MS)
        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_WEEK_GRID), WAIT_MS)
        val entryTag = "week-entry-${requireNotNull(fixture.serverId)}"
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(entryTag), WAIT_MS)

        e2e.composeRule.onNodeWithTag(entryTag, useUnmergedTree = true)
            .performScrollTo()
            .performTouchInput {
                down(center)
                moveBy(Offset(0f, -height.toFloat()), delayMillis = 250)
                up()
            }

        val moved = e2e.awaitServer(WAIT_MS, driveSync = true) { snapshot ->
            snapshot.entry(fixture)?.let { entry ->
                entry.end != null && entry.start != original.start && entry.duration == original.duration
            } == true
        }.entry(fixture)
        val persisted = requireNotNull(moved)
        assertNotEquals("The drag should change the entry start", original.start, persisted.start)
        assertEquals("The drag must preserve the one-hour duration", original.duration, persisted.duration)
        assertEquals(
            "The server interval must retain the original exact duration",
            Duration.ofHours(1),
            Duration.between(
                Instant.parse(persisted.start),
                Instant.parse(requireNotNull(persisted.end)),
            ),
        )
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}

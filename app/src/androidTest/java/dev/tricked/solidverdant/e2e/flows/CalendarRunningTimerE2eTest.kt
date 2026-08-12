/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.flows

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.icu.util.TimeZone
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import dev.tricked.solidverdant.service.TimeTrackingNotificationService
import dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarRunningTimerE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun cleanRunningTimerSurface() {
        context.stopService(Intent(context, TimeTrackingNotificationService::class.java))
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    @BackendPortable
    @Test
    fun calendar_shows_running_timer_and_menu_opens_start_editor() {
        val original = e2e.completedFixtureEntry(
            logicalId = "calendar-editable-running-entry",
            description = "Calendar editable timer",
        ).copy(end = null, duration = null)
        val originalHandle = e2e.prepare(E2eFixture.Active(original))
        e2e.launchApp()

        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_CONTENT_READY), WAIT_MS)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_RUNNING_TIMER), WAIT_MS)

        val entryTag = "week-entry-${requireNotNull(originalHandle.serverId)}"
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(entryTag), WAIT_MS)
        e2e.composeRule.waitForIdle()
        e2e.composeRule.onNodeWithTag(entryTag, useUnmergedTree = true)
            .performScrollTo()
            .performTouchInput { longClick() }
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_ENTRY_ACTIONS), WAIT_MS)
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_EDIT_START_TIME, useUnmergedTree = true).performClick()

        listOf(
            EditTimeEntryTestTags.START_DATE,
            EditTimeEntryTestTags.START_TIME,
            EditTimeEntryTestTags.SAVE_BUTTON,
            EditTimeEntryTestTags.CANCEL_BUTTON,
        ).forEach { tag ->
            e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(tag), WAIT_MS)
        }
        e2e.composeRule.onNodeWithTag(EditTimeEntryTestTags.CANCEL_BUTTON, useUnmergedTree = true).performClick()
        assertEquals(originalHandle.serverId, e2e.serverSnapshot().activeEntry?.id)
    }

    @BackendPortable
    @Test
    fun calendar_menu_start_date_edit_updates_the_running_entry_without_stopping_timer() {
        val original = e2e.completedFixtureEntry(
            logicalId = "calendar-start-correction",
            description = "Calendar start correction",
        ).copy(end = null, duration = null)
        val originalHandle = e2e.prepare(E2eFixture.Active(original))
        e2e.launchApp()

        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_CONTENT_READY), WAIT_MS)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_RUNNING_TIMER), WAIT_MS)
        val entryTag = "week-entry-${requireNotNull(originalHandle.serverId)}"
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(entryTag), WAIT_MS)
        e2e.composeRule.onNodeWithTag(entryTag, useUnmergedTree = true)
            .performScrollTo()
            .performTouchInput { longClick() }
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_ENTRY_ACTIONS), WAIT_MS)
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_EDIT_START_TIME, useUnmergedTree = true).performClick()

        val adjustedStart = Instant.parse(original.start).minus(1, ChronoUnit.DAYS)
        e2e.composeRule.onNodeWithTag(EditTimeEntryTestTags.START_DATE, useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(EditTimeEntryTestTags.DATE_PICKER), WAIT_MS)
        val dayLabel = datePickerDayLabel(adjustedStart.atZone(e2e.session.zone).toLocalDate())
        val day = hasText(dayLabel) and hasAnyAncestor(hasTestTag(EditTimeEntryTestTags.DATE_PICKER))
        e2e.composeRule.waitUntil(WAIT_MS) {
            e2e.composeRule.onAllNodes(day, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        e2e.composeRule.onAllNodes(day, useUnmergedTree = true).onFirst().performClick()
        e2e.composeRule.onNodeWithTag(EditTimeEntryTestTags.DATE_PICKER_CONFIRM, useUnmergedTree = true).performClick()
        e2e.composeRule.onNodeWithTag(EditTimeEntryTestTags.SAVE_BUTTON, useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            val active = current.activeEntry ?: return@awaitServer false
            active.id == originalHandle.serverId &&
                active.end == null &&
                Instant.parse(active.start) == adjustedStart
        }
        val active = requireNotNull(snapshot.activeEntry)
        assertEquals(originalHandle.serverId, active.id)
        assertEquals(adjustedStart, Instant.parse(active.start))
        assertEquals(null, active.end)
    }

    private fun datePickerDayLabel(date: java.time.LocalDate): String {
        val formatter = DateFormat.getInstanceForSkeleton(
            DatePickerDefaults.YearMonthWeekdayDaySkeleton,
            Locale.getDefault(),
        ).apply {
            setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
            timeZone = TimeZone.GMT_ZONE
        }
        return formatter.format(Date(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()))
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}

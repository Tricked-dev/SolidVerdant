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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
            start = Instant.now().minusSeconds(60),
        ).copy(end = null, duration = null)
        val originalHandle = e2e.prepare(E2eFixture.Active(original))
        e2e.launchApp()

        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_RUNNING_TIMER), WAIT_MS)

        val entryTag = "week-entry-${requireNotNull(originalHandle.serverId)}"
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(entryTag), WAIT_MS)
        e2e.composeRule.waitForIdle()
        e2e.composeRule.onNodeWithTag(entryTag, useUnmergedTree = true).performTouchInput { longClick() }
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

    private companion object {
        const val WAIT_MS = 15_000L
    }
}

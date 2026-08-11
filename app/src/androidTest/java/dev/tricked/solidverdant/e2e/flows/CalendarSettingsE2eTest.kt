/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.flows

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarSettingsE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @BackendPortable
    @Test
    fun calendarSettingsValidatePersistAndRestoreAcrossRecreation() {
        e2e.prepare(E2eFixture.Empty)
        val scenario = e2e.launchApp()
        openCalendar()
        openSettings()

        chooseOption(TestTags.CALENDAR_SETTINGS_SNAP, "30")
        chooseOption(TestTags.CALENDAR_SETTINGS_START, "8")
        chooseOption(TestTags.CALENDAR_SETTINGS_END, "18")
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_SETTINGS_DENSITY_SPACIOUS, useUnmergedTree = true).performClick()
        assertSettingsAreVisible()

        scenario.recreate()
        openCalendar()
        openSettings()
        assertSettingsAreVisible()
    }

    private fun openCalendar() {
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag("main_nav_calendar"), WAIT_MS)
        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_WEEK_GRID), WAIT_MS)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_CONTENT_READY), WAIT_MS)
    }

    private fun openSettings() {
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_SETTINGS, useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_SETTINGS_SHEET), WAIT_MS)
    }

    private fun chooseOption(control: String, value: String) {
        e2e.composeRule.onNodeWithTag(control, useUnmergedTree = true).performClick()
        val option = TestTags.calendarSettingsOption(control, value)
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(option), WAIT_MS)
        e2e.composeRule.onNodeWithTag(option, useUnmergedTree = true).performClick()
    }

    private fun assertSettingsAreVisible() {
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_SETTINGS_SHEET, useUnmergedTree = true).assertIsDisplayed()
        e2e.composeRule.onNodeWithTag(
            TestTags.calendarSettingsValue(TestTags.CALENDAR_SETTINGS_SNAP),
            useUnmergedTree = true,
        ).assertTextContains("30")
        e2e.composeRule.onNodeWithTag(
            TestTags.calendarSettingsValue(TestTags.CALENDAR_SETTINGS_START),
            useUnmergedTree = true,
        ).assertTextContains("8")
        e2e.composeRule.onNodeWithTag(
            TestTags.calendarSettingsValue(TestTags.CALENDAR_SETTINGS_END),
            useUnmergedTree = true,
        ).assertTextContains("18")
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_SETTINGS_DENSITY_SPACIOUS, useUnmergedTree = true).assertIsSelected()
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}

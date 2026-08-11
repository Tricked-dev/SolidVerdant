/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.tricked.solidverdant.data.calendar.DeviceCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarOverlayControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(state: CalendarUiState, onRetry: () -> Unit = {}, onToggleOverlay: (Boolean) -> Unit = {}) {
        composeRule.setContent {
            MaterialTheme {
                CalendarOverlayControls(
                    state = state,
                    showRationale = true,
                    onToggleOverlay = onToggleOverlay,
                    onRequestPermission = {},
                    onOpenAppSettings = {},
                    onToggleCalendar = {},
                    onRetry = onRetry,
                )
            }
        }
    }

    @Test
    fun calendar_picker_loading_is_not_reported_as_no_calendars() {
        setContent(
            CalendarUiState(
                overlayEnabled = true,
                hasCalendarPermission = true,
                calendarListLoading = true,
            ),
        )

        composeRule.onNodeWithTag(CalendarTestTags.OVERLAY_CALENDAR_LOADING).assertExists()
        composeRule.onNodeWithText("No calendars were found on this device.").assertDoesNotExist()
    }

    @Test
    fun calendar_picker_failure_exposes_a_retry_action() {
        var retryCount = 0
        setContent(
            CalendarUiState(
                overlayEnabled = true,
                hasCalendarPermission = true,
                calendarListError = true,
            ),
            onRetry = { retryCount++ },
        )

        composeRule.onNodeWithTag(CalendarTestTags.OVERLAY_CALENDAR_ERROR).assertExists()
        composeRule.onNodeWithTag(CalendarTestTags.OVERLAY_RETRY).performClick()

        assertEquals(1, retryCount)
    }

    @Test
    fun loaded_calendar_picker_keeps_the_overlay_toggle_operable() {
        var toggled: Boolean? = null
        setContent(
            CalendarUiState(
                overlayEnabled = true,
                hasCalendarPermission = true,
                availableCalendars = listOf(DeviceCalendar("1", "Work", "alice@example.com", null)),
            ),
            onToggleOverlay = { toggled = it },
        )

        composeRule.onNodeWithText("Work").assertExists()
        composeRule.onNodeWithTag(CalendarTestTags.OVERLAY_TOGGLE).performClick()

        assertTrue("The switch must report its new value", toggled == false)
    }
}

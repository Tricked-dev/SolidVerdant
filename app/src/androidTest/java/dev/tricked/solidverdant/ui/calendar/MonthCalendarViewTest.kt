/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthCalendarViewTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun tappingEntryInvokesCallback() {
        val date = LocalDate.of(2026, 7, 6)
        val e = TimeEntry(
            id = "e1",
            userId = "u",
            start = "2026-07-06T09:00:00Z",
            end = "2026-07-06T10:00:00Z",
            duration = 3600,
            organizationId = "o",
        )
        val state = CalendarUiState(
            visibleMonth = YearMonth.of(2026, 7),
            selectedDate = date,
            bucketsByDate = mapOf(date to DayBucket(date, listOf(e), 3600)),
            isLoading = false,
        )
        var clicked: String? = null
        composeRule.setContent {
            MonthCalendarView(
                state,
                onSelectDate = {},
                onPreviousMonth = {},
                onNextMonth = {},
                onEntryClick = { clicked = it.id },
            )
        }
        composeRule.onNodeWithTag("day-cell-2026-07-06").assertIsDisplayed()
        composeRule.onNodeWithTag("entry-row-e1").performClick()
        assertEquals("e1", clicked)
    }
}

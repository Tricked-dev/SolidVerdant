/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class WeekCalendarViewTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun draggingOnAnEmptyDayOpensASelectedRange() {
        val date = LocalDate.of(2026, 7, 6)
        var selected: CalendarTimeRange? = null
        composeRule.setContent {
            MaterialTheme {
                WeekCalendarView(
                    state = CalendarUiState(
                        viewMode = CalendarViewMode.WEEK,
                        visibleDays = listOf(date),
                        selectedDate = date,
                        weekAnchor = date,
                        isLoading = false,
                    ),
                    onSelectDate = {},
                    onEntryClick = { _: TimeEntry -> },
                    onCreateRange = { selected = it },
                    onPrevious = {},
                    onNext = {},
                    onToday = {},
                    projects = emptyList(),
                )
            }
        }

        composeRule.onNodeWithTag(CalendarTestTags.selection(date)).performTouchInput {
            val start = center
            down(Offset(start.x, start.y - 48f))
            moveTo(Offset(start.x, start.y + 48f), delayMillis = 250)
            up()
        }

        composeRule.runOnIdle { assertNotNull(selected) }
    }

    @Test
    fun tappingOnAnEmptyDayOpensOneQuarterHourEntry() {
        val date = LocalDate.of(2026, 7, 6)
        var selected: CalendarTimeRange? = null
        composeRule.setContent {
            MaterialTheme {
                WeekCalendarView(
                    state = CalendarUiState(
                        viewMode = CalendarViewMode.WEEK,
                        visibleDays = listOf(date),
                        selectedDate = date,
                        weekAnchor = date,
                        isLoading = false,
                    ),
                    onSelectDate = {},
                    onEntryClick = { _: TimeEntry -> },
                    onCreateRange = { selected = it },
                    onPrevious = {},
                    onNext = {},
                    onToday = {},
                    projects = emptyList(),
                )
            }
        }

        composeRule.onNodeWithTag(CalendarTestTags.selection(date)).performTouchInput {
            down(center)
            up()
        }

        composeRule.runOnIdle {
            assertNotNull(selected)
            val range = selected ?: error("tap did not select a range")
            assertEquals(15, java.time.Duration.between(range.start, range.end).toMinutes())
        }
    }
}

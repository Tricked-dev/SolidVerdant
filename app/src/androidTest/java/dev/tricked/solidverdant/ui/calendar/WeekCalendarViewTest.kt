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
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

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

    @Test
    fun draggingAnExistingEntryPreservesItsDurationAndCallsMove() {
        val date = LocalDate.of(2026, 7, 6)
        val entry = TimeEntry(
            id = "entry-1",
            userId = "user-1",
            organizationId = "org-1",
            start = "2026-07-06T09:00:00Z",
            end = "2026-07-06T10:00:00Z",
        )
        var moved: Triple<TimeEntry, String, String>? = null
        composeRule.setContent {
            MaterialTheme {
                WeekCalendarView(
                    state = CalendarUiState(
                        viewMode = CalendarViewMode.WEEK,
                        visibleDays = listOf(date),
                        selectedDate = date,
                        weekAnchor = date,
                        isLoading = false,
                        bucketsByDate = mapOf(date to DayBucket(date, listOf(entry), 3_600)),
                    ),
                    onSelectDate = {},
                    onEntryClick = {},
                    onMoveEntry = { source, start, end -> moved = Triple(source, start, end) },
                    onPrevious = {},
                    onNext = {},
                    onToday = {},
                    projects = emptyList(),
                )
            }
        }

        composeRule.onNodeWithTag("week-entry-${entry.id}").performTouchInput {
            down(center)
            moveBy(Offset(0f, 48f), delayMillis = 250)
            up()
        }

        composeRule.runOnIdle {
            val result = requireNotNull(moved)
            assertEquals(entry, result.first)
            assertEquals(Duration.ofHours(1), Duration.between(ZonedDateTime.parse(result.second), ZonedDateTime.parse(result.third)))
            assertEquals(10, ZonedDateTime.parse(result.second).hour)
        }
    }
}

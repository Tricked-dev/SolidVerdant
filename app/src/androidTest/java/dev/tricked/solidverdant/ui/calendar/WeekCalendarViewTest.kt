/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class WeekCalendarViewTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun currentTimeMarkerIsShownForToday() {
        val today = LocalDate.of(2026, 7, 6)
        val now = today.atTime(12, 0).toInstant(ZoneOffset.UTC)
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth().height(calendarTotalHeight(CalendarGridSettings()))) {
                    CurrentTimeMarker(now = now, day = today, zone = ZoneOffset.UTC)
                }
            }
        }

        composeRule.onNodeWithTag(CalendarTestTags.CURRENT_TIME_MARKER).fetchSemanticsNode()
    }

    @Test
    fun draggingOnAnEmptyDayOpensASelectedRange() {
        val date = LocalDate.of(2026, 7, 6)
        var selected: CalendarTimeRange? = null
        composeRule.setContent {
            MaterialTheme {
                WeekCalendarView(
                    state = CalendarUiState(
                        viewMode = CalendarViewMode.WEEK,
                        zone = ZoneOffset.UTC,
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
                        zone = ZoneOffset.UTC,
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
    fun entryShowsClientProjectTaskAndDurationMetadata() {
        val date = LocalDate.of(2026, 7, 6)
        val entry = TimeEntry(
            id = "entry-metadata",
            userId = "user-1",
            organizationId = "org-1",
            start = "2026-07-06T09:00:00Z",
            end = "2026-07-06T10:00:00Z",
            projectId = "project-1",
            taskId = "task-1",
        )
        composeRule.setContent {
            MaterialTheme {
                WeekCalendarView(
                    state = CalendarUiState(
                        viewMode = CalendarViewMode.WEEK,
                        zone = ZoneOffset.UTC,
                        visibleDays = listOf(date),
                        selectedDate = date,
                        weekAnchor = date,
                        isLoading = false,
                        bucketsByDate = mapOf(date to DayBucket(date, listOf(entry), 3_600)),
                    ),
                    onSelectDate = {},
                    onEntryClick = {},
                    onPrevious = {},
                    onNext = {},
                    onToday = {},
                    projects = listOf(Project(id = "project-1", name = "Project", color = "#123456", clientId = "client-1")),
                    clients = listOf(Client(id = "client-1", name = "Client")),
                    tasks = listOf(
                        Task(
                            id = "task-1",
                            name = "Task",
                            projectId = "project-1",
                            createdAt = "2026-07-01T00:00:00Z",
                            updatedAt = "2026-07-01T00:00:00Z",
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("week-entry-${entry.id}").performScrollTo()
        composeRule.onNodeWithText("Client · Project · Task").assertIsDisplayed()
        composeRule.onNodeWithText("1h 00m").assertExists()
    }

    @Test
    fun runningEntryKeepsCalculatedTimelineHeightWithoutDrag() {
        val running = TimeEntry(
            id = "entry-running-height",
            userId = "user-1",
            organizationId = "org-1",
            start = "2026-07-06T09:00:00Z",
            end = null,
        )
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .testTag("running-entry-height")
                        .then(
                            calendarEntryDragModifier(
                                modifier = Modifier.fillMaxWidth(),
                                entry = running,
                                day = LocalDate.of(2026, 7, 6),
                                zone = ZoneOffset.UTC,
                                dayIndex = 0,
                                dayCount = 1,
                                blockStartFraction = 0.375f,
                                blockHeightPx = 240f,
                                gridHeightPx = 640f,
                                columnWidthPx = 100f,
                                onMoveEntry = { _, _, _ -> },
                            ),
                        ),
                )
            }
        }

        assertEquals(
            240f,
            composeRule.onNodeWithTag("running-entry-height").fetchSemanticsNode().boundsInRoot.height,
            1f,
        )
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
        var gestureHeight = 0
        composeRule.setContent {
            MaterialTheme {
                WeekCalendarView(
                    state = CalendarUiState(
                        viewMode = CalendarViewMode.WEEK,
                        zone = ZoneOffset.UTC,
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

        composeRule.onNodeWithTag("week-entry-${entry.id}").performScrollTo().performTouchInput {
            gestureHeight = height
            down(center)
            // Use the rendered block height instead of a raw pixel distance. Instrumentation
            // emulators can use different densities, but one entry height is always one hour.
            moveBy(Offset(0f, -height.toFloat()), delayMillis = 250)
            up()
        }

        composeRule.runOnIdle {
            val result = requireNotNull(moved)
            assertEquals(entry, result.first)
            assertEquals(Duration.ofHours(1), Duration.between(ZonedDateTime.parse(result.second), ZonedDateTime.parse(result.third)))
            assertEquals("height=$gestureHeight start=${result.second}", 8, ZonedDateTime.parse(result.second).hour)
        }
    }

    @Test
    fun draggingAtAnEntryEdgeStillMovesAndPreservesItsDuration() {
        val date = LocalDate.of(2026, 7, 6)
        val entry = TimeEntry(
            id = "entry-edge-drag",
            userId = "user-1",
            organizationId = "org-1",
            start = "2026-07-06T09:00:00Z",
            end = "2026-07-06T10:00:00Z",
        )
        var moved: Triple<TimeEntry, String, String>? = null
        var gestureHeight = 0
        composeRule.setContent {
            MaterialTheme {
                WeekCalendarView(
                    state = CalendarUiState(
                        viewMode = CalendarViewMode.WEEK,
                        zone = ZoneOffset.UTC,
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

        composeRule.onNodeWithTag("week-entry-${entry.id}").performScrollTo().performTouchInput {
            gestureHeight = height
            down(Offset(center.x, bottom - 2f))
            moveBy(Offset(0f, -height.toFloat()), delayMillis = 250)
            up()
        }

        composeRule.runOnIdle {
            val result = requireNotNull(moved)
            assertEquals(Duration.ofHours(1), Duration.between(ZonedDateTime.parse(result.second), ZonedDateTime.parse(result.third)))
            assertEquals("height=$gestureHeight start=${result.second}", 8, ZonedDateTime.parse(result.second).hour)
        }
    }

    @Test
    fun longPressingAnExistingEntryInvokesLongPressCallback() {
        val date = LocalDate.of(2026, 7, 6)
        val entry = TimeEntry(
            id = "entry-long-press",
            userId = "user-1",
            organizationId = "org-1",
            start = "2026-07-06T09:00:00Z",
            end = "2026-07-06T10:00:00Z",
        )
        var longPressed: String? = null
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
                    onEntryLongPress = { longPressed = it.id },
                    onPrevious = {},
                    onNext = {},
                    onToday = {},
                    projects = emptyList(),
                )
            }
        }

        composeRule.onNodeWithTag("week-entry-${entry.id}").performScrollTo().performTouchInput { longClick() }

        composeRule.runOnIdle { assertEquals(entry.id, longPressed) }
    }
}

package dev.tricked.solidverdant.ui.calendar

import dev.tricked.solidverdant.data.calendar.DeviceCalendarEvent
import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class WeekCalendarLayoutTest {

    private val utc = ZoneOffset.UTC

    private fun event(
        id: Long,
        startIso: String,
        endIso: String,
        allDay: Boolean = false,
    ) = DeviceCalendarEvent(
        instanceId = id,
        eventId = id,
        calendarId = "1",
        title = "e$id",
        startUtcMs = Instant.parse(startIso).toEpochMilli(),
        endUtcMs = Instant.parse(endIso).toEpochMilli(),
        allDay = allDay,
        colorArgb = null,
    )

    private fun entry(id: String, startIso: String, endIso: String?) = TimeEntry(
        id = id,
        description = id,
        userId = "user",
        start = startIso,
        end = endIso,
        duration = 0,
        taskId = null,
        projectId = null,
        tags = emptyList(),
        billable = false,
        organizationId = "org",
    )

    // --- Week boundary / paging math ---------------------------------------------------------

    @Test
    fun weekStartDate_resolvesMondayAndSundayStarts() {
        val wed = LocalDate.of(2026, 7, 8) // Wednesday
        assertEquals(LocalDate.of(2026, 7, 6), weekStartDate(wed, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 7, 5), weekStartDate(wed, DayOfWeek.SUNDAY))
    }

    @Test
    fun visibleCalendarDays_sevenDayIsWeekAlignedThreeDayIsAnchored() {
        val wed = LocalDate.of(2026, 7, 8)
        val week = visibleCalendarDays(wed, DayOfWeek.MONDAY, 7)
        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 7, 6), week.first())
        assertEquals(LocalDate.of(2026, 7, 12), week.last())

        val threeDay = visibleCalendarDays(wed, DayOfWeek.MONDAY, 3)
        assertEquals(listOf(wed, wed.plusDays(1), wed.plusDays(2)), threeDay)
    }

    @Test
    fun pageAnchor_stepsFullWeekOrDayCount() {
        val wed = LocalDate.of(2026, 7, 8)
        assertEquals(LocalDate.of(2026, 7, 13), pageAnchor(wed, DayOfWeek.MONDAY, 7, 1))
        assertEquals(LocalDate.of(2026, 6, 29), pageAnchor(wed, DayOfWeek.MONDAY, 7, -1))
        assertEquals(LocalDate.of(2026, 7, 11), pageAnchor(wed, DayOfWeek.MONDAY, 3, 1))
        assertEquals(LocalDate.of(2026, 7, 5), pageAnchor(wed, DayOfWeek.MONDAY, 3, -1))
    }

    // --- Clipping ----------------------------------------------------------------------------

    @Test
    fun clampToDaySeconds_clipsToDayAndRejectsNonIntersecting() {
        val day = LocalDate.of(2026, 7, 6)
        val within = clampToDaySeconds(
            Instant.parse("2026-07-06T09:00:00Z").toEpochMilli(),
            Instant.parse("2026-07-06T10:00:00Z").toEpochMilli(),
            day, utc,
        )
        assertEquals(9 * 3600L to 10 * 3600L, within)

        val nextDay = clampToDaySeconds(
            Instant.parse("2026-07-07T09:00:00Z").toEpochMilli(),
            Instant.parse("2026-07-07T10:00:00Z").toEpochMilli(),
            day, utc,
        )
        assertNull(nextDay)
    }

    @Test
    fun clampToDaySeconds_clipsMidnightSpanningRangeOnEachDay() {
        val start = Instant.parse("2026-07-06T23:00:00Z").toEpochMilli()
        val end = Instant.parse("2026-07-07T01:00:00Z").toEpochMilli()
        val day6 = clampToDaySeconds(start, end, LocalDate.of(2026, 7, 6), utc)
        val day7 = clampToDaySeconds(start, end, LocalDate.of(2026, 7, 7), utc)
        assertEquals(23 * 3600L to SECONDS_PER_DAY, day6)
        assertEquals(0L to 3600L, day7)
    }

    // --- Overlap packing ---------------------------------------------------------------------

    @Test
    fun packOverlaps_nonOverlappingEventsShareNoColumns() {
        val packed = packOverlaps(listOf(0L to 100L, 200L to 300L))
        assertEquals(listOf(0 to 1, 0 to 1), packed)
    }

    @Test
    fun packOverlaps_overlappingEventsSplitIntoColumns() {
        val packed = packOverlaps(listOf(0L to 200L, 100L to 300L))
        assertEquals(0 to 2, packed[0])
        assertEquals(1 to 2, packed[1])
    }

    @Test
    fun packOverlaps_reusesFreedColumnWithinCluster() {
        // (0,100) & (50,150) overlap; (120,200) starts after (0,100) ends so it reuses column 0.
        val packed = packOverlaps(listOf(0L to 100L, 50L to 150L, 120L to 200L))
        assertEquals(listOf(0 to 2, 1 to 2, 0 to 2), packed)
    }

    // --- Timed layout ------------------------------------------------------------------------

    @Test
    fun layoutTimedEvents_flagsMidnightContinuationOnBothDays() {
        val events = listOf(event(1, "2026-07-06T23:00:00Z", "2026-07-07T01:00:00Z"))

        val day6 = layoutTimedEvents(events, LocalDate.of(2026, 7, 6), utc)
        assertEquals(1, day6.size)
        assertFalse(day6[0].continuesBefore)
        assertTrue(day6[0].continuesAfter)
        assertEquals(23f / 24f, day6[0].startFraction, 0.001f)

        val day7 = layoutTimedEvents(events, LocalDate.of(2026, 7, 7), utc)
        assertEquals(1, day7.size)
        assertTrue(day7[0].continuesBefore)
        assertFalse(day7[0].continuesAfter)
        assertEquals(0f, day7[0].startFraction, 0.001f)
    }

    @Test
    fun layoutTimedEvents_excludesAllDayEvents() {
        val events = listOf(event(1, "2026-07-06T00:00:00Z", "2026-07-07T00:00:00Z", allDay = true))
        assertTrue(layoutTimedEvents(events, LocalDate.of(2026, 7, 6), utc).isEmpty())
    }

    @Test
    fun layoutTimedEvents_enforcesMinimumHeightForShortEvents() {
        // A one-minute event must still be tall enough to perceive.
        val events = listOf(event(1, "2026-07-06T09:00:00Z", "2026-07-06T09:01:00Z"))
        val blocks = layoutTimedEvents(events, LocalDate.of(2026, 7, 6), utc)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].heightFraction >= MIN_BLOCK_HEIGHT_FRACTION)
    }

    @Test
    fun layoutTrackedEntries_placesConcurrentEntriesSideBySide() {
        val day = LocalDate.of(2026, 7, 6)
        val entries = listOf(
            entry("first", "2026-07-06T09:00:00Z", "2026-07-06T11:00:00Z"),
            entry("second", "2026-07-06T10:00:00Z", "2026-07-06T12:00:00Z"),
        )

        val blocks = layoutTrackedEntries(entries, day, Instant.parse("2026-07-06T13:00:00Z"), utc)

        assertEquals(0, blocks[0].column)
        assertEquals(1, blocks[1].column)
        assertEquals(2, blocks[0].columnCount)
        assertEquals(2, blocks[1].columnCount)
    }

    @Test
    fun layoutTrackedEntries_nonOverlappingEntriesUseFullWidth() {
        val day = LocalDate.of(2026, 7, 6)
        val entries = listOf(
            entry("first", "2026-07-06T09:00:00Z", "2026-07-06T10:00:00Z"),
            entry("second", "2026-07-06T10:00:00Z", "2026-07-06T11:00:00Z"),
        )

        val blocks = layoutTrackedEntries(entries, day, Instant.parse("2026-07-06T13:00:00Z"), utc)

        assertTrue(blocks.all { it.column == 0 && it.columnCount == 1 })
    }

    // --- All-day coverage --------------------------------------------------------------------

    @Test
    fun allDayEventsForDay_coversRangeWithExclusiveEnd() {
        val events = listOf(event(1, "2026-07-06T00:00:00Z", "2026-07-08T00:00:00Z", allDay = true))
        assertEquals(1, allDayEventsForDay(events, LocalDate.of(2026, 7, 6)).size)
        assertEquals(1, allDayEventsForDay(events, LocalDate.of(2026, 7, 7)).size)
        assertTrue(allDayEventsForDay(events, LocalDate.of(2026, 7, 8)).isEmpty())
    }

    @Test
    fun allDayEventsForDay_treatsEqualBeginEndAsSingleDay() {
        val events = listOf(event(1, "2026-07-06T00:00:00Z", "2026-07-06T00:00:00Z", allDay = true))
        assertEquals(1, allDayEventsForDay(events, LocalDate.of(2026, 7, 6)).size)
        assertTrue(allDayEventsForDay(events, LocalDate.of(2026, 7, 7)).isEmpty())
    }
}

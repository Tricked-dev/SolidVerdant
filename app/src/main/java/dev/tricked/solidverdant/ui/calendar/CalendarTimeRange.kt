/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import dev.tricked.solidverdant.data.model.TimeEntry
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** A half-open time range selected in a calendar time grid. */
data class CalendarTimeRange(val start: ZonedDateTime, val end: ZonedDateTime)

/** The complete interval produced when an existing entry is moved to a new start. */
data class CalendarEntryRange(val start: ZonedDateTime, val end: ZonedDateTime)

enum class CalendarResizeEdge { START, END }

/**
 * Convert a vertical drag in a configurable grid into a valid, snapped range. The grid uses the
 * actual elapsed length of the local day, so a DST transition does not create an invalid instant.
 */
fun calendarTimeRangeForDrag(
    day: LocalDate,
    startY: Float,
    endY: Float,
    gridHeightPx: Float,
    zone: ZoneId,
    settings: CalendarGridSettings = CalendarGridSettings(),
): CalendarTimeRange {
    val grid = calendarGridBounds(day, zone, settings)
    val secondsInGrid = grid.seconds
    val slotSeconds = settings.normalized().snapMinutes * SECONDS_PER_MINUTE
    val maxStartSecond = (secondsInGrid - slotSeconds).coerceAtLeast(0L)
    val lowSecond = calendarGridSecond(startY, gridHeightPx, secondsInGrid)
    val highSecond = calendarGridSecond(endY, gridHeightPx, secondsInGrid)
    val startSecond = snapToSlot(minOf(lowSecond, highSecond), slotSeconds).coerceIn(0L, maxStartSecond)
    val endSecond = snapToSlot(maxOf(lowSecond, highSecond), slotSeconds)
        .coerceIn(startSecond + slotSeconds, secondsInGrid)
    return CalendarTimeRange(
        start = grid.start.plusSeconds(startSecond).atZone(zone),
        end = grid.start.plusSeconds(endSecond).atZone(zone),
    )
}

/** Convert one vertical grid coordinate into the nearest valid calendar start time. */
fun calendarTimeAtGridPosition(
    day: LocalDate,
    y: Float,
    gridHeightPx: Float,
    zone: ZoneId,
    allowDayEnd: Boolean = false,
    settings: CalendarGridSettings = CalendarGridSettings(),
): ZonedDateTime {
    val normalized = settings.normalized()
    val grid = calendarGridBounds(day, zone, normalized)
    val slotSeconds = normalized.snapMinutes * SECONDS_PER_MINUTE
    val maxSecond = if (allowDayEnd) grid.seconds else (grid.seconds - slotSeconds).coerceAtLeast(0L)
    val second = snapToSlot(calendarGridSecond(y, gridHeightPx, grid.seconds), slotSeconds).coerceIn(0L, maxSecond)
    return grid.start.plusSeconds(second).atZone(zone)
}

/** Preserve an entry's complete duration while moving its start across local calendar days. */
fun calendarEntryRangeAt(entry: TimeEntry, targetStart: ZonedDateTime): CalendarEntryRange? {
    val originalStart = runCatching { ZonedDateTime.parse(entry.start) }.getOrNull() ?: return null
    val originalEnd = entry.end?.let { runCatching { ZonedDateTime.parse(it) }.getOrNull() } ?: return null
    val duration = Duration.between(originalStart, originalEnd)
    if (duration.isZero || duration.isNegative) return null
    return CalendarEntryRange(start = targetStart, end = targetStart.plus(duration))
}

/** Resize one boundary while preserving the other boundary and enforcing one visible grid slot. */
fun calendarEntryResizeRangeAt(
    entry: TimeEntry,
    edge: CalendarResizeEdge,
    boundary: ZonedDateTime,
    settings: CalendarGridSettings = CalendarGridSettings(),
): CalendarEntryRange? {
    val originalStart = runCatching { ZonedDateTime.parse(entry.start) }.getOrNull() ?: return null
    val originalEnd = entry.end?.let { runCatching { ZonedDateTime.parse(it) }.getOrNull() } ?: return null
    val range = when (edge) {
        CalendarResizeEdge.START -> CalendarEntryRange(boundary, originalEnd)
        CalendarResizeEdge.END -> CalendarEntryRange(originalStart, boundary)
    }
    return range.takeIf { Duration.between(it.start, it.end) >= Duration.ofMinutes(settings.normalized().snapMinutes.toLong()) }
}

/** A useful one-hour fallback for the toolbar's Add action when no drag range was selected. */
fun defaultCalendarTimeRange(
    day: LocalDate,
    zone: ZoneId,
    now: ZonedDateTime = ZonedDateTime.now(zone),
    settings: CalendarGridSettings = CalendarGridSettings(),
): CalendarTimeRange {
    val grid = calendarGridBounds(day, zone, settings)
    val oneHour = 60L * SECONDS_PER_MINUTE
    val available = grid.seconds.coerceAtLeast(1L)
    val duration = minOf(oneHour, available)
    val preferredStart = if (day == now.toLocalDate()) {
        now.truncatedTo(ChronoUnit.HOURS).minusHours(1).toInstant()
    } else {
        grid.start
    }
    val start = preferredStart.coerceIn(grid.start, grid.end.minusSeconds(duration))
    return CalendarTimeRange(start = start.atZone(zone), end = start.plusSeconds(duration).atZone(zone))
}

private fun calendarGridSecond(y: Float, gridHeightPx: Float, secondsInGrid: Long): Long {
    if (gridHeightPx <= 0f) return 0L
    return ((y / gridHeightPx).coerceIn(0f, 1f) * secondsInGrid).toLong()
}

private fun snapToSlot(second: Long, slotSeconds: Long): Long = ((second + slotSeconds / 2) / slotSeconds) * slotSeconds

private const val SECONDS_PER_MINUTE = 60L

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
 * Convert a vertical drag in a 24-hour grid into a valid, quarter-hour range. The grid uses the
 * actual elapsed length of the local day, so a DST transition does not create an invalid instant.
 */
fun calendarTimeRangeForDrag(day: LocalDate, startY: Float, endY: Float, gridHeightPx: Float, zone: ZoneId): CalendarTimeRange {
    val secondsInDay = secondsInLocalDay(day, zone)
    val maxStartSecond = (secondsInDay - SECONDS_PER_SLOT).coerceAtLeast(0L)
    val lowSecond = calendarGridSecond(startY, gridHeightPx, secondsInDay)
    val highSecond = calendarGridSecond(endY, gridHeightPx, secondsInDay)
    val startSecond = snapToSlot(minOf(lowSecond, highSecond)).coerceIn(0L, maxStartSecond)
    val endSecond = snapToSlot(maxOf(lowSecond, highSecond)).coerceIn(startSecond + SECONDS_PER_SLOT, secondsInDay)
    val dayStart = day.atStartOfDay(zone).toInstant()
    return CalendarTimeRange(
        start = dayStart.plusSeconds(startSecond).atZone(zone),
        end = dayStart.plusSeconds(endSecond).atZone(zone),
    )
}

/** Convert one vertical grid coordinate into the nearest valid calendar start time. */
fun calendarTimeAtGridPosition(day: LocalDate, y: Float, gridHeightPx: Float, zone: ZoneId, allowDayEnd: Boolean = false): ZonedDateTime {
    val secondsInDay = secondsInLocalDay(day, zone)
    val maxSecond = if (allowDayEnd) secondsInDay else (secondsInDay - SECONDS_PER_SLOT).coerceAtLeast(0L)
    val second = snapToSlot(calendarGridSecond(y, gridHeightPx, secondsInDay)).coerceIn(0L, maxSecond)
    return day.atStartOfDay(zone).toInstant().plusSeconds(second).atZone(zone)
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
fun calendarEntryResizeRangeAt(entry: TimeEntry, edge: CalendarResizeEdge, boundary: ZonedDateTime): CalendarEntryRange? {
    val originalStart = runCatching { ZonedDateTime.parse(entry.start) }.getOrNull() ?: return null
    val originalEnd = entry.end?.let { runCatching { ZonedDateTime.parse(it) }.getOrNull() } ?: return null
    val range = when (edge) {
        CalendarResizeEdge.START -> CalendarEntryRange(boundary, originalEnd)
        CalendarResizeEdge.END -> CalendarEntryRange(originalStart, boundary)
    }
    return range.takeIf {
        Duration.between(it.start, it.end) >= Duration.ofMinutes(SLOT_MINUTES)
    }
}

/** A useful one-hour fallback for the toolbar's Add action when no drag range was selected. */
fun defaultCalendarTimeRange(day: LocalDate, zone: ZoneId, now: ZonedDateTime = ZonedDateTime.now(zone)): CalendarTimeRange {
    val start = if (day == now.toLocalDate()) {
        now.truncatedTo(ChronoUnit.HOURS).minusHours(1)
    } else {
        day.atTime(DEFAULT_START_HOUR, 0).atZone(zone)
    }
    return CalendarTimeRange(start = start, end = start.plusHours(1))
}

private fun calendarGridSecond(y: Float, gridHeightPx: Float, secondsInDay: Long): Long {
    if (gridHeightPx <= 0f) return 0L
    return ((y / gridHeightPx).coerceIn(0f, 1f) * secondsInDay).toLong()
}

private fun snapToSlot(second: Long): Long = ((second + SECONDS_PER_SLOT / 2) / SECONDS_PER_SLOT) * SECONDS_PER_SLOT

private const val SLOT_MINUTES = 15L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_SLOT = SLOT_MINUTES * SECONDS_PER_MINUTE
private const val DEFAULT_START_HOUR = 9

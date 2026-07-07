/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.data.calendar.DeviceCalendarEvent
import dev.tricked.solidverdant.data.model.TimeEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Seconds in a 24h day. All timed layout fractions are expressed relative to this. */
const val SECONDS_PER_DAY: Long = 86_400L

/** Minimum visible height for a timed block so a very short event stays perceivable. */
const val MIN_BLOCK_HEIGHT_FRACTION: Float = 0.012f

/** Minimum visible fraction for a tracked entry so a very short entry stays legible/tappable. */
const val MIN_ENTRY_HEIGHT_FRACTION: Float = 0.02f

// --- Shared time-grid metrics ------------------------------------------------------------------
// ONE grid geometry for both the week grid and the month/day timeline so entries and hour lines
// align identically across views. Pick these over per-view magic numbers.

/** Height of a single hour row in the timed grid. */
val CalendarHourHeight = 48.dp

/** Width of the leading gutter holding the hour labels (also the left inset for entry blocks). */
val CalendarGutterWidth = 48.dp

/** Full height of a 24h day column. */
val CalendarTotalHeight = CalendarHourHeight * 24

/**
 * Vertical placement of a tracked [entry] within [day], as `topFraction to heightFraction` of a
 * 24h column. Unparseable starts fall back to the day start; a running entry (no end) extends to
 * [now]. The height is floored at [MIN_ENTRY_HEIGHT_FRACTION] so very short entries stay visible.
 */
fun timelineOffsets(
    entry: TimeEntry,
    day: LocalDate,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<Float, Float> {
    val dayStart = day.atStartOfDay(zone).toInstant()
    val secondsInDay = SECONDS_PER_DAY.toFloat()
    val start = try {
        ZonedDateTime.parse(entry.start, DateTimeFormatter.ISO_DATE_TIME).toInstant()
    } catch (_: Exception) {
        dayStart
    }
    val end = entry.end?.let {
        try {
            ZonedDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME).toInstant()
        } catch (_: Exception) {
            now
        }
    } ?: now
    val topSec = (start.epochSecond - dayStart.epochSecond).coerceIn(0, SECONDS_PER_DAY)
    val endSec = (end.epochSecond - dayStart.epochSecond).coerceIn(0, SECONDS_PER_DAY)
    val top = topSec / secondsInDay
    val height = ((endSec - topSec) / secondsInDay).coerceAtLeast(MIN_ENTRY_HEIGHT_FRACTION)
    return top to height
}

/**
 * A device-calendar event positioned within a single day column.
 *
 * A midnight-spanning event yields one block per day it covers, each clipped to that day and
 * flagged with [continuesBefore]/[continuesAfter] so the UI can show it as a continuation.
 * [column]/[columnCount] pack mutually-overlapping events side by side within the column.
 */
data class EventBlock(
    val event: DeviceCalendarEvent,
    val startFraction: Float,
    val heightFraction: Float,
    val column: Int,
    val columnCount: Int,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

/**
 * The first day of the week containing [reference] for the given [weekStart].
 * Mirrors the lead-day math used by the month grid so both views agree on week boundaries.
 */
fun weekStartDate(reference: LocalDate, weekStart: DayOfWeek): LocalDate {
    val diff = ((reference.dayOfWeek.value - weekStart.value) + 7) % 7
    return reference.minusDays(diff.toLong())
}

/**
 * The consecutive dates shown for a week/multi-day page.
 *
 * A 7-day page is aligned to [weekStart] so the columns are stable regardless of which day inside
 * the week is selected. Narrower pages (e.g. the 3-day phone layout) anchor directly on [anchor]
 * and page by their own width, which keeps swiping predictable without week alignment.
 */
fun visibleCalendarDays(anchor: LocalDate, weekStart: DayOfWeek, dayCount: Int): List<LocalDate> {
    val safeCount = dayCount.coerceAtLeast(1)
    val start = if (safeCount >= 7) weekStartDate(anchor, weekStart) else anchor
    return (0 until safeCount).map { start.plusDays(it.toLong()) }
}

/**
 * Advances [anchor] by one page in [direction] (+1 next, -1 previous). 7-day pages step a full
 * aligned week; shorter pages step by their width. Returns an anchor that
 * [visibleCalendarDays] will normalise back onto the correct page.
 */
fun pageAnchor(anchor: LocalDate, weekStart: DayOfWeek, dayCount: Int, direction: Int): LocalDate {
    val safeCount = dayCount.coerceAtLeast(1)
    val step = if (safeCount >= 7) 7 else safeCount
    val base = if (safeCount >= 7) weekStartDate(anchor, weekStart) else anchor
    return base.plusDays((step.toLong() * direction))
}

/**
 * Clips an instant range to [day] in [zone], returning the covered second-offsets within the day
 * as `startSec..endSec` (both in `0..86_400`), or null when the range does not intersect the day.
 */
fun clampToDaySeconds(startMs: Long, endMs: Long, day: LocalDate, zone: ZoneId): Pair<Long, Long>? {
    val dayStartMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEndMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val s = maxOf(startMs, dayStartMs)
    val e = minOf(endMs, dayEndMs)
    if (e <= s) return null
    val startSec = ((s - dayStartMs) / 1000L).coerceIn(0L, SECONDS_PER_DAY)
    val endSec = ((e - dayStartMs) / 1000L).coerceIn(0L, SECONDS_PER_DAY)
    if (endSec <= startSec) return null
    return startSec to endSec
}

/**
 * Assigns overlap columns to [intervals] (each `startSec to endSec`, in input order).
 *
 * Returns, per input interval, `column to columnCount`: its 0-based column and the number of
 * columns used by the maximal cluster of transitively-overlapping intervals it belongs to. This is
 * the standard day-view packing so concurrent events render side by side and non-overlapping
 * events each take the full width.
 */
fun packOverlaps(intervals: List<Pair<Long, Long>>): List<Pair<Int, Int>> {
    if (intervals.isEmpty()) return emptyList()
    val order = intervals.indices.sortedWith(
        compareBy({ intervals[it].first }, { intervals[it].second }),
    )
    val column = IntArray(intervals.size)
    val columnCount = IntArray(intervals.size)

    var clusterMembers = mutableListOf<Int>()
    var clusterEnd = Long.MIN_VALUE
    // Per-column running end time within the active cluster.
    var columnEnds = mutableListOf<Long>()

    fun closeCluster() {
        val count = columnEnds.size.coerceAtLeast(1)
        clusterMembers.forEach { columnCount[it] = count }
        clusterMembers = mutableListOf()
        columnEnds = mutableListOf()
        clusterEnd = Long.MIN_VALUE
    }

    for (idx in order) {
        val (start, end) = intervals[idx]
        if (clusterMembers.isNotEmpty() && start >= clusterEnd) {
            // No overlap with the running cluster -> finalise it and begin a new one.
            closeCluster()
        }
        // Reuse the first column whose last event has ended by this start.
        var assigned = -1
        for (c in columnEnds.indices) {
            if (columnEnds[c] <= start) {
                assigned = c
                columnEnds[c] = end
                break
            }
        }
        if (assigned == -1) {
            assigned = columnEnds.size
            columnEnds.add(end)
        }
        column[idx] = assigned
        clusterMembers.add(idx)
        clusterEnd = maxOf(clusterEnd, end)
    }
    if (clusterMembers.isNotEmpty()) closeCluster()

    return intervals.indices.map { column[it] to columnCount[it] }
}

/**
 * Lays out the timed (non all-day) [events] that intersect [day] into [EventBlock]s, packing
 * overlaps and flagging midnight continuations. Events are clipped to the day; all-day events are
 * excluded here and surfaced by [allDayEventsForDay].
 */
fun layoutTimedEvents(
    events: List<DeviceCalendarEvent>,
    day: LocalDate,
    zone: ZoneId,
): List<EventBlock> {
    data class Clipped(val event: DeviceCalendarEvent, val startSec: Long, val endSec: Long)

    val clipped = events
        .asSequence()
        .filter { !it.allDay }
        .mapNotNull { event ->
            clampToDaySeconds(event.startUtcMs, event.endUtcMs, day, zone)
                ?.let { (s, e) -> Clipped(event, s, e) }
        }
        // Stable order: earliest start first, then longest, then event id for determinism.
        .sortedWith(compareBy({ it.startSec }, { -it.endSec }, { it.event.instanceId }))
        .toList()

    if (clipped.isEmpty()) return emptyList()

    val packing = packOverlaps(clipped.map { it.startSec to it.endSec })
    val dayStartMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEndMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    return clipped.mapIndexed { i, c ->
        val startFraction = c.startSec.toFloat() / SECONDS_PER_DAY
        val rawHeight = (c.endSec - c.startSec).toFloat() / SECONDS_PER_DAY
        val (col, colCount) = packing[i]
        EventBlock(
            event = c.event,
            startFraction = startFraction.coerceIn(0f, 1f),
            heightFraction = rawHeight.coerceIn(MIN_BLOCK_HEIGHT_FRACTION, 1f),
            column = col,
            columnCount = colCount,
            continuesBefore = c.event.startUtcMs < dayStartMs,
            continuesAfter = c.event.endUtcMs > dayEndMs,
        )
    }
}

/**
 * All-day events covering [day]. The provider expresses all-day boundaries as UTC midnight, so
 * coverage is computed in UTC: an event covers [day] when its UTC start date is on/before [day]
 * and its UTC end date (exclusive) is after [day].
 */
fun allDayEventsForDay(
    events: List<DeviceCalendarEvent>,
    day: LocalDate,
): List<DeviceCalendarEvent> = events.filter { event ->
    if (!event.allDay) return@filter false
    val startDate = Instant.ofEpochMilli(event.startUtcMs).atZone(ZoneOffset.UTC).toLocalDate()
    // END is exclusive midnight; guard equal begin/end (single-day) by treating <= start as +1 day.
    val endInstant = Instant.ofEpochMilli(event.endUtcMs).atZone(ZoneOffset.UTC).toLocalDate()
    val endDateExclusive = if (!endInstant.isAfter(startDate)) startDate.plusDays(1) else endInstant
    !day.isBefore(startDate) && day.isBefore(endDateExclusive)
}

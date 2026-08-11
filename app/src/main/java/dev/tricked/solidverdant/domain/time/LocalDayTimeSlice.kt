/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.domain.time

import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.TimeEntryType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** A half-open portion of a time entry clipped to one local calendar day. */
data class LocalDayTimeSlice(val date: LocalDate, val start: Instant, val endExclusive: Instant) {
    val seconds: Long get() = Duration.between(start, endExclusive).seconds.coerceAtLeast(0L)
}

/**
 * Resolves [entry] to its real half-open instant interval and clips it to [day] in [zone].
 *
 * Explicit end timestamps are authoritative. A positive duration is a fallback for cached
 * completed entries without an end. Solidtime sends zero or null duration for a running entry, so
 * those entries extend only to [now]. Invalid or empty intervals do not overlap any day.
 */
fun clipTimeEntryToLocalDay(entry: TimeEntry, day: LocalDate, zone: ZoneId, now: Instant): LocalDayTimeSlice? {
    val interval = resolveTimeEntryInterval(entry, now) ?: return null
    val dayStart = day.atStartOfDay(zone).toInstant()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
    val clippedStart = maxOf(interval.first, dayStart)
    val clippedEnd = minOf(interval.second, dayEnd)
    if (!clippedEnd.isAfter(clippedStart)) return null
    return LocalDayTimeSlice(day, clippedStart, clippedEnd)
}

/** Splits [entry] into one clipped slice for every local day it overlaps. */
fun timeEntryLocalDaySlices(entry: TimeEntry, zone: ZoneId, now: Instant): List<LocalDayTimeSlice> {
    val interval = resolveTimeEntryInterval(entry, now) ?: return emptyList()
    val slices = mutableListOf<LocalDayTimeSlice>()
    var day = interval.first.atZone(zone).toLocalDate()
    while (true) {
        val dayStart = day.atStartOfDay(zone).toInstant()
        if (!dayStart.isBefore(interval.second)) break
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
        val clippedStart = maxOf(interval.first, dayStart)
        val clippedEnd = minOf(interval.second, dayEnd)
        if (clippedEnd.isAfter(clippedStart)) {
            slices += LocalDayTimeSlice(day, clippedStart, clippedEnd)
        }
        day = day.plusDays(1)
    }
    return slices
}

/** Total resolved elapsed seconds, preferring an explicit end over a possibly stale duration. */
fun timeEntryDurationSeconds(entry: TimeEntry, now: Instant): Long? {
    val interval = resolveTimeEntryInterval(entry, now) ?: return null
    return Duration.between(interval.first, interval.second).seconds.coerceAtLeast(0L)
}

/** Whether [entry]'s half-open interval intersects the inclusive local-date filter bounds. */
fun timeEntryOverlapsLocalDateRange(entry: TimeEntry, startDate: LocalDate?, endDate: LocalDate?, zone: ZoneId, now: Instant): Boolean {
    if (startDate == null && endDate == null) return true
    val interval = resolveTimeEntryInterval(entry, now) ?: return false
    val rangeStart = startDate?.atStartOfDay(zone)?.toInstant()
    val rangeEndExclusive = endDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()
    if (rangeStart != null && rangeEndExclusive != null && !rangeEndExclusive.isAfter(rangeStart)) return false
    return (rangeStart == null || interval.second.isAfter(rangeStart)) &&
        (rangeEndExclusive == null || interval.first.isBefore(rangeEndExclusive))
}

fun resolveTimeEntryInterval(entry: TimeEntry, now: Instant): Pair<Instant, Instant>? {
    val start = parseTimeEntryInstant(entry.start) ?: return null
    val end = when {
        entry.end != null -> parseTimeEntryInstant(entry.end) ?: return null
        entry.duration != null && entry.duration > 0 -> start.plusSeconds(entry.duration.toLong())
        else -> now
    }
    return (start to end).takeIf { end.isAfter(start) }
}

/** Solidtime represents a running timer with no end and a null or zero duration. */
fun isRunningTimeEntry(entry: TimeEntry): Boolean = entry.type == TimeEntryType.WORK &&
    entry.end == null && (entry.duration ?: 0) <= 0

/** Breaks are completed calendar intervals and never count as active work timers. */
fun isBreakTimeEntry(entry: TimeEntry): Boolean = entry.type == TimeEntryType.BREAK

/** Work entries are the intervals included in tracked/billable totals. */
fun isWorkTimeEntry(entry: TimeEntry): Boolean = !isBreakTimeEntry(entry)

/** Whether the response carries either supported representation of a completed entry. */
fun isCompletedTimeEntry(entry: TimeEntry): Boolean = !isRunningTimeEntry(entry)

fun parseTimeEntryInstant(value: String): Instant? = runCatching { OffsetDateTime.parse(value).toInstant() }
    .recoverCatching { Instant.parse(value) }
    .recoverCatching { ZonedDateTime.parse(value).toInstant() }
    .getOrNull()

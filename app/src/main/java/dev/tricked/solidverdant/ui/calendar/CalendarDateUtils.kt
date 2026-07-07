/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import dev.tricked.solidverdant.data.model.TimeEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun monthGridWeeks(month: YearMonth, weekStart: DayOfWeek = DayOfWeek.MONDAY): List<List<LocalDate>> {
    val firstOfMonth = month.atDay(1)
    val lead = ((firstOfMonth.dayOfWeek.value - weekStart.value) + 7) % 7
    val gridStart = firstOfMonth.minusDays(lead.toLong())
    val lastOfMonth = month.atEndOfMonth()
    val gridEndExclusive = run {
        val trail = ((weekStart.value + 6 - lastOfMonth.dayOfWeek.value) + 7) % 7
        lastOfMonth.plusDays(trail.toLong() + 1)
    }
    val totalDays = java.time.temporal.ChronoUnit.DAYS.between(gridStart, gridEndExclusive).toInt()
    return (0 until totalDays)
        .map { gridStart.plusDays(it.toLong()) }
        .chunked(7)
}

/**
 * Resolves the local date an entry started on, or null when [TimeEntry.start] cannot be parsed.
 *
 * Returning null (rather than falling back to today) keeps malformed entries from polluting the
 * current day's totals; callers must skip null buckets. Mirrors the aggregator's safe startDate.
 */
fun entryLocalDate(entry: TimeEntry): LocalDate? = try {
    ZonedDateTime.parse(entry.start, DateTimeFormatter.ISO_DATE_TIME)
        .withZoneSameInstant(ZoneId.systemDefault())
        .toLocalDate()
} catch (_: Exception) {
    null
}

fun entryDurationSeconds(entry: TimeEntry, now: Instant): Long {
    entry.duration?.let { return it.toLong().coerceAtLeast(0) }
    return try {
        val start = ZonedDateTime.parse(entry.start, DateTimeFormatter.ISO_DATE_TIME).toInstant()
        val end = entry.end?.let {
            ZonedDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME).toInstant()
        } ?: now
        (end.epochSecond - start.epochSecond).coerceAtLeast(0)
    } catch (_: Exception) {
        0L
    }
}

fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    return "%dh %02dm".format(hours, minutes)
}

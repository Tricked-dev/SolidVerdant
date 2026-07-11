/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

enum class TrendGranularity { DAY, WEEK }

data class ProjectTotal(val projectId: String?, val projectName: String, val colorHex: String, val seconds: Long)

data class TrendBucket(val label: String, val startDate: LocalDate, val seconds: Long)

data class StatisticsSummary(
    val totalSeconds: Long,
    val entryCount: Int,
    val avgSecondsPerDay: Long,
    val billableSeconds: Long,
    val nonBillableSeconds: Long,
    val perProject: List<ProjectTotal>,
    val trend: List<TrendBucket>,
)

/** A single entry as surfaced in a drill-down list, clipped to the selected sub-range. */
data class DrillDownRow(
    val entryId: String,
    val description: String?,
    val projectId: String?,
    val projectName: String?,
    val colorHex: String,
    val taskName: String?,
    val startDate: LocalDate,
    val seconds: Long,
    val billable: Boolean,
)

object StatisticsAggregator {

    private const val NO_PROJECT_COLOR = "#9E9E9E"
    private val dayLabelFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

    /**
     * Applies the active [filters] to [entries] before aggregation. An empty dimension means no
     * restriction; the client dimension is resolved through each entry's project. Returns [entries]
     * unchanged when nothing is active so the common unfiltered path allocates nothing.
     */
    fun applyFilters(entries: List<TimeEntry>, projects: List<Project>, filters: StatFilters): List<TimeEntry> {
        if (!filters.isActive) return entries
        val clientByProject: Map<String, String?> = projects.associate { it.id to it.clientId }
        return entries.filter { e ->
            val projectOk = filters.projectIds.isEmpty() ||
                (e.projectId != null && e.projectId in filters.projectIds)
            val taskOk = filters.taskIds.isEmpty() ||
                (e.taskId != null && e.taskId in filters.taskIds)
            val clientOk = filters.clientIds.isEmpty() ||
                (e.projectId != null && clientByProject[e.projectId]?.let { it in filters.clientIds } == true)
            val tagOk = filters.tagIds.isEmpty() || e.tags.any { it.id in filters.tagIds }
            val billableOk = when (filters.billable) {
                BillableFilter.All -> true
                BillableFilter.Billable -> e.billable
                BillableFilter.NonBillable -> !e.billable
            }
            projectOk && taskOk && clientOk && tagOk && billableOk
        }
    }

    /** In-range contribution (seconds) of [e], or null when it does not overlap the window. */
    fun clippedSeconds(e: TimeEntry, zone: ZoneId, rangeStart: LocalDate, rangeEnd: LocalDate): Long? =
        clippedDailyBreakdown(e, zone, rangeStart, rangeEnd)?.sumOf { it.second }

    /**
     * Entries contributing to a tapped chart slice, each clipped to the selected [selStart]..[selEnd]
     * window (inclusive). When [matchProject] is true only entries whose project equals [projectId]
     * (including the null "no project" bucket) are kept, so tapping a donut segment lists just that
     * project; a trend bar passes [matchProject] false to list every entry within that time bucket.
     * Rows are ordered newest day first, then longest first.
     */
    fun drillDown(
        entries: List<TimeEntry>,
        projects: List<Project>,
        tasks: List<Task>,
        zone: ZoneId,
        selStart: LocalDate,
        selEnd: LocalDate,
        matchProject: Boolean,
        projectId: String?,
    ): List<DrillDownRow> {
        val projectById = projects.associateBy { it.id }
        val taskById = tasks.associateBy { it.id }
        return entries.mapNotNull { e ->
            if (matchProject && e.projectId != projectId) return@mapNotNull null
            val daily = clippedDailyBreakdown(e, zone, selStart, selEnd) ?: return@mapNotNull null
            val project = e.projectId?.let { projectById[it] }
            DrillDownRow(
                entryId = e.id,
                description = e.description,
                projectId = e.projectId,
                projectName = project?.name,
                colorHex = project?.color ?: NO_PROJECT_COLOR,
                taskName = e.taskId?.let { taskById[it]?.name },
                startDate = daily.first().first,
                seconds = daily.sumOf { it.second },
                billable = e.billable,
            )
        }.sortedWith(
            compareByDescending<DrillDownRow> { it.startDate }.thenByDescending { it.seconds },
        )
    }

    fun compute(
        entries: List<TimeEntry>,
        projects: List<Project>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        zone: ZoneId,
        granularity: TrendGranularity,
    ): StatisticsSummary {
        val projectById = projects.associateBy { it.id }

        // Per-entry clipped total plus its per-day breakdown within the range. An entry that begins
        // before rangeStart or ends after rangeEnd only contributes the overlapping seconds, and a
        // multi-day entry has those seconds split across each day it actually spans.
        data class Counted(val seconds: Long, val entry: TimeEntry, val daily: List<Pair<LocalDate, Long>>)

        val counted = entries.mapNotNull { e ->
            val daily = clippedDailyBreakdown(e, zone, rangeStart, rangeEnd) ?: return@mapNotNull null
            Counted(daily.sumOf { it.second }, e, daily)
        }

        val totalSeconds = counted.sumOf { it.seconds }
        val billable = counted.filter { it.entry.billable }.sumOf { it.seconds }
        val nonBillable = totalSeconds - billable

        val perProject = counted
            .groupBy { it.entry.projectId }
            .map { (pid, rows) ->
                val project = pid?.let { projectById[it] }
                ProjectTotal(
                    projectId = pid,
                    projectName = project?.name ?: if (pid == null) "No project" else "Unknown",
                    colorHex = project?.color ?: NO_PROJECT_COLOR,
                    seconds = rows.sumOf { it.seconds },
                )
            }
            .sortedWith(compareByDescending<ProjectTotal> { it.seconds }.thenBy { it.projectName })

        val days = ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1
        val avgPerDay = if (days > 0) totalSeconds / days else totalSeconds

        val trend = buildTrend(counted.flatMap { it.daily }, rangeStart, rangeEnd, granularity)

        return StatisticsSummary(
            totalSeconds = totalSeconds,
            entryCount = counted.size,
            avgSecondsPerDay = avgPerDay,
            billableSeconds = billable,
            nonBillableSeconds = nonBillable,
            perProject = perProject,
            trend = trend,
        )
    }

    /**
     * Clips an entry's [start, end) interval to the inclusive [rangeStart, rangeEnd] window (in
     * [zone]) and splits the overlapping seconds across every local day it spans.
     *
     * Returns null when the entry cannot be resolved to a finite interval (unparseable start, or an
     * active entry with neither duration nor end) or has no overlap with the range. The end is taken
     * from [TimeEntry.duration] when present (start + duration), otherwise from [TimeEntry.end].
     * A zero-length entry whose instant falls inside the range yields a single 0-second day so it
     * still counts and attributes to the correct project/day. The returned per-day seconds sum to
     * the entry's total in-range contribution.
     */
    private fun clippedDailyBreakdown(
        e: TimeEntry,
        zone: ZoneId,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ): List<Pair<LocalDate, Long>>? {
        val startInstant = try {
            Instant.parse(e.start)
        } catch (t: Throwable) {
            return null
        }
        val endInstant = when {
            e.duration != null -> startInstant.plusSeconds(e.duration.toLong().coerceAtLeast(0))
            e.end != null -> try {
                Instant.parse(e.end)
            } catch (t: Throwable) {
                return null
            }
            else -> return null
        }

        val rangeStartInstant = rangeStart.atStartOfDay(zone).toInstant()
        val rangeEndExclusive = rangeEnd.plusDays(1).atStartOfDay(zone).toInstant()

        // Zero- or negative-length entry: attribute 0 seconds on its start day if that day is in range.
        if (!endInstant.isAfter(startInstant)) {
            if (startInstant < rangeStartInstant || !startInstant.isBefore(rangeEndExclusive)) return null
            return listOf(startInstant.atZone(zone).toLocalDate() to 0L)
        }

        val clipStart = maxOf(startInstant, rangeStartInstant)
        val clipEnd = minOf(endInstant, rangeEndExclusive)
        if (!clipEnd.isAfter(clipStart)) return null

        val out = mutableListOf<Pair<LocalDate, Long>>()
        var cursor = clipStart
        while (cursor < clipEnd) {
            val date = cursor.atZone(zone).toLocalDate()
            val nextDayStart = date.plusDays(1).atStartOfDay(zone).toInstant()
            val segmentEnd = minOf(nextDayStart, clipEnd)
            out += date to (segmentEnd.epochSecond - cursor.epochSecond)
            cursor = segmentEnd
        }
        return out
    }

    private fun buildTrend(
        rows: List<Pair<LocalDate, Long>>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        granularity: TrendGranularity,
    ): List<TrendBucket> = when (granularity) {
        TrendGranularity.DAY -> {
            val byDay = rows.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            generateSequence(rangeStart) { if (it < rangeEnd) it.plusDays(1) else null }
                .map { d -> TrendBucket(d.format(dayLabelFmt), d, byDay[d] ?: 0L) }
                .toList()
        }
        TrendGranularity.WEEK -> {
            val wf = WeekFields.ISO
            val byWeekStart = rows.groupBy(
                { it.first.with(wf.dayOfWeek(), 1) },
                { it.second },
            ).mapValues { it.value.sum() }
            val firstWeek = rangeStart.with(wf.dayOfWeek(), 1)
            generateSequence(firstWeek) { it.plusWeeks(1) }
                .takeWhile { it <= rangeEnd }
                .map { ws ->
                    val week = ws.get(wf.weekOfWeekBasedYear())
                    // Include the (week-based) year so labels don't collide across year
                    // boundaries, e.g. W52 of 2025 vs W52 of 2026 in a multi-year range.
                    val yy = ws.get(wf.weekBasedYear()) % 100
                    TrendBucket("W$week '%02d".format(yy), ws, byWeekStart[ws] ?: 0L)
                }
                .toList()
        }
    }
}

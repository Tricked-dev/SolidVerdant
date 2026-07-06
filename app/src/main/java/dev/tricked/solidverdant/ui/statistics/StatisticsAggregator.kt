package dev.tricked.solidverdant.ui.statistics

import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.TimeEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

enum class TrendGranularity { DAY, WEEK }

data class ProjectTotal(
    val projectId: String?,
    val projectName: String,
    val colorHex: String,
    val seconds: Long,
)

data class TrendBucket(
    val label: String,
    val startDate: LocalDate,
    val seconds: Long,
)

data class StatisticsSummary(
    val totalSeconds: Long,
    val entryCount: Int,
    val avgSecondsPerDay: Long,
    val billableSeconds: Long,
    val nonBillableSeconds: Long,
    val perProject: List<ProjectTotal>,
    val trend: List<TrendBucket>,
)

object StatisticsAggregator {

    private const val NO_PROJECT_COLOR = "#9E9E9E"
    private val dayLabelFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

    fun compute(
        entries: List<TimeEntry>,
        projects: List<Project>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        zone: ZoneId,
        granularity: TrendGranularity,
    ): StatisticsSummary {
        val projectById = projects.associateBy { it.id }

        data class Counted(val date: LocalDate, val seconds: Long, val entry: TimeEntry)

        val counted = entries.mapNotNull { e ->
            val secs = effectiveSeconds(e) ?: return@mapNotNull null
            val date = startDate(e, zone) ?: return@mapNotNull null
            if (date < rangeStart || date > rangeEnd) return@mapNotNull null
            Counted(date, secs, e)
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

        val trend = buildTrend(counted.map { it.date to it.seconds }, rangeStart, rangeEnd, granularity)

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

    private fun effectiveSeconds(e: TimeEntry): Long? {
        e.duration?.let { return it.toLong() }
        val end = e.end ?: return null
        return try {
            val s = Instant.parse(e.start)
            val en = Instant.parse(end)
            (en.epochSecond - s.epochSecond).coerceAtLeast(0)
        } catch (t: Throwable) {
            null
        }
    }

    private fun startDate(e: TimeEntry, zone: ZoneId): LocalDate? = try {
        Instant.parse(e.start).atZone(zone).toLocalDate()
    } catch (t: Throwable) {
        null
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
                { it.first.with(wf.dayOfWeek(), 1) }, { it.second }
            ).mapValues { it.value.sum() }
            val firstWeek = rangeStart.with(wf.dayOfWeek(), 1)
            generateSequence(firstWeek) { it.plusWeeks(1) }
                .takeWhile { it <= rangeEnd }
                .map { ws ->
                    val week = ws.get(wf.weekOfWeekBasedYear())
                    TrendBucket("W$week", ws, byWeekStart[ws] ?: 0L)
                }
                .toList()
        }
    }
}

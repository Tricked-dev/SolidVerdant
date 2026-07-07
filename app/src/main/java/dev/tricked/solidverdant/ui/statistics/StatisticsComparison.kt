package dev.tricked.solidverdant.ui.statistics

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * The immediately preceding equivalent-length window for [range].
 *
 * The previous period has the same number of inclusive days and ends the day before [range] starts,
 * so a 7-day range compares against the prior 7 days and a full month compares against the same
 * number of days directly before it. This is deterministic and independent of calendar shape, which
 * keeps tiny or partial current ranges from producing overlapping or zero-length comparison windows.
 */
fun previousPeriod(range: ClosedRange<LocalDate>): ClosedRange<LocalDate> {
    val days = ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1
    val prevEnd = range.start.minusDays(1)
    val prevStart = range.start.minusDays(days)
    return prevStart..prevEnd
}

/** A single metric measured across the current and previous periods. */
data class MetricDelta(
    val current: Long,
    val previous: Long,
) {
    val absoluteDelta: Long get() = current - previous

    /**
     * Percentage change from [previous] to [current], or null when there is no prior baseline to
     * grow from (an empty previous period). Callers render null as "new"/no-percentage rather than
     * dividing by zero or reporting a misleading infinite jump.
     */
    fun percentChange(): Double? =
        if (previous == 0L) null else (current - previous) * 100.0 / previous
}

/** How one project's tracked time moved between the two periods. */
data class ProjectChange(
    val projectId: String?,
    val projectName: String?,
    val colorHex: String,
    val current: Long,
    val previous: Long,
) {
    val delta: Long get() = current - previous
}

/** Aggregated previous-period comparison surfaced under the KPI grid. */
data class PeriodComparison(
    val total: MetricDelta,
    val billable: MetricDelta,
    val avgPerDay: MetricDelta,
    val topChanges: List<ProjectChange>,
    val previousStart: LocalDate,
    val previousEnd: LocalDate,
) {
    val previousHasData: Boolean get() = total.previous > 0L || billable.previous > 0L
}

/**
 * Builds the [PeriodComparison] from two already-computed summaries plus the previous window's
 * dates. Project changes union the projects present in either period (so a project that vanished or
 * newly appeared still shows), ranked by the magnitude of their movement.
 */
fun computeComparison(
    current: StatisticsSummary,
    previous: StatisticsSummary,
    previousRange: ClosedRange<LocalDate>,
    maxProjectChanges: Int = 5,
): PeriodComparison {
    val currentByProject = current.perProject.associateBy { it.projectId }
    val previousByProject = previous.perProject.associateBy { it.projectId }
    val ids = LinkedHashSet<String?>().apply {
        current.perProject.forEach { add(it.projectId) }
        previous.perProject.forEach { add(it.projectId) }
    }
    val changes = ids.map { id ->
        val cur = currentByProject[id]
        val prev = previousByProject[id]
        ProjectChange(
            projectId = id,
            projectName = cur?.projectName ?: prev?.projectName,
            colorHex = cur?.colorHex ?: prev?.colorHex ?: "#9E9E9E",
            current = cur?.seconds ?: 0L,
            previous = prev?.seconds ?: 0L,
        )
    }
        .filter { it.delta != 0L }
        .sortedWith(compareByDescending<ProjectChange> { abs(it.delta) }.thenBy { it.projectName ?: "" })
        .take(maxProjectChanges)

    return PeriodComparison(
        total = MetricDelta(current.totalSeconds, previous.totalSeconds),
        billable = MetricDelta(current.billableSeconds, previous.billableSeconds),
        avgPerDay = MetricDelta(current.avgSecondsPerDay, previous.avgSecondsPerDay),
        topChanges = changes,
        previousStart = previousRange.start,
        previousEnd = previousRange.endInclusive,
    )
}

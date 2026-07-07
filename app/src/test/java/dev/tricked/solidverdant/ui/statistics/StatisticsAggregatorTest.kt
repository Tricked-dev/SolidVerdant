package dev.tricked.solidverdant.ui.statistics

import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatisticsAggregatorTest {

    private val utc = ZoneId.of("UTC")

    private fun entry(
        id: String,
        start: String,
        end: String? = null,
        duration: Int? = null,
        projectId: String? = null,
        billable: Boolean = false,
    ) = TimeEntry(
        id = id, userId = "u", start = start, end = end, duration = duration,
        projectId = projectId, billable = billable, organizationId = "org",
    )

    private val projects = listOf(
        Project(id = "p1", name = "Alpha", color = "#FF0000"),
        Project(id = "p2", name = "Beta", color = "#00FF00"),
    )

    @Test
    fun `uses duration when present`() {
        val e = entry("1", "2026-07-01T09:00:00Z", "2026-07-01T10:00:00Z", duration = 3600)
        val s = StatisticsAggregator.compute(
            listOf(e), projects, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"),
            utc, TrendGranularity.DAY,
        )
        assertEquals(3600L, s.totalSeconds)
        assertEquals(1, s.entryCount)
    }

    @Test
    fun `falls back to end minus start when duration null`() {
        val e = entry("1", "2026-07-01T09:00:00Z", "2026-07-01T09:30:00Z", duration = null)
        val s = StatisticsAggregator.compute(
            listOf(e), projects, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"),
            utc, TrendGranularity.DAY,
        )
        assertEquals(1800L, s.totalSeconds)
    }

    @Test
    fun `skips active entries`() {
        val active = entry("1", "2026-07-01T09:00:00Z", end = null, duration = null)
        val s = StatisticsAggregator.compute(
            listOf(active), projects, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"),
            utc, TrendGranularity.DAY,
        )
        assertEquals(0L, s.totalSeconds)
        assertEquals(0, s.entryCount)
    }

    @Test
    fun `groups by project and null becomes No project sorted desc`() {
        val entries = listOf(
            entry("1", "2026-07-01T09:00:00Z", duration = 100, projectId = "p1"),
            entry("2", "2026-07-01T10:00:00Z", duration = 500, projectId = "p2"),
            entry("3", "2026-07-01T11:00:00Z", duration = 300, projectId = null),
        )
        val s = StatisticsAggregator.compute(
            entries, projects, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"),
            utc, TrendGranularity.DAY,
        )
        assertEquals(listOf("Beta", "No project", "Alpha"), s.perProject.map { it.projectName })
        assertEquals(500L, s.perProject.first().seconds)
        assertEquals("#9E9E9E", s.perProject.first { it.projectId == null }.colorHex)
    }

    @Test
    fun `splits billable and non-billable`() {
        val entries = listOf(
            entry("1", "2026-07-01T09:00:00Z", duration = 100, billable = true),
            entry("2", "2026-07-01T10:00:00Z", duration = 400, billable = false),
        )
        val s = StatisticsAggregator.compute(
            entries, projects, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"),
            utc, TrendGranularity.DAY,
        )
        assertEquals(100L, s.billableSeconds)
        assertEquals(400L, s.nonBillableSeconds)
    }

    @Test
    fun `avg per day divides by inclusive day count`() {
        val entries = listOf(
            entry("1", "2026-07-01T09:00:00Z", duration = 600),
            entry("2", "2026-07-03T09:00:00Z", duration = 600),
        )
        val s = StatisticsAggregator.compute(
            entries, projects, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-03"),
            utc, TrendGranularity.DAY,
        )
        assertEquals(1200L, s.totalSeconds)
        assertEquals(400L, s.avgSecondsPerDay) // 1200 / 3 days
    }

    @Test
    fun `day trend has one bucket per day in range`() {
        val entries = listOf(
            entry("1", "2026-07-01T09:00:00Z", duration = 600),
            entry("2", "2026-07-03T09:00:00Z", duration = 300),
        )
        val s = StatisticsAggregator.compute(
            entries, projects, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-03"),
            utc, TrendGranularity.DAY,
        )
        assertEquals(3, s.trend.size)
        assertEquals(600L, s.trend[0].seconds)
        assertEquals(0L, s.trend[1].seconds)
        assertEquals(300L, s.trend[2].seconds)
    }

    @Test
    fun `week trend groups by ISO week`() {
        val entries = listOf(
            entry("1", "2026-07-01T09:00:00Z", duration = 600), // W27
            entry("2", "2026-07-08T09:00:00Z", duration = 300), // W28
        )
        val s = StatisticsAggregator.compute(
            entries, projects, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-12"),
            utc, TrendGranularity.WEEK,
        )
        assertEquals(2, s.trend.size)
        assertEquals(600L, s.trend[0].seconds)
        assertEquals(300L, s.trend[1].seconds)
    }

    @Test
    fun `ignores entries outside range attributed by start date`() {
        val entries = listOf(
            entry("1", "2026-06-30T23:00:00Z", duration = 999), // before range
            entry("2", "2026-07-01T09:00:00Z", duration = 100),
        )
        val s = StatisticsAggregator.compute(
            entries, projects, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"),
            utc, TrendGranularity.DAY,
        )
        assertEquals(100L, s.totalSeconds)
    }

    @Test
    fun `clips entry starting before range to only the in-range seconds`() {
        // Starts 23:00 the day before, runs 2h -> 1h before range, 1h inside. Old code dropped it
        // entirely (start day out of range); it must now contribute the in-range hour.
        val e = entry("1", "2026-06-30T23:00:00Z", duration = 7200)
        val s = StatisticsAggregator.compute(
            listOf(e), projects, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"),
            utc, TrendGranularity.DAY,
        )
        assertEquals(3600L, s.totalSeconds)
        assertEquals(1, s.entryCount)
        assertEquals(3600L, s.trend[0].seconds)
    }

    @Test
    fun `clips entry extending past range end`() {
        // Starts 23:00 on the last day, runs 2h -> only 1h falls within the range.
        val e = entry("1", "2026-07-01T23:00:00Z", duration = 7200)
        val s = StatisticsAggregator.compute(
            listOf(e), projects, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"),
            utc, TrendGranularity.DAY,
        )
        assertEquals(3600L, s.totalSeconds)
    }

    @Test
    fun `splits multi-day entry across the days it spans`() {
        // 22:00 -> 02:00 next day: 2h on Jul 1, 2h on Jul 2.
        val e = entry("1", "2026-07-01T22:00:00Z", "2026-07-02T02:00:00Z", duration = null)
        val s = StatisticsAggregator.compute(
            listOf(e), projects, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-02"),
            utc, TrendGranularity.DAY,
        )
        assertEquals(4 * 3600L, s.totalSeconds)
        assertEquals(2, s.trend.size)
        assertEquals(2 * 3600L, s.trend[0].seconds)
        assertEquals(2 * 3600L, s.trend[1].seconds)
    }

    @Test
    fun `week labels are year-aware across a year boundary`() {
        val entries = listOf(
            entry("1", "2025-12-29T09:00:00Z", duration = 600), // W1 2026 (ISO week-based year)
            entry("2", "2026-12-28T09:00:00Z", duration = 300), // W53 2026
        )
        val s = StatisticsAggregator.compute(
            entries, projects, LocalDate.parse("2025-12-29"), LocalDate.parse("2026-12-31"),
            utc, TrendGranularity.WEEK,
        )
        // Every label must be unique despite spanning a year boundary.
        assertEquals(s.trend.map { it.label }.toSet().size, s.trend.size)
        assertTrue(s.trend.first().label.contains("'"))
    }

    @Test
    fun `empty input yields zero summary`() {
        val s = StatisticsAggregator.compute(
            emptyList(), projects, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"),
            utc, TrendGranularity.DAY,
        )
        assertEquals(0L, s.totalSeconds)
        assertEquals(0, s.entryCount)
        assertEquals(emptyList<ProjectTotal>(), s.perProject)
    }
}

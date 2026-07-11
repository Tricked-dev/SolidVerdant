/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class StatisticsAggregatorTest {

    private val utc = ZoneId.of("UTC")

    // Regression guard: the existing tests call this Monday-start wrapper so their expected values
    // must stay byte-identical to the pre-policy (WeekFields.ISO) behaviour. Sunday-start / non-UTC
    // cases call StatisticsAggregator.compute directly with an explicit firstDayOfWeek.
    private fun compute(
        entries: List<TimeEntry>,
        projects: List<Project>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        zone: ZoneId,
        granularity: TrendGranularity,
    ) = StatisticsAggregator.compute(
        entries = entries,
        projects = projects,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        zone = zone,
        granularity = granularity,
        firstDayOfWeek = DayOfWeek.MONDAY,
    )

    private fun entry(
        id: String,
        start: String,
        end: String? = null,
        duration: Int? = null,
        projectId: String? = null,
        billable: Boolean = false,
    ) = TimeEntry(
        id = id,
        userId = "u",
        start = start,
        end = end,
        duration = duration,
        projectId = projectId,
        billable = billable,
        organizationId = "org",
    )

    private val projects = listOf(
        Project(id = "p1", name = "Alpha", color = "#FF0000"),
        Project(id = "p2", name = "Beta", color = "#00FF00"),
    )

    @Test
    fun `uses duration when present`() {
        val e = entry("1", "2026-07-01T09:00:00Z", "2026-07-01T10:00:00Z", duration = 3600)
        val s = compute(
            listOf(e),
            projects,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-01"),
            utc,
            TrendGranularity.DAY,
        )
        assertEquals(3600L, s.totalSeconds)
        assertEquals(1, s.entryCount)
    }

    @Test
    fun `falls back to end minus start when duration null`() {
        val e = entry("1", "2026-07-01T09:00:00Z", "2026-07-01T09:30:00Z", duration = null)
        val s = compute(
            listOf(e),
            projects,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-01"),
            utc,
            TrendGranularity.DAY,
        )
        assertEquals(1800L, s.totalSeconds)
    }

    @Test
    fun `skips active entries`() {
        val active = entry("1", "2026-07-01T09:00:00Z", end = null, duration = null)
        val s = compute(
            listOf(active),
            projects,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-01"),
            utc,
            TrendGranularity.DAY,
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
        val s = compute(
            entries,
            projects,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-01"),
            utc,
            TrendGranularity.DAY,
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
        val s = compute(
            entries,
            projects,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-01"),
            utc,
            TrendGranularity.DAY,
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
        val s = compute(
            entries,
            projects,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-03"),
            utc,
            TrendGranularity.DAY,
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
        val s = compute(
            entries,
            projects,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-03"),
            utc,
            TrendGranularity.DAY,
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
        val s = compute(
            entries,
            projects,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-12"),
            utc,
            TrendGranularity.WEEK,
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
        val s = compute(
            entries,
            projects,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-01"),
            utc,
            TrendGranularity.DAY,
        )
        assertEquals(100L, s.totalSeconds)
    }

    @Test
    fun `clips entry starting before range to only the in-range seconds`() {
        // Starts 23:00 the day before, runs 2h -> 1h before range, 1h inside. Old code dropped it
        // entirely (start day out of range); it must now contribute the in-range hour.
        val e = entry("1", "2026-06-30T23:00:00Z", duration = 7200)
        val s = compute(
            listOf(e),
            projects,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-01"),
            utc,
            TrendGranularity.DAY,
        )
        assertEquals(3600L, s.totalSeconds)
        assertEquals(1, s.entryCount)
        assertEquals(3600L, s.trend[0].seconds)
    }

    @Test
    fun `clips entry extending past range end`() {
        // Starts 23:00 on the last day, runs 2h -> only 1h falls within the range.
        val e = entry("1", "2026-07-01T23:00:00Z", duration = 7200)
        val s = compute(
            listOf(e),
            projects,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-01"),
            utc,
            TrendGranularity.DAY,
        )
        assertEquals(3600L, s.totalSeconds)
    }

    @Test
    fun `splits multi-day entry across the days it spans`() {
        // 22:00 -> 02:00 next day: 2h on Jul 1, 2h on Jul 2.
        val e = entry("1", "2026-07-01T22:00:00Z", "2026-07-02T02:00:00Z", duration = null)
        val s = compute(
            listOf(e),
            projects,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-02"),
            utc,
            TrendGranularity.DAY,
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
        val s = compute(
            entries,
            projects,
            LocalDate.parse("2025-12-29"),
            LocalDate.parse("2026-12-31"),
            utc,
            TrendGranularity.WEEK,
        )
        // Every label must be unique despite spanning a year boundary.
        assertEquals(s.trend.map { it.label }.toSet().size, s.trend.size)
        assertTrue(s.trend.first().label.contains("'"))
    }

    @Test
    fun `week trend buckets on a sunday week start`() {
        // Sun 2026-07-05 and the following Sun 2026-07-12 open two Sunday-start weeks. An entry on
        // Sat 2026-07-11 belongs to the week that began Sun 2026-07-05 (not a Monday-start bucket).
        val entries = listOf(
            entry("1", "2026-07-06T09:00:00Z", duration = 600), // Mon in week starting Sun 07-05
            entry("2", "2026-07-11T09:00:00Z", duration = 300), // Sat, still week starting Sun 07-05
            entry("3", "2026-07-13T09:00:00Z", duration = 900), // Mon in week starting Sun 07-12
        )
        val s = StatisticsAggregator.compute(
            entries = entries,
            projects = projects,
            rangeStart = LocalDate.parse("2026-07-05"),
            rangeEnd = LocalDate.parse("2026-07-18"),
            zone = utc,
            granularity = TrendGranularity.WEEK,
            firstDayOfWeek = DayOfWeek.SUNDAY,
        )
        assertEquals(2, s.trend.size)
        // Buckets start on Sundays, proving the Sunday-start grouping key.
        assertEquals(LocalDate.parse("2026-07-05"), s.trend[0].startDate)
        assertEquals(LocalDate.parse("2026-07-12"), s.trend[1].startDate)
        assertEquals(900L, s.trend[0].seconds) // 600 + 300 in the first Sunday-start week
        assertEquals(900L, s.trend[1].seconds)
    }

    @Test
    fun `clips across a spring-forward DST transition in Europe Amsterdam`() {
        // Amsterdam springs forward 2026-03-29 02:00 -> 03:00 (CET->CEST), so that local day is 23h.
        // An entry from 2026-03-28 23:00 local (22:00Z) to 2026-03-29 04:00 local (02:00Z) spans the
        // gap. Day boundaries follow the profile zone: 1h on the 28th, then the rest on the 29th
        // whose wall-clock 00:00->04:00 is only 3 real hours because of the skipped hour.
        val amsterdam = ZoneId.of("Europe/Amsterdam")
        val e = entry("1", "2026-03-28T22:00:00Z", "2026-03-29T02:00:00Z", duration = null)
        val s = StatisticsAggregator.compute(
            entries = listOf(e),
            projects = projects,
            rangeStart = LocalDate.parse("2026-03-28"),
            rangeEnd = LocalDate.parse("2026-03-29"),
            zone = amsterdam,
            granularity = TrendGranularity.DAY,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        // Total is the wall-to-wall real duration: 22:00Z -> 02:00Z = 4h.
        assertEquals(4 * 3600L, s.totalSeconds)
        assertEquals(2, s.trend.size)
        assertEquals(LocalDate.parse("2026-03-28"), s.trend[0].startDate)
        // 23:00->00:00 local on the 28th = 1h.
        assertEquals(3600L, s.trend[0].seconds)
        // 00:00->04:00 local on the 29th spans the skipped 02:00->03:00 hour, so only 3 real hours.
        assertEquals(3 * 3600L, s.trend[1].seconds)
    }

    @Test
    fun `profile zone day boundary attributes a late-UTC entry to the next local day`() {
        // 2026-07-01T23:30Z is 2026-07-02T08:30 in Tokyo (UTC+9). With a Tokyo profile zone the
        // entry must land entirely on Jul 2, not Jul 1 as a naive UTC-day attribution would.
        val tokyo = ZoneId.of("Asia/Tokyo")
        val e = entry("1", "2026-07-01T23:30:00Z", duration = 3600)
        val s = StatisticsAggregator.compute(
            entries = listOf(e),
            projects = projects,
            rangeStart = LocalDate.parse("2026-07-01"),
            rangeEnd = LocalDate.parse("2026-07-02"),
            zone = tokyo,
            granularity = TrendGranularity.DAY,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        assertEquals(3600L, s.totalSeconds)
        assertEquals(2, s.trend.size)
        assertEquals(LocalDate.parse("2026-07-01"), s.trend[0].startDate)
        assertEquals(0L, s.trend[0].seconds) // nothing on Jul 1 (local)
        assertEquals(3600L, s.trend[1].seconds) // all on Jul 2 (local)
    }

    @Test
    fun `empty input yields zero summary`() {
        val s = compute(
            emptyList(),
            projects,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-01"),
            utc,
            TrendGranularity.DAY,
        )
        assertEquals(0L, s.totalSeconds)
        assertEquals(0, s.entryCount)
        assertEquals(emptyList<ProjectTotal>(), s.perProject)
    }
}

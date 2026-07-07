package dev.tricked.solidverdant.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class PeriodComparisonTest {

    private fun summary(
        total: Long,
        billable: Long,
        avg: Long,
        perProject: List<ProjectTotal>,
    ) = StatisticsSummary(
        totalSeconds = total,
        entryCount = perProject.size,
        avgSecondsPerDay = avg,
        billableSeconds = billable,
        nonBillableSeconds = total - billable,
        perProject = perProject,
        trend = emptyList(),
    )

    private fun pt(id: String?, name: String, seconds: Long) =
        ProjectTotal(projectId = id, projectName = name, colorHex = "#123456", seconds = seconds)

    @Test
    fun `previous period is the equivalent length window immediately before`() {
        val range = LocalDate.parse("2026-07-01")..LocalDate.parse("2026-07-07")
        val prev = previousPeriod(range)
        assertEquals(LocalDate.parse("2026-06-24"), prev.start)
        assertEquals(LocalDate.parse("2026-06-30"), prev.endInclusive)
    }

    @Test
    fun `previous period of a single day is the day before`() {
        val range = LocalDate.parse("2026-07-07")..LocalDate.parse("2026-07-07")
        val prev = previousPeriod(range)
        assertEquals(LocalDate.parse("2026-07-06"), prev.start)
        assertEquals(LocalDate.parse("2026-07-06"), prev.endInclusive)
    }

    @Test
    fun `previous period keeps the same inclusive day count for a month`() {
        val range = LocalDate.parse("2026-07-01")..LocalDate.parse("2026-07-31")
        val prev = previousPeriod(range)
        assertEquals(LocalDate.parse("2026-05-31"), prev.start)
        assertEquals(LocalDate.parse("2026-06-30"), prev.endInclusive)
        val curDays = ChronoUnit.DAYS.between(range.start, range.endInclusive)
        val prevDays = ChronoUnit.DAYS.between(prev.start, prev.endInclusive)
        assertEquals(curDays, prevDays)
    }

    @Test
    fun `metric delta computes absolute and percent change`() {
        val d = MetricDelta(current = 400, previous = 150)
        assertEquals(250, d.absoluteDelta)
        assertEquals(166.66, d.percentChange()!!, 0.1)
    }

    @Test
    fun `percent change is null with no baseline`() {
        assertNull(MetricDelta(current = 400, previous = 0).percentChange())
    }

    @Test
    fun `comparison ranks project changes by magnitude and drops unchanged`() {
        val prevRange = LocalDate.parse("2026-06-24")..LocalDate.parse("2026-06-30")
        val current = summary(
            total = 400, billable = 200, avg = 57,
            perProject = listOf(pt("p1", "Alpha", 300), pt("p2", "Beta", 100)),
        )
        val previous = summary(
            total = 250, billable = 100, avg = 35,
            perProject = listOf(pt("p1", "Alpha", 100), pt("p3", "Gamma", 100), pt("p2", "Beta", 50)),
        )
        val cmp = computeComparison(current, previous, prevRange)

        assertEquals(150, cmp.total.absoluteDelta)
        assertEquals(100, cmp.billable.absoluteDelta)
        assertTrue(cmp.previousHasData)
        assertEquals(LocalDate.parse("2026-06-24"), cmp.previousStart)

        // p1 +200, p2 +50, p3 -100 ; ranked by |delta|: p1(200), p3(100), p2(50)
        assertEquals(listOf("Alpha", "Gamma", "Beta"), cmp.topChanges.map { it.projectName })
        assertEquals(200, cmp.topChanges[0].delta)
        assertEquals(-100, cmp.topChanges[1].delta)
    }

    @Test
    fun `comparison drops projects with identical time in both periods`() {
        val prevRange = LocalDate.parse("2026-06-24")..LocalDate.parse("2026-06-30")
        val current = summary(400, 0, 0, listOf(pt("p1", "Alpha", 300), pt("p2", "Beta", 100)))
        val previous = summary(400, 0, 0, listOf(pt("p1", "Alpha", 300), pt("p2", "Beta", 100)))
        val cmp = computeComparison(current, previous, prevRange)
        assertTrue(cmp.topChanges.isEmpty())
    }

    @Test
    fun `previousHasData is false when the previous period is empty`() {
        val prevRange = LocalDate.parse("2026-06-24")..LocalDate.parse("2026-06-30")
        val current = summary(400, 200, 57, listOf(pt("p1", "Alpha", 400)))
        val previous = summary(0, 0, 0, emptyList())
        val cmp = computeComparison(current, previous, prevRange)
        assertFalse(cmp.previousHasData)
        assertNull(cmp.total.percentChange())
    }
}

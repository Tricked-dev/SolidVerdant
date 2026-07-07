package dev.tricked.solidverdant.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatRangeTest {

    // Wed 2026-07-08; ISO week starts Monday 2026-07-06
    private val today = LocalDate.parse("2026-07-08")

    @Test
    fun `this week is monday to today`() {
        val r = StatRange.ThisWeek.resolve(today)
        assertEquals(LocalDate.parse("2026-07-06"), r.start)
        assertEquals(today, r.endInclusive)
    }

    @Test
    fun `this month is first to today`() {
        val r = StatRange.ThisMonth.resolve(today)
        assertEquals(LocalDate.parse("2026-07-01"), r.start)
        assertEquals(today, r.endInclusive)
    }

    @Test
    fun `custom returns its own bounds`() {
        val r = StatRange.Custom(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")).resolve(today)
        assertEquals(LocalDate.parse("2026-01-01"), r.start)
        assertEquals(LocalDate.parse("2026-01-31"), r.endInclusive)
    }

    @Test fun `shortcut ranges resolve inclusively`() {
        assertEquals(LocalDate.parse("2026-07-02"), StatRange.Last7Days.resolve(today).start)
        assertEquals(LocalDate.parse("2026-06-29"), StatRange.LastWeek.resolve(today).start)
        assertEquals(LocalDate.parse("2026-07-05"), StatRange.LastWeek.resolve(today).endInclusive)
        assertEquals(LocalDate.parse("2026-06-01"), StatRange.PreviousMonth.resolve(today).start)
        assertEquals(LocalDate.parse("2026-06-30"), StatRange.PreviousMonth.resolve(today).endInclusive)
    }

    @Test
    fun `granularity is day for short ranges and week for long`() {
        assertEquals(
            TrendGranularity.DAY,
            granularityFor(LocalDate.parse("2026-07-01")..LocalDate.parse("2026-07-15")),
        )
        assertEquals(
            TrendGranularity.WEEK,
            granularityFor(LocalDate.parse("2026-01-01")..LocalDate.parse("2026-03-01")),
        )
    }
}

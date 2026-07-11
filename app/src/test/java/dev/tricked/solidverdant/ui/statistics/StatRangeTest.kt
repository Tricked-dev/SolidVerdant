/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StatRangeTest {

    // Wed 2026-07-08; ISO (Monday-start) week starts Monday 2026-07-06
    private val today = LocalDate.parse("2026-07-08")

    @Test
    fun `this week is monday to today`() {
        val r = StatRange.ThisWeek.resolve(today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.parse("2026-07-06"), r.start)
        assertEquals(today, r.endInclusive)
    }

    @Test
    fun `this month is first to today`() {
        val r = StatRange.ThisMonth.resolve(today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.parse("2026-07-01"), r.start)
        assertEquals(today, r.endInclusive)
    }

    @Test
    fun `custom returns its own bounds`() {
        val r = StatRange.Custom(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))
            .resolve(today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.parse("2026-01-01"), r.start)
        assertEquals(LocalDate.parse("2026-01-31"), r.endInclusive)
    }

    @Test fun `shortcut ranges resolve inclusively`() {
        assertEquals(LocalDate.parse("2026-07-02"), StatRange.Last7Days.resolve(today, DayOfWeek.MONDAY).start)
        assertEquals(LocalDate.parse("2026-06-29"), StatRange.LastWeek.resolve(today, DayOfWeek.MONDAY).start)
        assertEquals(LocalDate.parse("2026-07-05"), StatRange.LastWeek.resolve(today, DayOfWeek.MONDAY).endInclusive)
        assertEquals(LocalDate.parse("2026-06-01"), StatRange.PreviousMonth.resolve(today, DayOfWeek.MONDAY).start)
        assertEquals(LocalDate.parse("2026-06-30"), StatRange.PreviousMonth.resolve(today, DayOfWeek.MONDAY).endInclusive)
    }

    @Test
    fun `this week resolves with a sunday week start`() {
        // Wed 2026-07-08: the Sunday-start week began Sunday 2026-07-05.
        val r = StatRange.ThisWeek.resolve(today, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.parse("2026-07-05"), r.start)
        assertEquals(today, r.endInclusive)
    }

    @Test
    fun `last week resolves with a sunday week start`() {
        // This Sunday-start week began 2026-07-05, so last week is 2026-06-28..2026-07-04.
        val r = StatRange.LastWeek.resolve(today, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.parse("2026-06-28"), r.start)
        assertEquals(LocalDate.parse("2026-07-04"), r.endInclusive)
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

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

private const val LAST_7_DAYS_OFFSET = 6L
private const val MONTHLY_GRANULARITY_DAYS = 31L

// Minimal-days matching WeekFields.ISO so a Monday [firstDayOfWeek] reproduces ISO week starts
// exactly; only the first-day-of-week affects the week-START computation used here.
private const val WEEK_MIN_DAYS = 4

/** First local day (per [firstDayOfWeek]) of the week containing [date]. */
private fun weekStart(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
    date.with(WeekFields.of(firstDayOfWeek, WEEK_MIN_DAYS).dayOfWeek(), 1)

sealed interface StatRange {
    /**
     * Resolves this range to an inclusive [today]-relative window. Week-based variants start the
     * week on [firstDayOfWeek] (from the account [dev.tricked.solidverdant.domain.time.TemporalPolicy]);
     * non-week variants ignore it.
     */
    fun resolve(today: LocalDate, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate>

    data object Today : StatRange {
        override fun resolve(today: LocalDate, firstDayOfWeek: DayOfWeek) = today..today
    }

    data object Yesterday : StatRange {
        override fun resolve(today: LocalDate, firstDayOfWeek: DayOfWeek) = today.minusDays(1)..today.minusDays(1)
    }

    data object Last7Days : StatRange {
        override fun resolve(today: LocalDate, firstDayOfWeek: DayOfWeek) = today.minusDays(LAST_7_DAYS_OFFSET)..today
    }

    data object LastWeek : StatRange {
        override fun resolve(today: LocalDate, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate> {
            val thisWeekStart = weekStart(today, firstDayOfWeek)
            return thisWeekStart.minusWeeks(1)..thisWeekStart.minusDays(1)
        }
    }

    data object ThisWeek : StatRange {
        override fun resolve(today: LocalDate, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate> = weekStart(today, firstDayOfWeek)..today
    }

    data object ThisMonth : StatRange {
        override fun resolve(today: LocalDate, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate> = today.withDayOfMonth(1)..today
    }

    data object PreviousMonth : StatRange {
        override fun resolve(today: LocalDate, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate> {
            val month = today.withDayOfMonth(1).minusMonths(1)
            return month..month.withDayOfMonth(month.lengthOfMonth())
        }
    }

    data class Custom(val start: LocalDate, val end: LocalDate) : StatRange {
        init {
            require(!end.isBefore(start)) { "Custom range end must not precede start" }
        }
        override fun resolve(today: LocalDate, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate> = start..end
    }
}

fun granularityFor(range: ClosedRange<LocalDate>): TrendGranularity {
    val days = ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1
    return if (days <= MONTHLY_GRANULARITY_DAYS) TrendGranularity.DAY else TrendGranularity.WEEK
}

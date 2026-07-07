package dev.tricked.solidverdant.ui.statistics

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

sealed interface StatRange {
    fun resolve(today: LocalDate): ClosedRange<LocalDate>

    data object Today : StatRange {
        override fun resolve(today: LocalDate) = today..today
    }

    data object Yesterday : StatRange {
        override fun resolve(today: LocalDate) = today.minusDays(1)..today.minusDays(1)
    }

    data object Last7Days : StatRange {
        override fun resolve(today: LocalDate) = today.minusDays(6)..today
    }

    data object LastWeek : StatRange {
        override fun resolve(today: LocalDate): ClosedRange<LocalDate> {
            val thisMonday = today.with(WeekFields.ISO.dayOfWeek(), 1)
            return thisMonday.minusWeeks(1)..thisMonday.minusDays(1)
        }
    }

    data object ThisWeek : StatRange {
        override fun resolve(today: LocalDate): ClosedRange<LocalDate> {
            val monday = today.with(WeekFields.ISO.dayOfWeek(), 1)
            return monday..today
        }
    }

    data object ThisMonth : StatRange {
        override fun resolve(today: LocalDate): ClosedRange<LocalDate> =
            today.withDayOfMonth(1)..today
    }

    data object PreviousMonth : StatRange {
        override fun resolve(today: LocalDate): ClosedRange<LocalDate> {
            val month = today.withDayOfMonth(1).minusMonths(1)
            return month..month.withDayOfMonth(month.lengthOfMonth())
        }
    }

    data class Custom(val start: LocalDate, val end: LocalDate) : StatRange {
        init { require(!end.isBefore(start)) { "Custom range end must not precede start" } }
        override fun resolve(today: LocalDate): ClosedRange<LocalDate> = start..end
    }
}

fun granularityFor(range: ClosedRange<LocalDate>): TrendGranularity {
    val days = ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1
    return if (days <= 31) TrendGranularity.DAY else TrendGranularity.WEEK
}

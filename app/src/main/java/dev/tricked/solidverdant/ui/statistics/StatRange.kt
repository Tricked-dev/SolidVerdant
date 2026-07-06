package dev.tricked.solidverdant.ui.statistics

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

sealed interface StatRange {
    fun resolve(today: LocalDate): ClosedRange<LocalDate>

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

    data class Custom(val start: LocalDate, val end: LocalDate) : StatRange {
        override fun resolve(today: LocalDate): ClosedRange<LocalDate> = start..end
    }
}

fun granularityFor(range: ClosedRange<LocalDate>): TrendGranularity {
    val days = ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1
    return if (days <= 31) TrendGranularity.DAY else TrendGranularity.WEEK
}

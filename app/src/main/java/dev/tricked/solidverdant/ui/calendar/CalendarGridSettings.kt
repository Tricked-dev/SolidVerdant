/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

/** Persisted calendar density choices. The actual dp tokens live in the shared calendar layout. */
enum class CalendarGridDensity {
    COMPACT,
    COMFORTABLE,
    SPACIOUS,
}

data class CalendarGridSettings(
    val snapMinutes: Int = DEFAULT_SNAP_MINUTES,
    val startHour: Int = DEFAULT_START_HOUR,
    val endHour: Int = DEFAULT_END_HOUR,
    val density: CalendarGridDensity = CalendarGridDensity.COMFORTABLE,
) {
    val startMinute: Int get() = startHour * MINUTES_PER_HOUR
    val endMinute: Int get() = endHour * MINUTES_PER_HOUR
    val visibleMinutes: Int get() = endMinute - startMinute

    fun normalized(): CalendarGridSettings = copy(
        snapMinutes = snapMinutes.takeIf { it in SNAP_MINUTES } ?: DEFAULT_SNAP_MINUTES,
        startHour = startHour.coerceIn(MIN_START_HOUR, MAX_START_HOUR),
        endHour = endHour.coerceIn(MIN_END_HOUR, MAX_END_HOUR),
    ).let { settings ->
        if (settings.startHour < settings.endHour) {
            settings
        } else {
            settings.copy(
                startHour = DEFAULT_START_HOUR,
                endHour = DEFAULT_END_HOUR,
            )
        }
    }

    companion object {
        val SNAP_MINUTES: List<Int> = listOf(1, 5, 10, 15, 30, 60)
        const val DEFAULT_SNAP_MINUTES = 15
        const val DEFAULT_START_HOUR = 0
        const val DEFAULT_END_HOUR = 24
        const val MIN_START_HOUR = 0
        const val MAX_START_HOUR = 23
        const val MIN_END_HOUR = 1
        const val MAX_END_HOUR = 24
        const val MINUTES_PER_HOUR = 60
    }
}

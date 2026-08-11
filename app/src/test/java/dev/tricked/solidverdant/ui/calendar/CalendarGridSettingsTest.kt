/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CalendarGridSettingsTest {
    @Test
    fun invalidSettingsFallBackToSafeDefaults() {
        val normalized = CalendarGridSettings(
            snapMinutes = 17,
            startHour = 22,
            endHour = 4,
        ).normalized()

        assertEquals(CalendarGridSettings.DEFAULT_SNAP_MINUTES, normalized.snapMinutes)
        assertEquals(CalendarGridSettings.DEFAULT_START_HOUR, normalized.startHour)
        assertEquals(CalendarGridSettings.DEFAULT_END_HOUR, normalized.endHour)
    }

    @Test
    fun customVisibleHoursAndSnapIntervalDriveSelection() {
        val settings = CalendarGridSettings(snapMinutes = 30, startHour = 9, endHour = 17)
        val range = calendarTimeRangeForDrag(
            day = LocalDate.of(2026, 8, 11),
            startY = 48f,
            endY = 102f,
            gridHeightPx = 48f * 8f,
            zone = ZoneId.of("UTC"),
            settings = settings,
        )

        assertEquals("2026-08-11T10:00:00Z", range.start.toInstant().toString())
        assertEquals("2026-08-11T11:00:00Z", range.end.toInstant().toString())
    }

    @Test
    fun visibleDayBoundsUseActualElapsedTimeAcrossDst() {
        val day = LocalDate.of(2026, 3, 29)
        val grid = calendarGridBounds(
            day = day,
            zone = ZoneId.of("Europe/Amsterdam"),
            settings = CalendarGridSettings(startHour = 0, endHour = 24),
        )

        assertEquals(23 * 60 * 60L, grid.seconds)
        assertTrue(grid.end.isAfter(grid.start))
    }
}

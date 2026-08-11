/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class CalendarNowTest {

    @Test
    fun tickerDelayAlignsWithTheNextMinuteBoundary() {
        assertEquals(
            60_000L,
            millisUntilNextCalendarMinute(Instant.parse("2026-08-11T14:54:00Z").toEpochMilli()),
        )
        assertEquals(
            56_544L,
            millisUntilNextCalendarMinute(Instant.parse("2026-08-11T14:54:03.456Z").toEpochMilli()),
        )
    }

    @Test
    fun initialScrollKeepsTheCurrentTimeNearTheTopOfTheViewport() {
        val scrollHours = calendarInitialScrollHours(
            now = Instant.parse("2026-08-11T14:54:00Z"),
            zone = ZoneOffset.UTC,
            settings = CalendarGridSettings(startHour = 8, endHour = 18),
        )

        assertEquals(4.9, scrollHours, 0.001)
    }

    @Test
    fun initialScrollClampsBeforeAndAfterTheVisibleWindow() {
        val settings = CalendarGridSettings(startHour = 8, endHour = 18)

        assertEquals(
            0.0,
            calendarInitialScrollHours(Instant.parse("2026-08-11T07:00:00Z"), ZoneOffset.UTC, settings),
            0.0,
        )
        assertEquals(
            9.0,
            calendarInitialScrollHours(Instant.parse("2026-08-11T23:00:00Z"), ZoneOffset.UTC, settings),
            0.0,
        )
    }
}

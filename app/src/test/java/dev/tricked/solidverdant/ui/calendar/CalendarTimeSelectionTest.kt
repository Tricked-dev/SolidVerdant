/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CalendarTimeSelectionTest {
    private val day = LocalDate.of(2026, 7, 6)
    private val zone = ZoneOffset.UTC
    private val gridHeight = 48f * 24f

    @Test
    fun dragSnapsToQuarterHoursAndUsesChronologicalOrder() {
        val range = calendarTimeRangeForDrag(
            day = day,
            startY = gridHeight * 9.08f / 24f,
            endY = gridHeight * 10.14f / 24f,
            gridHeightPx = gridHeight,
            zone = zone,
        )

        assertEquals("2026-07-06T09:00:00Z", range.start.toInstant().toString())
        assertEquals("2026-07-06T10:15:00Z", range.end.toInstant().toString())
    }

    @Test
    fun upwardDragStillCreatesAnEarlierStart() {
        val range = calendarTimeRangeForDrag(
            day = day,
            startY = gridHeight * 11f / 24f,
            endY = gridHeight * 9f / 24f,
            gridHeightPx = gridHeight,
            zone = zone,
        )

        assertEquals("2026-07-06T09:00:00Z", range.start.toInstant().toString())
        assertEquals("2026-07-06T11:00:00Z", range.end.toInstant().toString())
    }

    @Test
    fun aShortDragStillProducesOneSlot() {
        val range = calendarTimeRangeForDrag(
            day = day,
            startY = gridHeight * 14.02f / 24f,
            endY = gridHeight * 14.04f / 24f,
            gridHeightPx = gridHeight,
            zone = zone,
        )

        assertEquals("2026-07-06T14:00:00Z", range.start.toInstant().toString())
        assertEquals("2026-07-06T14:15:00Z", range.end.toInstant().toString())
    }
}

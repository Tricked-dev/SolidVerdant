/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class EntryRetimingTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val start = ZonedDateTime.of(2026, 7, 6, 10, 0, 30, 0, zone)
    private val end = start.plusMinutes(45).plusSeconds(20)

    @Test fun `moving the start keeps the exact length including seconds`() {
        val newStart = start.withHour(9).withMinute(15).withSecond(0)
        val retimed = retimedEnd(previousStart = start, newStart = newStart, end = end)
        assertEquals(Duration.ofMinutes(45).plusSeconds(20), Duration.between(newStart, retimed))
    }

    @Test fun `moving the start date keeps the length across a DST change`() {
        val newStart = start.withDayOfMonth(29).withMonth(3)
        val retimed = retimedEnd(previousStart = start, newStart = newStart, end = end)
        assertEquals(Duration.between(start, end), Duration.between(newStart, retimed))
    }
}

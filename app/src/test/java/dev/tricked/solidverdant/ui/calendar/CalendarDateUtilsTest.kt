/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class CalendarDateUtilsTest {

    @Test
    fun monthGridWeeks_coversFullWeeksIncludingSpillover() {
        // July 2026: 1st is a Wednesday, 31 days.
        val weeks = monthGridWeeks(YearMonth.of(2026, 7), DayOfWeek.MONDAY)
        assertEquals(7, weeks.first().size)
        // First cell is Monday of the week containing July 1 -> June 29, 2026.
        assertEquals(LocalDate.of(2026, 6, 29), weeks.first().first())
        // Grid contains every day of July.
        val all = weeks.flatten()
        (1..31).forEach { d -> assert(all.contains(LocalDate.of(2026, 7, d))) }
    }

    @Test
    fun entryDurationSeconds_usesExplicitDuration() {
        val e = TimeEntry(
            id = "1",
            userId = "u",
            start = "2026-07-06T09:00:00Z",
            end = "2026-07-06T10:00:00Z",
            duration = 3600,
            organizationId = "o",
        )
        assertEquals(3600L, entryDurationSeconds(e, Instant.parse("2026-07-06T12:00:00Z")))
    }

    @Test
    fun entryDurationSeconds_computesActiveEntryFromNow() {
        val e = TimeEntry(
            id = "1",
            userId = "u",
            start = "2026-07-06T09:00:00Z",
            end = null,
            duration = null,
            organizationId = "o",
        )
        assertEquals(3600L, entryDurationSeconds(e, Instant.parse("2026-07-06T10:00:00Z")))
    }

    @Test
    fun entryLocalDate_parsesValidStart() {
        val e = TimeEntry(
            id = "1",
            userId = "u",
            start = "2026-07-06T09:00:00Z",
            end = null,
            duration = null,
            organizationId = "o",
        )
        assertEquals(LocalDate.of(2026, 7, 6), entryLocalDate(e))
    }

    @Test
    fun entryLocalDate_returnsNullOnUnparseableStart() {
        // Malformed start must NOT fall back to today (which would pollute today's totals).
        val e = TimeEntry(
            id = "1",
            userId = "u",
            start = "not-a-timestamp",
            end = null,
            duration = null,
            organizationId = "o",
        )
        assertNull(entryLocalDate(e))
    }

    @Test
    fun formatDuration_padsMinutes() {
        assertEquals("2h 05m", formatDuration(2 * 3600 + 5 * 60L))
        assertEquals("0h 00m", formatDuration(0))
    }
}

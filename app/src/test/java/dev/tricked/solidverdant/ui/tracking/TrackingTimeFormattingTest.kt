/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TrackingTimeFormattingTest {
    @Test
    fun `multi-day range includes both dates`() {
        val formatted = formatTimeRange(
            start = "2026-07-06T23:00:00Z",
            end = "2026-07-08T01:00:00Z",
            zone = ZoneOffset.UTC,
        )

        assertTrue(formatted, formatted.contains("6 Jul 2026"))
        assertTrue(formatted, formatted.contains("8 Jul 2026"))
        assertTrue(formatted, formatted.contains("23:00"))
        assertTrue(formatted, formatted.contains("01:00"))
    }

    @Test
    fun `range uses the account timezone for date boundaries`() {
        val formatted = formatTimeRange(
            start = "2026-07-06T23:30:00Z",
            end = "2026-07-07T00:30:00Z",
            zone = ZoneId.of("Asia/Tokyo"),
        )

        assertEquals("08:30 - 09:30", formatted)
    }

    @Test
    fun `history search compares entry starts in the account timezone`() {
        val entry = TimeEntry(
            id = "timezone-boundary",
            userId = "u",
            start = "2026-07-06T23:30:00Z",
            organizationId = "o",
        )

        assertEquals(LocalDate.of(2026, 7, 7), historyEntryStartDate(entry, ZoneId.of("Asia/Tokyo")))
        assertEquals(LocalDate.of(2026, 7, 6), historyEntryStartDate(entry, ZoneOffset.UTC))
    }

    @Test
    fun `history groups a completed multi-day entry into every overlapping account day`() {
        val entry = TimeEntry(
            id = "multi-day",
            userId = "u",
            start = "2026-07-06T23:00:00Z",
            end = "2026-07-08T01:00:00Z",
            duration = null,
            organizationId = "o",
        )

        val grouped = groupCompletedEntriesByLocalDay(
            listOf(entry),
            ZoneOffset.UTC,
            Instant.parse("2026-07-09T00:00:00Z"),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 8),
                LocalDate.of(2026, 7, 7),
                LocalDate.of(2026, 7, 6),
            ),
            grouped.keys.toList(),
        )
        assertTrue(grouped.values.all { it.single().id == entry.id })
    }
}

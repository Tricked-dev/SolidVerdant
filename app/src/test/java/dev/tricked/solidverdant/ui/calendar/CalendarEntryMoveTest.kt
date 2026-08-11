/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CalendarEntryMoveTest {
    @Test
    fun movingAnEntryPreservesItsDurationAcrossDays() {
        val entry = TimeEntry(
            id = "entry-1",
            userId = "user-1",
            organizationId = "org-1",
            start = "2026-08-11T23:30:00+02:00",
            end = "2026-08-12T01:00:00+02:00",
        )
        val targetStart = ZonedDateTime.of(2026, 8, 13, 8, 15, 0, 0, ZoneOffset.ofHours(2))

        val moved = calendarEntryRangeAt(entry, targetStart)

        assertNotNull(moved)
        assertEquals(targetStart, moved?.start)
        assertEquals(targetStart.plusMinutes(90), moved?.end)
    }

    @Test
    fun invalidOrRunningEntriesCannotBeMoved() {
        val running = TimeEntry(
            id = "running",
            userId = "user-1",
            organizationId = "org-1",
            start = "2026-08-11T08:00:00Z",
        )
        val invalid = running.copy(end = "2026-08-11T07:00:00Z")
        val target = ZonedDateTime.parse("2026-08-11T09:00:00Z")

        assertEquals(null, calendarEntryRangeAt(running, target))
        assertEquals(null, calendarEntryRangeAt(invalid, target))
    }

    @Test
    fun durationUsesExactInstantsForOffsetChanges() {
        val entry = TimeEntry(
            id = "entry-dst",
            userId = "user-1",
            organizationId = "org-1",
            start = "2026-10-25T01:30:00+02:00",
            end = "2026-10-25T02:30:00+01:00",
        )
        val targetStart = ZonedDateTime.parse("2026-10-26T09:00:00+01:00")

        val moved = requireNotNull(calendarEntryRangeAt(entry, targetStart))

        assertEquals(Duration.ofHours(2), Duration.between(moved.start, moved.end))
    }

    @Test
    fun resizingStartKeepsTheEndBoundary() {
        val entry = TimeEntry(
            id = "entry-start",
            userId = "user-1",
            organizationId = "org-1",
            start = "2026-08-11T09:00:00Z",
            end = "2026-08-11T10:00:00Z",
        )

        val resized = calendarEntryResizeRangeAt(
            entry = entry,
            edge = CalendarResizeEdge.START,
            boundary = ZonedDateTime.parse("2026-08-11T09:30:00Z"),
        )

        assertEquals("2026-08-11T09:30:00Z", resized?.start?.toInstant().toString())
        assertEquals("2026-08-11T10:00:00Z", resized?.end?.toInstant().toString())
    }

    @Test
    fun resizingEndAllowsTheLocalDayBoundaryButRejectsTooShortRanges() {
        val entry = TimeEntry(
            id = "entry-end",
            userId = "user-1",
            organizationId = "org-1",
            start = "2026-08-11T23:00:00Z",
            end = "2026-08-12T00:00:00Z",
        )

        val resized = calendarEntryResizeRangeAt(
            entry = entry,
            edge = CalendarResizeEdge.END,
            boundary = ZonedDateTime.parse("2026-08-12T01:00:00Z"),
        )
        val tooShort = calendarEntryResizeRangeAt(
            entry = entry,
            edge = CalendarResizeEdge.END,
            boundary = ZonedDateTime.parse("2026-08-11T23:10:00Z"),
        )

        assertEquals("2026-08-12T01:00:00Z", resized?.end?.toInstant().toString())
        assertEquals(null, tooShort)
    }
}

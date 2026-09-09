/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeEntryTimestampTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    @Test fun `local wall time is written as UTC seconds with a Z suffix`() {
        val local = ZonedDateTime.of(2026, 7, 6, 10, 30, 0, 0, amsterdam)
        assertEquals("2026-07-06T08:30:00Z", formatTimeEntryInstant(local))
    }

    @Test fun `sub-second precision is dropped so the string matches server echoes`() {
        val local = ZonedDateTime.of(2026, 7, 6, 10, 30, 15, 750_000_000, amsterdam)
        assertEquals("2026-07-06T08:30:15Z", formatTimeEntryInstant(local))
    }

    @Test fun `formatted value parses back to the same instant`() {
        val local = ZonedDateTime.of(2026, 3, 29, 2, 45, 0, 0, amsterdam)
        assertEquals(local.toInstant(), parseTimeEntryInstant(formatTimeEntryInstant(local)))
    }

    @Test fun `string order matches chronological order against server timestamps`() {
        val local = formatTimeEntryInstant(ZonedDateTime.of(2026, 7, 6, 10, 30, 0, 0, amsterdam))
        val sorted = listOf("2026-07-06T09:00:00Z", local, "2026-07-06T08:00:00Z").sortedDescending()
        assertEquals(listOf("2026-07-06T09:00:00Z", "2026-07-06T08:30:00Z", "2026-07-06T08:00:00Z"), sorted)
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * IsoTimes fast paths must return exactly what the ZonedDateTime.parse equivalents returned
 * before the optimization, for every ISO shape the solidtime API emits.
 */
class IsoTimesTest {

    private val samples = listOf(
        "2026-07-07T09:30:00Z",
        "2026-07-07T23:59:59Z",
        "2026-01-01T00:00:00Z",
        "2026-07-07T09:30:00+02:00",
        "2026-07-07T09:30:00.123Z",
        "2026-12-31T23:00:00-05:00",
    )

    @Test
    fun `localDate matches ZonedDateTime parse for API shapes`() {
        samples.forEach { iso ->
            assertEquals(iso, ZonedDateTime.parse(iso).toLocalDate(), IsoTimes.localDate(iso))
        }
    }

    @Test
    fun `hourMinute matches ZonedDateTime format for API shapes`() {
        val hhmm = DateTimeFormatter.ofPattern("HH:mm")
        samples.forEach { iso ->
            assertEquals(iso, ZonedDateTime.parse(iso).format(hhmm), IsoTimes.hourMinute(iso))
        }
    }

    @Test
    fun `garbage input returns null instead of throwing`() {
        listOf("", "not a date").forEach { bad ->
            assertNull("localDate($bad)", IsoTimes.localDate(bad))
            assertNull("hourMinute($bad)", IsoTimes.hourMinute(bad))
        }
        // Inputs with a readable date portion still yield the date (more lenient than the old
        // parse-or-null, which only matters for malformed input that never comes from the API).
        assertEquals(java.time.LocalDate.of(2026, 7, 7), IsoTimes.localDate("2026-07-07"))
        assertNull(IsoTimes.hourMinute("2026-07-07"))
    }
}

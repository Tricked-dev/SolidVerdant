/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SolidtimeTimestampsTest {
    @Test
    fun `positive and negative offsets preserve the represented instant`() {
        assertEquals("2026-08-08T08:00:00Z", SolidtimeTimestamps.utc("2026-08-08T10:00:00+02:00"))
        assertEquals("2026-08-08T15:30:00Z", SolidtimeTimestamps.utc("2026-08-08T09:30:00-06:00"))
    }

    @Test
    fun `fractional seconds are reduced to the Solidtime second precision contract`() {
        assertEquals("2026-08-08T08:00:00Z", SolidtimeTimestamps.utc("2026-08-08T08:00:00.987Z"))
    }

    @Test
    fun `invalid timestamps fail locally instead of producing a doomed request`() {
        assertThrows(java.time.format.DateTimeParseException::class.java) {
            SolidtimeTimestamps.utc("2026-08-08 10:00")
        }
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The home-screen widget shows minute-resolution elapsed time (HH:MM) because it cannot tick
 * every second in the background. These tests pin that formatting and the negative-duration
 * guard.
 */
class WidgetElapsedFormatTest {

    private val start = 1_000_000_000_000L

    @Test
    fun `formats hours and minutes, ignoring seconds`() {
        // 1h 2m 45s after start -> minutes floored, seconds dropped.
        val now = start + ((1 * 3600) + (2 * 60) + 45) * 1000L
        assertEquals("01:02", TimeTrackingWidget.formatElapsed(start, now))
    }

    @Test
    fun `just started renders as zero`() {
        assertEquals("00:00", TimeTrackingWidget.formatElapsed(start, start))
    }

    @Test
    fun `minutes past the hour wrap correctly`() {
        val now = start + ((2 * 3600) + (59 * 60)) * 1000L
        assertEquals("02:59", TimeTrackingWidget.formatElapsed(start, now))
    }

    @Test
    fun `does not roll a partial minute into the next minute`() {
        // 59 seconds elapsed must still read 00:00, not 00:01.
        val now = start + 59_000L
        assertEquals("00:00", TimeTrackingWidget.formatElapsed(start, now))
    }

    @Test
    fun `negative elapsed (future start due to clock skew) clamps to zero`() {
        val now = start - 5_000L
        assertEquals("00:00", TimeTrackingWidget.formatElapsed(start, now))
    }

    @Test
    fun `long running timers keep counting hours beyond a day`() {
        val now = start + ((26 * 3600) + (5 * 60)) * 1000L
        assertEquals("26:05", TimeTrackingWidget.formatElapsed(start, now))
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

private const val MILLIS_PER_MINUTE = 60_000L
internal const val CURRENT_TIME_SCROLL_LEAD_HOURS = 2.0

/** Keep time-dependent calendar content fresh without causing a per-second recomposition. */
@Composable
internal fun rememberCalendarNow(): Instant {
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            val current = Instant.now()
            value = current
            delay(millisUntilNextCalendarMinute(current.toEpochMilli()))
        }
    }
    return now
}

internal fun millisUntilNextCalendarMinute(epochMillis: Long): Long {
    val elapsedInMinute = Math.floorMod(epochMillis, MILLIS_PER_MINUTE)
    return MILLIS_PER_MINUTE - elapsedInMinute
}

/** Put the current time a little below the top of the first viewport when the grid opens. */
internal fun calendarInitialScrollHours(
    now: Instant,
    zone: ZoneId,
    settings: CalendarGridSettings,
    leadHours: Double = CURRENT_TIME_SCROLL_LEAD_HOURS,
): Double {
    val normalized = settings.normalized()
    val local = now.atZone(zone).toLocalTime()
    val currentHour = local.hour + (local.minute / 60.0) + (local.second / 3_600.0)
    val maxScroll = (normalized.endHour - normalized.startHour - 1).coerceAtLeast(0).toDouble()
    return (currentHour - normalized.startHour - leadHours).coerceIn(0.0, maxScroll)
}

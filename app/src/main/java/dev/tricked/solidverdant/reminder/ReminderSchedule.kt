/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.reminder

import java.time.Instant
import java.time.ZoneId

/**
 * Pure scheduling math for the daily reminder / end-of-day review nudge.
 *
 * WorkManager delivery is intentionally best-effort (gap analysis #78), but the schedule is still
 * anchored to the user's chosen local wall-clock time. Every calculation is timezone-aware and
 * re-resolves the wall time from the local date, so day rollover and DST transitions are handled
 * correctly (e.g. the reminder stays at 17:00 local across a spring-forward, even though that day
 * is only 23 hours long).
 *
 * Kept free of any Android dependency so it can be unit-tested directly.
 */
object ReminderSchedule {

    const val MAX_MINUTE_OF_DAY: Int = 1439

    /** Wall-clock hour (0..23) for a minute-of-day value. */
    fun hourOf(minuteOfDay: Int): Int = minuteOfDay.coerceIn(0, MAX_MINUTE_OF_DAY) / 60

    /** Wall-clock minute (0..59) for a minute-of-day value. */
    fun minuteOf(minuteOfDay: Int): Int = minuteOfDay.coerceIn(0, MAX_MINUTE_OF_DAY) % 60

    /**
     * Epoch-millis of the next occurrence of [minuteOfDay] local time strictly after [nowMillis] in
     * [zone]. When today's time has already passed (or is exactly now) the following day is used,
     * with the wall time re-resolved against [zone] so DST shifts are respected.
     */
    fun nextTriggerMillis(nowMillis: Long, zone: ZoneId, minuteOfDay: Int): Long {
        val now = Instant.ofEpochMilli(nowMillis)
        val nowLocal = now.atZone(zone)
        val hour = hourOf(minuteOfDay)
        val minute = minuteOf(minuteOfDay)
        var candidate = nowLocal.toLocalDate().atTime(hour, minute).atZone(zone)
        if (!candidate.toInstant().isAfter(now)) {
            candidate = nowLocal.toLocalDate().plusDays(1).atTime(hour, minute).atZone(zone)
        }
        return candidate.toInstant().toEpochMilli()
    }

    /** Delay from [nowMillis] until the next trigger; never negative. */
    fun initialDelayMillis(nowMillis: Long, zone: ZoneId, minuteOfDay: Int): Long =
        (nextTriggerMillis(nowMillis, zone, minuteOfDay) - nowMillis).coerceAtLeast(0L)
}

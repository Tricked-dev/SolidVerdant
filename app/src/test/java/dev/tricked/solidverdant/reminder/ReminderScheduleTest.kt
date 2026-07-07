package dev.tricked.solidverdant.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleTest {

    private val utc = ZoneId.of("UTC")
    private val newYork = ZoneId.of("America/New_York")

    private fun millis(zdt: ZonedDateTime): Long = zdt.toInstant().toEpochMilli()

    @Test fun `decomposes minute of day into wall clock`() {
        assertEquals(17, ReminderSchedule.hourOf(17 * 60))
        assertEquals(0, ReminderSchedule.minuteOf(17 * 60))
        assertEquals(23, ReminderSchedule.hourOf(1439))
        assertEquals(59, ReminderSchedule.minuteOf(1439))
    }

    @Test fun `clamps out of range minute of day`() {
        assertEquals(0, ReminderSchedule.hourOf(-5))
        assertEquals(23, ReminderSchedule.hourOf(5000))
        assertEquals(59, ReminderSchedule.minuteOf(5000))
    }

    @Test fun `next trigger later today when time has not passed`() {
        val now = millis(ZonedDateTime.of(2026, 7, 7, 10, 0, 0, 0, utc))
        val expected = millis(ZonedDateTime.of(2026, 7, 7, 17, 0, 0, 0, utc))
        assertEquals(expected, ReminderSchedule.nextTriggerMillis(now, utc, 17 * 60))
        assertEquals(Duration.ofHours(7).toMillis(), ReminderSchedule.initialDelayMillis(now, utc, 17 * 60))
    }

    @Test fun `next trigger rolls over to tomorrow when time already passed`() {
        val now = millis(ZonedDateTime.of(2026, 7, 7, 18, 0, 0, 0, utc))
        val expected = millis(ZonedDateTime.of(2026, 7, 8, 17, 0, 0, 0, utc))
        assertEquals(expected, ReminderSchedule.nextTriggerMillis(now, utc, 17 * 60))
    }

    @Test fun `exact match rolls over so the trigger is strictly in the future`() {
        val now = millis(ZonedDateTime.of(2026, 7, 7, 17, 0, 0, 0, utc))
        val expected = millis(ZonedDateTime.of(2026, 7, 8, 17, 0, 0, 0, utc))
        assertEquals(expected, ReminderSchedule.nextTriggerMillis(now, utc, 17 * 60))
        assertTrue(ReminderSchedule.nextTriggerMillis(now, utc, 17 * 60) > now)
    }

    @Test fun `spring forward keeps the wall clock time and shortens the delay by an hour`() {
        // DST begins in America/New_York on 2026-03-08 02:00 -> 03:00 (that day has 23 hours).
        val now = millis(ZonedDateTime.of(2026, 3, 7, 17, 30, 0, 0, newYork))
        val expected = millis(ZonedDateTime.of(2026, 3, 8, 17, 0, 0, 0, newYork))
        assertEquals(expected, ReminderSchedule.nextTriggerMillis(now, newYork, 17 * 60))
        // 23h30m nominal minus the lost hour = 22h30m of real time.
        assertEquals(
            Duration.ofHours(22).plusMinutes(30).toMillis(),
            ReminderSchedule.initialDelayMillis(now, newYork, 17 * 60),
        )
    }

    @Test fun `fall back keeps the wall clock time and lengthens the delay by an hour`() {
        // DST ends in America/New_York on 2026-11-01 02:00 -> 01:00 (that day has 25 hours).
        val now = millis(ZonedDateTime.of(2026, 10, 31, 17, 30, 0, 0, newYork))
        assertEquals(
            Duration.ofHours(24).plusMinutes(30).toMillis(),
            ReminderSchedule.initialDelayMillis(now, newYork, 17 * 60),
        )
    }

    @Test fun `initial delay is never negative`() {
        val now = millis(ZonedDateTime.of(2026, 7, 7, 23, 59, 0, 0, utc))
        assertTrue(ReminderSchedule.initialDelayMillis(now, utc, 0) >= 0L)
    }
}

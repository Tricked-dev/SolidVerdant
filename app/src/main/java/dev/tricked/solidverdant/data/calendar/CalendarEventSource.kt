/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.calendar

/**
 * Read-only seam over the device calendar. Extracted so the calendar overlay can be unit-tested
 * with a fake instead of a real [android.provider.CalendarContract] content resolver.
 *
 * Implemented by [CalendarEventProvider]; bound in [dev.tricked.solidverdant.di.CalendarOverlayModule].
 */
interface CalendarEventSource {
    /** Lists calendars available on the device. Requires READ_CALENDAR; empty when denied. */
    suspend fun queryCalendars(): List<DeviceCalendar>

    /**
     * Returns event occurrences from [calendarIds] intersecting `[rangeStartMs, rangeEndMs)`.
     * Empty when [calendarIds] is empty or permission is denied.
     */
    suspend fun queryEvents(
        calendarIds: Set<String>,
        rangeStartMs: Long,
        rangeEndMs: Long,
    ): List<DeviceCalendarEvent>
}

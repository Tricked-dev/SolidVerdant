/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A selectable device calendar. Only identifiers and display metadata are exposed; event content
 * (titles, attendees, locations) is queried ephemerally when rendering and never persisted or
 * logged (see FEATURE_GAP_ANALYSIS.md #77).
 */
data class DeviceCalendar(
    val id: String,
    val displayName: String,
    val accountName: String,
    /** Packed ARGB color of the calendar, or null when the provider supplies none. */
    val colorArgb: Int?,
)

/**
 * A single event occurrence resolved from [CalendarContract.Instances] (recurrences expanded).
 *
 * [startUtcMs]/[endUtcMs] are epoch-millis as returned by the provider. For timed events these are
 * true instants; for [allDay] events the provider expresses them as UTC midnight boundaries, so
 * callers must interpret all-day coverage in UTC rather than the device zone.
 */
data class DeviceCalendarEvent(
    val instanceId: Long,
    val eventId: Long,
    val calendarId: String,
    /** May be null/blank; shown in the UI but never logged. */
    val title: String?,
    val startUtcMs: Long,
    val endUtcMs: Long,
    val allDay: Boolean,
    val colorArgb: Int?,
)

/**
 * Reads the device calendar through [CalendarContract]. All queries run off the main thread and
 * require the READ_CALENDAR runtime permission; callers must confirm the grant before invoking.
 *
 * A [SecurityException] (permission revoked mid-session) is swallowed to an empty result so the UI
 * degrades gracefully; any other failure propagates so the caller can surface a retryable error.
 */
@Singleton
class CalendarEventProvider @Inject constructor(@ApplicationContext private val context: Context) : CalendarEventSource {

    /** Lists calendars that expose a display name, sorted by account then name. */
    override suspend fun queryCalendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val result = mutableListOf<DeviceCalendar>()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol).toString()
                    val name = if (cursor.isNull(nameCol)) "" else cursor.getString(nameCol).orEmpty()
                    val account = if (cursor.isNull(accountCol)) "" else cursor.getString(accountCol).orEmpty()
                    val color = if (cursor.isNull(colorCol)) null else cursor.getInt(colorCol)
                    result += DeviceCalendar(
                        id = id,
                        displayName = name,
                        accountName = account,
                        colorArgb = color?.takeIf { it != 0 },
                    )
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        }
        result
    }

    /**
     * Returns occurrences from [calendarIds] that intersect [rangeStartMs, rangeEndMs).
     *
     * Uses the Instances table so recurring events are expanded to concrete occurrences. Returns an
     * empty list when [calendarIds] is empty.
     */
    override suspend fun queryEvents(calendarIds: Set<String>, rangeStartMs: Long, rangeEndMs: Long): List<DeviceCalendarEvent> =
        withContext(Dispatchers.IO) {
            if (calendarIds.isEmpty() || rangeEndMs <= rangeStartMs) return@withContext emptyList()

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, rangeStartMs)
            ContentUris.appendId(builder, rangeEndMs)
            val uri = builder.build()

            val projection = arrayOf(
                CalendarContract.Instances._ID,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR,
            )
            // Constrain to the selected calendars in SQL so we never materialise unselected events.
            val placeholders = calendarIds.joinToString(",") { "?" }
            val selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
            val args = calendarIds.toTypedArray()

            val result = mutableListOf<DeviceCalendarEvent>()
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    args,
                    "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances._ID)
                    val eventCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                    val calCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                    val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                    val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                    val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                    val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                    val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)
                    while (cursor.moveToNext()) {
                        val begin = cursor.getLong(beginCol)
                        val end = cursor.getLong(endCol)
                        // Guard against provider rows with a non-positive span.
                        if (end < begin) continue
                        result += DeviceCalendarEvent(
                            instanceId = cursor.getLong(idCol),
                            eventId = cursor.getLong(eventCol),
                            calendarId = cursor.getLong(calCol).toString(),
                            title = if (cursor.isNull(titleCol)) null else cursor.getString(titleCol),
                            startUtcMs = begin,
                            endUtcMs = end,
                            allDay = cursor.getInt(allDayCol) == 1,
                            colorArgb = if (cursor.isNull(colorCol)) null else cursor.getInt(colorCol).takeIf { it != 0 },
                        )
                    }
                }
            } catch (_: SecurityException) {
                return@withContext emptyList()
            }
            result
        }
}

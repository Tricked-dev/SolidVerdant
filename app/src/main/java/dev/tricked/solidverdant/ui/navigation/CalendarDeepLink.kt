/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.navigation

import android.net.Uri
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val CALENDAR_SCHEME = "solidtime"
private const val CALENDAR_HOST = "calendar"
private const val CALENDAR_DATE_QUERY = "date"
private const val LOCAL_DATE_LENGTH = 10

/** Resolve a calendar date from the public app-link contract without accepting malformed input. */
internal fun calendarDateFromUri(uri: Uri): LocalDate? = parseCalendarDateDeepLink(
    scheme = uri.scheme,
    host = uri.host,
    path = uri.path,
    queryDate = uri.getQueryParameter(CALENDAR_DATE_QUERY),
)

/**
 * Pure parser seam for JVM tests. Both `solidtime://calendar?date=yyyy-MM-dd` and
 * `solidtime://calendar/yyyy-MM-dd` are supported so notification and external callers can use a
 * query or a path without making the UI responsible for URI parsing.
 */
internal fun parseCalendarDateDeepLink(scheme: String?, host: String?, path: String?, queryDate: String?): LocalDate? {
    if (!scheme.equals(CALENDAR_SCHEME, ignoreCase = true) ||
        !host.equals(CALENDAR_HOST, ignoreCase = true)
    ) {
        return null
    }
    val candidate = queryDate?.takeIf { it.isNotBlank() } ?: pathDate(path) ?: return null
    if (candidate.length != LOCAL_DATE_LENGTH) return null
    return runCatching { LocalDate.parse(candidate, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
}

private fun pathDate(path: String?): String? {
    val value = path?.removePrefix("/")?.takeIf { it.isNotBlank() } ?: return null
    return value.takeIf { '/' !in it }
}

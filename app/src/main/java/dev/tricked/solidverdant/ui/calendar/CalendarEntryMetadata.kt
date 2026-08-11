/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.domain.time.parseTimeEntryInstant

/** Resolved catalogue context used by calendar blocks and entry action surfaces. */
data class CalendarEntryMetadata(val title: String?, val context: List<String>, val durationSeconds: Long?) {
    val subtitle: String?
        get() = context.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Resolve the useful, privacy-safe summary shown without opening an editor. Empty catalogue names
 * are ignored, and duplicate names are collapsed so archived/stale data cannot produce noisy
 * labels. Duration is derived from exact instants when the API did not provide one.
 */
fun calendarEntryMetadata(
    entry: TimeEntry,
    projectName: String? = null,
    taskName: String? = null,
    clientName: String? = null,
): CalendarEntryMetadata {
    val context = listOf(clientName, projectName, taskName)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
    val durationSeconds = when {
        entry.duration != null && entry.duration > 0 -> entry.duration.toLong()
        entry.end != null -> {
            val start = parseTimeEntryInstant(entry.start)
            val end = parseTimeEntryInstant(entry.end)
            start?.let { startInstant -> end?.epochSecond?.minus(startInstant.epochSecond) }
                ?.takeIf { it > 0 }
        }
        else -> null
    }
    return CalendarEntryMetadata(
        title = entry.description?.trim()?.takeIf(String::isNotEmpty),
        context = context,
        durationSeconds = durationSeconds,
    )
}

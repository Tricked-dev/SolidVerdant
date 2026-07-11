/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.util

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Cheap accessors for the ISO-8601 date-time strings the solidtime API returns
 * (e.g. "2026-07-07T09:30:00Z").
 *
 * The history pipeline and every visible history row need the date or wall-clock portion of
 * these strings; a full ZonedDateTime.parse per entry is many times the cost of reading the
 * fixed ISO layout directly and dominated the per-emission history preparation. The fast path
 * reads the fixed positions and falls back to real parsing for anything that doesn't match, so
 * the result is always identical to the parse-based equivalent.
 */
object IsoTimes {

    /** Same result as `ZonedDateTime.parse(iso).toLocalDate()` for valid ISO date-times. */
    fun localDate(iso: String): LocalDate? {
        if (iso.length >= 10 && iso[4] == '-' && iso[7] == '-') {
            runCatching { return LocalDate.parse(iso.substring(0, 10)) }
        }
        return runCatching { ZonedDateTime.parse(iso).toLocalDate() }.getOrNull()
    }

    /** Same result as `ZonedDateTime.parse(iso).format(ofPattern("HH:mm"))`. */
    fun hourMinute(iso: String): String? {
        if (iso.length >= 16 && iso[10] == 'T' && iso[13] == ':') {
            return iso.substring(11, 16)
        }
        return runCatching {
            val parsed = ZonedDateTime.parse(iso)
            "%02d:%02d".format(parsed.hour, parsed.minute)
        }.getOrNull()
    }
}

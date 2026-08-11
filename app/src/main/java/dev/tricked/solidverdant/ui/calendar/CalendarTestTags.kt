/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import java.time.LocalDate

/** Stable semantics owned by the calendar UI and consumed by on-device tests. */
object CalendarTestTags {
    const val WEEK_GRID = "calendar_week_grid"
    const val MODE_MONTH = "calendar_mode_month"
    const val ADD_ENTRY = "calendar_add_entry"
    const val SETTINGS = "calendar_settings"

    fun selection(day: LocalDate): String = "calendar-selection-$day"
}

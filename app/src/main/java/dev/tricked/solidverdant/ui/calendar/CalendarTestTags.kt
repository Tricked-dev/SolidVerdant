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
    const val ADD_BREAK = "calendar_add_break"
    const val ADD_BREAK_MENU = "calendar_add_break_menu"
    const val SETTINGS = "calendar_settings"
    const val ENTRY_ACTIONS = "calendar_entry_actions"
    const val SYNC_STATUS = "calendar_entry_sync_status"
    const val SYNC_RETRY = "calendar_entry_sync_retry"
    const val SYNC_DISCARD = "calendar_entry_sync_discard"
    const val SPLIT_PICKER = "calendar_split_picker"

    fun selection(day: LocalDate): String = "calendar-selection-$day"
}

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
    const val CURRENT_TIME_MARKER = "calendar_current_time_marker"
    const val MODE_MONTH = "calendar_mode_month"
    const val ADD_ENTRY = "calendar_add_entry"
    const val ADD_BREAK = "calendar_add_break"
    const val ADD_BREAK_MENU = "calendar_add_break_menu"
    const val RUNNING_TIMER = "calendar_running_timer"
    const val RUNNING_TIMER_EDIT = "calendar_running_timer_edit"
    const val SETTINGS = "calendar_settings"
    const val SETTINGS_SHEET = "calendar_settings_sheet"
    const val SETTINGS_SNAP = "calendar_settings_snap"
    const val SETTINGS_START = "calendar_settings_start"
    const val SETTINGS_END = "calendar_settings_end"
    const val SETTINGS_DENSITY_COMPACT = "calendar_settings_density_compact"
    const val SETTINGS_DENSITY_COMFORTABLE = "calendar_settings_density_comfortable"
    const val SETTINGS_DENSITY_SPACIOUS = "calendar_settings_density_spacious"
    const val ENTRY_ACTIONS = "calendar_entry_actions"
    const val EDIT_START_TIME = "calendar_edit_start_time"
    const val DELETE_ENTRY = "calendar_delete_entry"
    const val DELETE_CONFIRM = "calendar_delete_confirm"
    const val DELETE_CANCEL = "calendar_delete_cancel"
    const val SYNC_STATUS = "calendar_entry_sync_status"
    const val SYNC_RETRY = "calendar_entry_sync_retry"
    const val SYNC_DISCARD = "calendar_entry_sync_discard"
    const val LOAD_ERROR = "calendar_load_error"
    const val SPLIT_PICKER = "calendar_split_picker"

    fun selection(day: LocalDate): String = "calendar-selection-$day"

    fun settingsOption(control: String, value: String): String = "$control-option-$value"
}

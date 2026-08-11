/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e

import dev.tricked.solidverdant.ui.calendar.CalendarTestTags
import dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags
import dev.tricked.solidverdant.ui.login.LoginTestTags
import dev.tricked.solidverdant.ui.sync.SyncCenterTestTags
import dev.tricked.solidverdant.ui.tile.ProjectSelectionTestTags
import dev.tricked.solidverdant.ui.tracking.TrackingTestTags
import java.time.LocalDate

/**
 * Central registry of stable Compose testTags used by the E2E robots.
 *
 * Prefer matching on these tags over localized text so tests survive copy/translation changes.
 * The values are owned by production code ([TrackingTestTags]) and re-exported here so tests have a
 * single import; the UI applies the same constants via `Modifier.testTag(...)`.
 */
object TestTags {
    const val TRACK_PRIMARY_LIST = TrackingTestTags.PRIMARY_LIST
    const val TRACK_HISTORY_LIST = TrackingTestTags.HISTORY_LIST
    const val TRACK_ENTRY_ROW = TrackingTestTags.ENTRY_ROW
    const val TRACK_START_BUTTON = TrackingTestTags.START_BUTTON
    const val TRACK_STOP_BUTTON = TrackingTestTags.STOP_BUTTON
    const val TRACK_SETTINGS_BUTTON = TrackingTestTags.SETTINGS_BUTTON
    const val TRACK_LIVE_UPDATE_SWITCH = TrackingTestTags.LIVE_UPDATE_SWITCH
    const val TRACK_REFRESH_BUTTON = TrackingTestTags.REFRESH_BUTTON
    const val TRACK_ADD_ENTRY_BUTTON = TrackingTestTags.ADD_ENTRY_BUTTON
    const val TRACK_EDIT_ACTIVE_ENTRY = TrackingTestTags.EDIT_ACTIVE_ENTRY
    const val TRACK_LOGOUT_BUTTON = TrackingTestTags.LOGOUT_BUTTON
    const val TRACK_ENTRY_EDIT_BUTTON = TrackingTestTags.ENTRY_EDIT_BUTTON
    const val TRACK_ENTRY_DELETE_BUTTON = TrackingTestTags.ENTRY_DELETE_BUTTON
    const val TRACK_CONTINUE_BUTTON = TrackingTestTags.CONTINUE_BUTTON
    const val TRACK_SHEET = TrackingTestTags.SHEET
    const val TRACK_SHEET_PROJECT_TASK_SELECTOR = TrackingTestTags.SHEET_PROJECT_TASK_SELECTOR
    const val TRACK_PROJECT_TASK_LIST = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.PROJECT_TASK_LIST
    const val TRACK_SHEET_START_TIME = TrackingTestTags.SHEET_START_TIME
    const val TRACK_SHEET_END_TIME = TrackingTestTags.SHEET_END_TIME
    const val TRACK_SHEET_DESCRIPTION_FIELD = TrackingTestTags.SHEET_DESCRIPTION_FIELD
    const val TRACK_SHEET_DURATION_FIELD = TrackingTestTags.SHEET_DURATION_FIELD
    const val TRACK_SHEET_BILLABLE = TrackingTestTags.SHEET_BILLABLE
    const val TRACK_SHEET_CANCEL_BUTTON = TrackingTestTags.SHEET_CANCEL_BUTTON
    const val TRACK_SHEET_SAVE_BUTTON = TrackingTestTags.SHEET_SAVE_BUTTON
    const val TRACK_SHEET_DUPLICATE_BUTTON = TrackingTestTags.SHEET_DUPLICATE_BUTTON
    const val TRACK_SHEET_SPLIT_BUTTON = TrackingTestTags.SHEET_SPLIT_BUTTON
    const val TRACK_SHEET_SPLIT_TIME_PICKER = TrackingTestTags.SHEET_SPLIT_TIME_PICKER
    const val TRACK_SHEET_TIME_PICKER_CONFIRM = TrackingTestTags.SHEET_TIME_PICKER_CONFIRM
    const val TRACK_SHEET_VALIDATION_BANNER = TrackingTestTags.SHEET_VALIDATION_BANNER
    const val TRACK_SYNC_STATUS_CARD = TrackingTestTags.SYNC_STATUS_CARD
    const val TRACK_SYNC_DETAILS_BUTTON = TrackingTestTags.SYNC_DETAILS_BUTTON
    const val TRACK_FILTER_OPEN_BUTTON = TrackingTestTags.FILTER_OPEN_BUTTON
    const val TRACK_FILTER_SEARCH_FIELD = TrackingTestTags.FILTER_SEARCH_FIELD
    const val TRACK_FILTER_CLOSE_BUTTON = TrackingTestTags.FILTER_CLOSE_BUTTON
    const val SYNC_STATUS_SCREEN = SyncCenterTestTags.SCREEN
    const val SYNC_STATUS_BACK_BUTTON = SyncCenterTestTags.BACK_BUTTON
    const val LOGIN_BUTTON = LoginTestTags.LOGIN_BUTTON
    const val TILE_PROJECT_SELECTION_SCREEN = ProjectSelectionTestTags.SCREEN
    const val TILE_PROJECT_SELECTION_START_BUTTON = ProjectSelectionTestTags.START_BUTTON
    const val TILE_PROJECT_SELECTION_CANCEL_BUTTON = ProjectSelectionTestTags.CANCEL_BUTTON
    const val STATS_SCREEN = "stats_screen"
    const val CALENDAR_WEEK_GRID = CalendarTestTags.WEEK_GRID
    const val CALENDAR_CURRENT_TIME_MARKER = CalendarTestTags.CURRENT_TIME_MARKER
    const val CALENDAR_MODE_MONTH = CalendarTestTags.MODE_MONTH
    const val CALENDAR_MODE_WEEK = CalendarTestTags.MODE_WEEK
    const val CALENDAR_MODE_DAY = CalendarTestTags.MODE_DAY
    const val CALENDAR_ADD_ENTRY = CalendarTestTags.ADD_ENTRY
    const val CALENDAR_ADD_BREAK = CalendarTestTags.ADD_BREAK
    const val CALENDAR_ADD_BREAK_MENU = CalendarTestTags.ADD_BREAK_MENU
    const val CALENDAR_OVERLAY = CalendarTestTags.OVERLAY
    const val CALENDAR_OVERLAY_TOGGLE = CalendarTestTags.OVERLAY_TOGGLE
    const val CALENDAR_OVERLAY_CALENDAR_LOADING = CalendarTestTags.OVERLAY_CALENDAR_LOADING
    const val CALENDAR_OVERLAY_CALENDAR_ERROR = CalendarTestTags.OVERLAY_CALENDAR_ERROR
    const val CALENDAR_OVERLAY_RETRY = CalendarTestTags.OVERLAY_RETRY
    const val CALENDAR_SETTINGS = CalendarTestTags.SETTINGS
    const val CALENDAR_RUNNING_TIMER = CalendarTestTags.RUNNING_TIMER
    const val CALENDAR_RUNNING_TIMER_EDIT = CalendarTestTags.RUNNING_TIMER_EDIT
    const val CALENDAR_SETTINGS_SHEET = CalendarTestTags.SETTINGS_SHEET
    const val CALENDAR_SETTINGS_SNAP = CalendarTestTags.SETTINGS_SNAP
    const val CALENDAR_SETTINGS_START = CalendarTestTags.SETTINGS_START
    const val CALENDAR_SETTINGS_END = CalendarTestTags.SETTINGS_END
    const val CALENDAR_SETTINGS_DENSITY_COMPACT = CalendarTestTags.SETTINGS_DENSITY_COMPACT
    const val CALENDAR_SETTINGS_DENSITY_COMFORTABLE = CalendarTestTags.SETTINGS_DENSITY_COMFORTABLE
    const val CALENDAR_SETTINGS_DENSITY_SPACIOUS = CalendarTestTags.SETTINGS_DENSITY_SPACIOUS
    const val CALENDAR_ENTRY_ACTIONS = CalendarTestTags.ENTRY_ACTIONS
    const val CALENDAR_EDIT_START_TIME = CalendarTestTags.EDIT_START_TIME
    const val CALENDAR_STOP_ENTRY = CalendarTestTags.STOP_ENTRY
    const val CALENDAR_DUPLICATE_ENTRY = CalendarTestTags.DUPLICATE_ENTRY
    const val CALENDAR_SPLIT_ENTRY = CalendarTestTags.SPLIT_ENTRY
    const val CALENDAR_DELETE_ENTRY = CalendarTestTags.DELETE_ENTRY
    const val CALENDAR_DELETE_CONFIRM = CalendarTestTags.DELETE_CONFIRM
    const val CALENDAR_DELETE_CANCEL = CalendarTestTags.DELETE_CANCEL
    const val CALENDAR_ENTRY_SYNC_STATUS = CalendarTestTags.SYNC_STATUS
    const val CALENDAR_ENTRY_SYNC_RETRY = CalendarTestTags.SYNC_RETRY
    const val CALENDAR_ENTRY_SYNC_DISCARD = CalendarTestTags.SYNC_DISCARD
    const val CALENDAR_LOAD_ERROR = CalendarTestTags.LOAD_ERROR
    const val CALENDAR_CONTENT_READY = CalendarTestTags.CONTENT_READY
    const val CALENDAR_SPLIT_CONFIRM = CalendarTestTags.SPLIT_CONFIRM
    const val CALENDAR_SPLIT_CANCEL = CalendarTestTags.SPLIT_CANCEL
    const val TRACK_SHEET_START_DATE = TrackingTestTags.SHEET_START_DATE
    const val TRACK_SHEET_END_DATE = TrackingTestTags.SHEET_END_DATE
    const val ENTRY_DATE_PICKER = EditTimeEntryTestTags.DATE_PICKER
    const val ENTRY_DATE_PICKER_CONFIRM = EditTimeEntryTestTags.DATE_PICKER_CONFIRM
    const val ENTRY_SAVE = EditTimeEntryTestTags.SAVE_BUTTON
    const val ENTRY_DESCRIPTION = EditTimeEntryTestTags.DESCRIPTION_FIELD
    const val ENTRY_PROJECT_TASK_SELECTOR = EditTimeEntryTestTags.PROJECT_TASK_SELECTOR
    const val CATALOGUE_PROJECT_TASK_SEARCH = EditTimeEntryTestTags.PROJECT_TASK_SEARCH
    const val CATALOGUE_CLIENT_PICKER = EditTimeEntryTestTags.CLIENT_PICKER
    const val CATALOGUE_CREATE_PROJECT = EditTimeEntryTestTags.CREATE_PROJECT
    const val CATALOGUE_CREATE_TASK = EditTimeEntryTestTags.CREATE_TASK
    const val CATALOGUE_CREATE_TAG = EditTimeEntryTestTags.CREATE_TAG
    const val CATALOGUE_CREATE_CLIENT = EditTimeEntryTestTags.CREATE_CLIENT
    const val CATALOGUE_NAME = EditTimeEntryTestTags.CATALOGUE_NAME
    const val CATALOGUE_CREATE_CONFIRM = EditTimeEntryTestTags.CATALOGUE_CREATE_CONFIRM
    const val CATALOGUE_CREATE_ERROR = EditTimeEntryTestTags.CATALOGUE_CREATE_ERROR

    fun trackEntryTimeRange(entryId: String): String = TrackingTestTags.entryTimeRange(entryId)

    fun trackSheetTagChip(tagId: String): String = TrackingTestTags.sheetTagChip(tagId)

    fun calendarSelection(day: LocalDate): String = CalendarTestTags.selection(day)

    fun calendarSettingsOption(control: String, value: String): String = CalendarTestTags.settingsOption(control, value)

    fun calendarSettingsValue(control: String): String = CalendarTestTags.settingsValue(control)
}

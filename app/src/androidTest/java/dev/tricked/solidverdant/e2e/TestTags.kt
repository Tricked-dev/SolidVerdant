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
import dev.tricked.solidverdant.ui.tracking.TrackingTestTags

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
    const val TRACK_REFRESH_BUTTON = TrackingTestTags.REFRESH_BUTTON
    const val TRACK_LOGOUT_BUTTON = TrackingTestTags.LOGOUT_BUTTON
    const val TRACK_ENTRY_EDIT_BUTTON = TrackingTestTags.ENTRY_EDIT_BUTTON
    const val TRACK_ENTRY_DELETE_BUTTON = TrackingTestTags.ENTRY_DELETE_BUTTON
    const val TRACK_CONTINUE_BUTTON = TrackingTestTags.CONTINUE_BUTTON
    const val TRACK_SHEET_DESCRIPTION_FIELD = TrackingTestTags.SHEET_DESCRIPTION_FIELD
    const val TRACK_SHEET_SAVE_BUTTON = TrackingTestTags.SHEET_SAVE_BUTTON
    const val TRACK_SHEET_DUPLICATE_BUTTON = TrackingTestTags.SHEET_DUPLICATE_BUTTON
    const val TRACK_SYNC_DETAILS_BUTTON = TrackingTestTags.SYNC_DETAILS_BUTTON
    const val TRACK_FILTER_OPEN_BUTTON = TrackingTestTags.FILTER_OPEN_BUTTON
    const val TRACK_FILTER_SEARCH_FIELD = TrackingTestTags.FILTER_SEARCH_FIELD
    const val TRACK_FILTER_CLOSE_BUTTON = TrackingTestTags.FILTER_CLOSE_BUTTON
    const val SYNC_STATUS_SCREEN = SyncCenterTestTags.SCREEN
    const val SYNC_STATUS_BACK_BUTTON = SyncCenterTestTags.BACK_BUTTON
    const val LOGIN_BUTTON = LoginTestTags.LOGIN_BUTTON
    const val STATS_SCREEN = "stats_screen"
    const val CALENDAR_WEEK_GRID = CalendarTestTags.WEEK_GRID
    const val CALENDAR_MODE_MONTH = CalendarTestTags.MODE_MONTH
    const val TRACK_SHEET_START_DATE = TrackingTestTags.SHEET_START_DATE
    const val TRACK_SHEET_END_DATE = TrackingTestTags.SHEET_END_DATE
    const val ENTRY_DATE_PICKER = EditTimeEntryTestTags.DATE_PICKER
    const val ENTRY_DATE_PICKER_CONFIRM = EditTimeEntryTestTags.DATE_PICKER_CONFIRM

    fun trackEntryTimeRange(entryId: String): String = TrackingTestTags.entryTimeRange(entryId)
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

/**
 * Stable Compose testTag constants for the Track screen, defined in production code so that both the
 * UI (via `Modifier.testTag(...)`) and the androidTest robots reference the exact same values.
 *
 * These tags carry no user-facing text and add no visible chrome, so they are inert in production.
 */
object TrackingTestTags {
    const val PRIMARY_LIST = "track_primary_list"
    const val HISTORY_LIST = "track_history_list"
    const val ENTRY_ROW = "track_entry_row"
    const val START_BUTTON = "track_start_button"
    const val STOP_BUTTON = "track_stop_button"
    const val RESET_FIELDS_BUTTON = "track_reset_fields_button"
    const val SETTINGS_BUTTON = "track_settings_button"
    const val LIVE_UPDATE_SWITCH = "track_live_update_switch"
    const val AUTO_CLEAR_FIELDS_SWITCH = "track_auto_clear_fields_switch"
    const val CLEAR_DESCRIPTION_AFTER_STOP_SWITCH = "track_clear_description_after_stop_switch"
    const val REFRESH_BUTTON = "track_refresh_button"
    const val ADD_ENTRY_BUTTON = "track_add_entry_button"
    const val EDIT_ACTIVE_ENTRY = "track_edit_active_entry"
    const val LOGOUT_BUTTON = "track_logout_button"
    const val ENTRY_EDIT_BUTTON = "track_entry_edit"
    const val ENTRY_DELETE_BUTTON = "track_entry_delete"
    const val CONTINUE_BUTTON = "track_continue_last"
    const val SHEET = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.SHEET
    const val SHEET_PROJECT_TASK_SELECTOR = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.PROJECT_TASK_SELECTOR
    const val SHEET_TASK_SELECTOR = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.TASK_SELECTOR
    const val SHEET_TAGS_SELECTOR = "track_sheet_tags_selector"
    const val SHEET_TAGS_LIST = "track_sheet_tags_list"
    const val SHEET_START_TIME = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.START_TIME
    const val SHEET_END_TIME = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.END_TIME
    const val SHEET_DESCRIPTION_FIELD = "track_sheet_description"
    const val SHEET_DURATION_FIELD = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.DURATION_FIELD
    const val SHEET_BILLABLE = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.BILLABLE
    const val SHEET_CANCEL_BUTTON = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.CANCEL_BUTTON
    const val SHEET_SAVE_BUTTON = "track_sheet_save"
    const val SHEET_DUPLICATE_BUTTON = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.DUPLICATE_BUTTON
    const val SHEET_SPLIT_BUTTON = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.SPLIT_BUTTON
    const val SHEET_SPLIT_TIME_PICKER = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.SPLIT_TIME_PICKER
    const val SHEET_TIME_PICKER_CONFIRM = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.TIME_PICKER_CONFIRM
    const val SHEET_VALIDATION_BANNER = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.VALIDATION_BANNER
    const val ELAPSED_TIMER = "track_elapsed_timer"
    const val SYNC_STATUS_CARD = "track_sync_status_card"
    const val SYNC_DETAILS_BUTTON = "track_sync_details"
    const val FILTER_OPEN_BUTTON = "track_filter_open"
    const val FILTER_SEARCH_FIELD = "track_filter_search"
    const val FILTER_CLOSE_BUTTON = "track_filter_close"
    const val SHEET_START_DATE = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.START_DATE
    const val SHEET_END_DATE = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.END_DATE

    fun entryTimeRange(entryId: String): String = "track_entry_time_range_$entryId"

    fun sheetTagChip(tagId: String): String = dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.tagChip(tagId)
}

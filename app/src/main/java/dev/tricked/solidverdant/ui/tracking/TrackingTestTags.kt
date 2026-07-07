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
    const val HISTORY_LIST = "track_history_list"
    const val ENTRY_ROW = "track_entry_row"
    const val START_BUTTON = "track_start_button"
    const val STOP_BUTTON = "track_stop_button"
    const val SETTINGS_BUTTON = "track_settings_button"
    const val LOGOUT_BUTTON = "track_logout_button"
    const val ENTRY_EDIT_BUTTON = "track_entry_edit"
    const val ENTRY_DELETE_BUTTON = "track_entry_delete"
    const val CONTINUE_BUTTON = "track_continue_last"
    const val SHEET_DESCRIPTION_FIELD = "track_sheet_description"
    const val SHEET_SAVE_BUTTON = "track_sheet_save"
    const val ELAPSED_TIMER = "track_elapsed_timer"
}

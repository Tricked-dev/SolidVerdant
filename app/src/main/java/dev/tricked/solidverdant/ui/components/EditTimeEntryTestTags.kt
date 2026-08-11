/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

object EditTimeEntryTestTags {
    const val SHEET = "entry_edit_sheet"
    const val PROJECT_TASK_SELECTOR = "project_task_selector"
    const val PROJECT_TASK_LIST = "project_task_list"
    const val CREATE_PROJECT = "create_project"
    const val CREATE_TASK = "create_task"
    const val CREATE_TAG = "create_tag"
    const val CREATE_CLIENT = "create_client"
    const val CLIENT_PICKER = "catalogue_client_picker"
    const val PROJECT_TASK_SEARCH = "project_task_search"
    const val CATALOGUE_NAME = "catalogue_name"
    const val CATALOGUE_CREATE_CONFIRM = "catalogue_create_confirm"
    const val CATALOGUE_CREATE_ERROR = "catalogue_create_error"
    const val START_TIME = "entry_start_time"
    const val END_TIME = "entry_end_time"
    const val START_DATE = "entry_start_date"
    const val END_DATE = "entry_end_date"
    const val DURATION_FIELD = "entry_duration"
    const val DESCRIPTION_FIELD = "entry_description"
    const val SAVE_BUTTON = "entry_save"
    const val BILLABLE = "entry_billable"
    const val CANCEL_BUTTON = "entry_cancel"
    const val DELETE_BUTTON = "entry_delete"
    const val VALIDATION_BANNER = "entry_validation"
    const val DUPLICATE_BUTTON = "entry_duplicate"
    const val SPLIT_BUTTON = "entry_split"
    const val SPLIT_TIME_PICKER = "entry_split_time_picker"
    const val TIME_PICKER_CONFIRM = "entry_time_picker_confirm"
    const val DATE_PICKER = "entry_date_picker"
    const val DATE_PICKER_CONFIRM = "entry_date_picker_confirm"

    fun tagChip(tagId: String): String = "entry_tag_$tagId"
}

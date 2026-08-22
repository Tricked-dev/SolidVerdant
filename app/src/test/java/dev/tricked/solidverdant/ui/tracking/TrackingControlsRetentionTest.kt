/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackingControlsRetentionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun idle_retained_fields_show_an_operable_reset_button() {
        var resetClicked = false
        composeRule.setContent {
            MaterialTheme {
                TrackingControls(
                    uiState = TrackingUiState(
                        editingDescription = "Precision setup",
                        editingProjectId = "project-1",
                        editingTaskId = "task-1",
                    ),
                    onDescriptionChange = {},
                    onProjectChange = {},
                    onTaskChange = {},
                    onResetEntryFields = { resetClicked = true },
                    autoClearEntryFieldsAfterStop = false,
                    onTagsChange = {},
                    onBillableChange = {},
                    onStart = {},
                    onStop = {},
                    onPause = {},
                    onResume = {},
                    onUpdate = {},
                )
            }
        }

        composeRule.onNodeWithTag(TrackingTestTags.RESET_FIELDS_BUTTON)
            .assertIsDisplayed()
            .performClick()

        assertTrue(resetClicked)
    }

    @Test
    fun blank_idle_fields_do_not_show_reset_button() {
        composeRule.setContent {
            MaterialTheme {
                TrackingControls(
                    uiState = TrackingUiState(),
                    onDescriptionChange = {},
                    onProjectChange = {},
                    onTaskChange = {},
                    onTagsChange = {},
                    onBillableChange = {},
                    onStart = {},
                    onStop = {},
                    onPause = {},
                    onResume = {},
                    onUpdate = {},
                )
            }
        }

        composeRule.onNodeWithTag(TrackingTestTags.RESET_FIELDS_BUTTON).assertDoesNotExist()
    }

    @Test
    fun auto_clear_enabled_hides_reset_even_when_fields_have_values() {
        composeRule.setContent {
            MaterialTheme {
                TrackingControls(
                    uiState = TrackingUiState(editingDescription = "Prepared work"),
                    onDescriptionChange = {},
                    onProjectChange = {},
                    onTaskChange = {},
                    autoClearEntryFieldsAfterStop = true,
                    onTagsChange = {},
                    onBillableChange = {},
                    onStart = {},
                    onStop = {},
                    onPause = {},
                    onResume = {},
                    onUpdate = {},
                )
            }
        }

        composeRule.onNodeWithTag(TrackingTestTags.RESET_FIELDS_BUTTON).assertDoesNotExist()
    }

    @Test
    fun description_only_setting_is_shown_when_full_auto_clear_is_disabled() {
        var enabled = false
        composeRule.setContent {
            MaterialTheme {
                AutoClearEntryFieldsSettings(
                    autoClearEntryFieldsAfterStop = false,
                    clearDescriptionAfterStop = false,
                    onAutoClearEntryFieldsAfterStopChange = {},
                    onClearDescriptionAfterStopChange = { enabled = it },
                )
            }
        }

        composeRule.onNodeWithTag(TrackingTestTags.CLEAR_DESCRIPTION_AFTER_STOP_SWITCH)
            .assertIsDisplayed()
            .performClick()

        assertTrue(enabled)
    }

    @Test
    fun description_only_setting_is_hidden_when_full_auto_clear_is_enabled() {
        composeRule.setContent {
            MaterialTheme {
                AutoClearEntryFieldsSettings(
                    autoClearEntryFieldsAfterStop = true,
                    clearDescriptionAfterStop = true,
                    onAutoClearEntryFieldsAfterStopChange = {},
                    onClearDescriptionAfterStopChange = {},
                )
            }
        }

        composeRule.onNodeWithTag(TrackingTestTags.CLEAR_DESCRIPTION_AFTER_STOP_SWITCH).assertDoesNotExist()
    }
}

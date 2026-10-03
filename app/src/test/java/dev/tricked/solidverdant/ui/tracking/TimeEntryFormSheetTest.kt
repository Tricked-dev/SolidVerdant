/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimeEntryFormSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completed_entry_start_time_opens_picker_without_dismissing_parent_sheet() {
        var dismissCount = 0
        composeRule.setContent {
            MaterialTheme {
                TimeEntryFormSheet(
                    entry = TimeEntry(
                        id = "completed-overnight",
                        userId = "user",
                        organizationId = "org",
                        start = "2026-08-21T23:30:00-06:00",
                        end = "2026-08-22T01:00:00-06:00",
                        description = "Milling",
                    ),
                    zone = ZoneId.of("America/Edmonton"),
                    suggestedStart = null,
                    projects = emptyList(),
                    tasks = emptyList(),
                    tags = emptyList(),
                    onDismiss = { dismissCount++ },
                    onSave = { _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag(TrackingTestTags.SHEET_START_TIME).performScrollTo().performClick()

        composeRule.onNodeWithTag(TrackingTestTags.SHEET_TIME_PICKER_CONFIRM).assertExists()
        composeRule.onNodeWithTag(TrackingTestTags.SHEET).assertExists()
        assertEquals(0, dismissCount)
    }

    @Test
    fun every_child_picker_blocks_parent_sheet_dismissal() {
        assertFalse(canDismissTimeEntryFormSheet(hasTimePicker = true, hasDatePicker = false, hasSplitPicker = false))
        assertFalse(canDismissTimeEntryFormSheet(hasTimePicker = false, hasDatePicker = true, hasSplitPicker = false))
        assertFalse(canDismissTimeEntryFormSheet(hasTimePicker = false, hasDatePicker = false, hasSplitPicker = true))
        assertFalse(
            canDismissTimeEntryFormSheet(hasTimePicker = false, hasDatePicker = false, hasSplitPicker = false, hasTemplatePicker = true),
        )
        assertTrue(canDismissTimeEntryFormSheet(hasTimePicker = false, hasDatePicker = false, hasSplitPicker = false))
    }

    @Test
    fun another_entry_replacing_the_open_one_does_not_keep_its_fields() {
        val history = TimeEntry(
            id = "history",
            userId = "user",
            organizationId = "org",
            start = "2026-08-21T08:00:00Z",
            end = "2026-08-21T09:00:00Z",
            description = "Typed into the history entry",
        )
        val running =
            TimeEntry(id = "running", userId = "user", organizationId = "org", start = "2026-08-21T10:00:00Z", description = "Running work")
        var shown by mutableStateOf(history)
        var saved: String? = null
        composeRule.setContent {
            MaterialTheme {
                TimeEntryFormSheet(
                    entry = shown,
                    zone = ZoneId.of("UTC"),
                    suggestedStart = null,
                    projects = emptyList(),
                    tasks = emptyList(),
                    tags = emptyList(),
                    onDismiss = {},
                    onSave = { description, _, _, _, _, _, _ -> saved = description },
                )
            }
        }
        composeRule.onNodeWithTag(TrackingTestTags.SHEET_DESCRIPTION_FIELD).assert(hasText("Typed into the history entry"))

        // "Edit the running entry" arrives while the history entry's sheet is open.
        shown = running
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TrackingTestTags.SHEET_DESCRIPTION_FIELD).assert(hasText("Running work"))
        composeRule.onNodeWithTag(TrackingTestTags.SHEET_SAVE_BUTTON).performClick()
        assertEquals("Running work", saved)
    }

    @Test
    fun swiping_down_inside_the_form_does_not_dismiss_the_sheet() {
        var dismissCount = 0
        composeRule.setContent {
            MaterialTheme {
                TimeEntryFormSheet(
                    entry = TimeEntry(
                        id = "completed",
                        userId = "user",
                        organizationId = "org",
                        start = "2026-08-21T08:00:00Z",
                        end = "2026-08-21T09:00:00Z",
                        description = "Milling",
                    ),
                    zone = ZoneId.of("UTC"),
                    suggestedStart = null,
                    projects = emptyList(),
                    tasks = emptyList(),
                    tags = emptyList(),
                    onDismiss = { dismissCount++ },
                    onSave = { _, _, _, _, _, _, _ -> },
                )
            }
        }

        // The form is already scrolled to the top, so the whole swipe is leftover the sheet could take.
        composeRule.onNodeWithTag(TrackingTestTags.SHEET_DESCRIPTION_FIELD).performTouchInput { swipeDown(durationMillis = 100) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TrackingTestTags.SHEET).assertExists()
        assertEquals(0, dismissCount)
    }
}

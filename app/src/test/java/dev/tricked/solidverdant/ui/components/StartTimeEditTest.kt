/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.ui.tracking.TimeEntryFormSheet
import dev.tricked.solidverdant.ui.tracking.TrackingTestTags
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

/**
 * Changing the start time corrects when work began: the end must stay where it was and the
 * duration must follow, rather than the whole entry sliding with a fixed length.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartTimeEditTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val entry = TimeEntry(
        id = "e1",
        description = "work",
        userId = "u1",
        start = "2026-07-06T18:00:00Z",
        end = "2026-07-06T22:00:00Z",
        duration = 14400,
        organizationId = "org1",
    )

    @Test
    fun edit_dialog_moves_only_the_start() {
        var saved: Pair<String, String?>? = null
        composeRule.setContent {
            MaterialTheme {
                EditTimeEntryDialog(
                    entry = entry,
                    zone = ZoneOffset.UTC,
                    projects = emptyList(),
                    tasks = emptyList(),
                    tags = emptyList(),
                    onDismiss = {},
                    onSave = { _, _, _, _, _, start, end -> saved = start to end },
                    inlinePresentation = true,
                )
            }
        }

        pickStartHour(EditTimeEntryTestTags.START_TIME, hour = 20)
        composeRule.onNodeWithTag(EditTimeEntryTestTags.SAVE_BUTTON).performScrollTo().performClick()

        assertSaved(saved, start = "2026-07-06T20:00:00Z", end = "2026-07-06T22:00:00Z")
    }

    @Test
    fun tracking_sheet_moves_only_the_start() {
        var saved: Pair<String, String?>? = null
        composeRule.setContent {
            MaterialTheme {
                TimeEntryFormSheet(
                    entry = entry,
                    zone = ZoneOffset.UTC,
                    suggestedStart = null,
                    projects = emptyList(),
                    tasks = emptyList(),
                    tags = emptyList(),
                    onDismiss = {},
                    onSave = { _, _, _, _, _, start, end -> saved = start to end },
                )
            }
        }

        pickStartHour(TrackingTestTags.SHEET_START_TIME, hour = 20)
        composeRule.onNodeWithTag(TrackingTestTags.SHEET_SAVE_BUTTON).performScrollTo().performClick()

        assertSaved(saved, start = "2026-07-06T20:00:00Z", end = "2026-07-06T22:00:00Z")
    }

    @Before
    fun use24HourClock() {
        // The picker follows the device clock format; pin it so the hour dial labels are stable.
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun pickStartHour(startTimeTag: String, hour: Int) {
        composeRule.onNodeWithTag(startTimeTag).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("$hour hours")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag(EditTimeEntryTestTags.TIME_PICKER_CONFIRM).performClick()
        composeRule.waitForIdle()
    }

    private fun assertSaved(saved: Pair<String, String?>?, start: String, end: String) {
        val (savedStart, savedEnd) = checkNotNull(saved) { "Save was not called" }
        assertEquals(Instant.parse(start), Instant.parse(savedStart))
        assertEquals(Instant.parse(end), Instant.parse(checkNotNull(savedEnd)))
    }
}

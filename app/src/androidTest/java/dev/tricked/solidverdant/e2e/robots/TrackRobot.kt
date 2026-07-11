/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.robots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import dev.tricked.solidverdant.e2e.TestTags

/**
 * Robot for the Track screen. High-level actions/assertions matched on stable testTags plus entry
 * data (descriptions are user data, not localized chrome, so text matching them is stable).
 */
class TrackRobot(composeRule: ComposeTestRule) : Robot(composeRule) {

    /** Wait until the Track screen's history list is present (app finished launching + logged in). */
    fun waitForHistory(): TrackRobot = apply {
        waitUntilTagExists(TestTags.TRACK_HISTORY_LIST)
    }

    /** Assert a history entry with [description] is shown, waiting for background refresh/sync. */
    fun assertEntryVisible(description: String): TrackRobot = apply {
        waitUntilTextExists(description)
        composeRule.onAllNodes(hasText(description, substring = true), useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
    }

    fun entryRowCount(): Int = nodesWithTag(TestTags.TRACK_ENTRY_ROW).fetchSemanticsNodes().size

    fun tapStart(): TrackRobot = apply {
        waitUntilEnabledTagExists(TestTags.TRACK_START_BUTTON)
        firstEnabledNodeWithTag(TestTags.TRACK_START_BUTTON).performClick()
    }

    fun tapStop(): TrackRobot = apply {
        waitUntilEnabledTagExists(TestTags.TRACK_STOP_BUTTON)
        firstEnabledNodeWithTag(TestTags.TRACK_STOP_BUTTON).performClick()
    }

    fun tapRefresh(): TrackRobot = apply {
        waitUntilEnabledTagExists(TestTags.TRACK_REFRESH_BUTTON)
        firstEnabledNodeWithTag(TestTags.TRACK_REFRESH_BUTTON).performClick()
    }

    fun assertStopButtonVisible(): TrackRobot = apply {
        waitUntilTagExists(TestTags.TRACK_STOP_BUTTON)
    }

    fun openSettings(): TrackRobot = apply {
        waitUntilTagExists(TestTags.TRACK_SETTINGS_BUTTON)
        firstNodeWithTag(TestTags.TRACK_SETTINGS_BUTTON).performClick()
        waitUntilTagExists(TestTags.TRACK_LOGOUT_BUTTON)
    }

    fun logout(): TrackRobot = apply {
        firstNodeWithTag(TestTags.TRACK_LOGOUT_BUTTON).performClick()
    }

    fun assertLoginVisible(): TrackRobot = apply {
        waitUntilTagExists(TestTags.LOGIN_BUTTON)
        firstNodeWithTag(TestTags.LOGIN_BUTTON).assertIsDisplayed()
    }

    fun assertStartButtonVisible(): TrackRobot = apply {
        waitUntilTagExists(TestTags.TRACK_START_BUTTON)
    }

    fun tapContinueLastEntry(): TrackRobot = apply {
        waitUntilEnabledTagExists(TestTags.TRACK_CONTINUE_BUTTON)
        firstEnabledNodeWithTag(TestTags.TRACK_CONTINUE_BUTTON).performClick()
    }

    /** Open the edit sheet for the first (newest) visible single-entry row. */
    fun tapFirstEntryEdit(): TrackRobot = apply {
        waitUntilTagExists(TestTags.TRACK_ENTRY_EDIT_BUTTON)
        firstNodeWithTag(TestTags.TRACK_ENTRY_EDIT_BUTTON).performClick()
        waitUntilTagExists(TestTags.TRACK_SHEET_SAVE_BUTTON)
    }

    fun tapFirstEntryDelete(): TrackRobot = apply {
        waitUntilTagExists(TestTags.TRACK_ENTRY_DELETE_BUTTON)
        firstNodeWithTag(TestTags.TRACK_ENTRY_DELETE_BUTTON).performClick()
    }

    /** Replace the description in the open edit sheet. */
    fun replaceSheetDescription(text: String): TrackRobot = apply {
        waitUntilTagExists(TestTags.TRACK_SHEET_DESCRIPTION_FIELD)
        firstNodeWithTag(TestTags.TRACK_SHEET_DESCRIPTION_FIELD).performTextClearance()
        firstNodeWithTag(TestTags.TRACK_SHEET_DESCRIPTION_FIELD).performTextInput(text)
    }

    fun saveSheet(): TrackRobot = apply {
        // The IME from typing can cover the save button; dismiss it before clicking.
        Espresso.closeSoftKeyboard()
        waitUntilEnabledTagExists(TestTags.TRACK_SHEET_SAVE_BUTTON)
        firstEnabledNodeWithTag(TestTags.TRACK_SHEET_SAVE_BUTTON).performClick()
        // Wait until the sheet is gone so later text assertions match history rows, not the
        // sheet's own fields.
        composeRule.waitUntil(DEFAULT_TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag(TestTags.TRACK_SHEET_SAVE_BUTTON), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    /** Wait until no history row shows [description] (e.g. after delete). */
    fun waitUntilEntryGone(description: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): TrackRobot = apply {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodes(hasText(description, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    /** Tap the snackbar action with [label] (localized text resolved by the caller). */
    fun tapSnackbarAction(label: String): TrackRobot = apply {
        waitUntilTextExists(label)
        composeRule.onAllNodes(hasText(label), useUnmergedTree = true).onFirst().performClick()
    }
}

package dev.tricked.solidverdant.e2e.robots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
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
        composeRule.onAllNodes(hasText(description, substring = true)).onFirst().assertIsDisplayed()
    }

    fun entryRowCount(): Int =
        nodesWithTag(TestTags.TRACK_ENTRY_ROW).fetchSemanticsNodes().size

    fun tapStart(): TrackRobot = apply {
        waitUntilTagExists(TestTags.TRACK_START_BUTTON)
        firstNodeWithTag(TestTags.TRACK_START_BUTTON).performClick()
    }

    fun tapStop(): TrackRobot = apply {
        waitUntilTagExists(TestTags.TRACK_STOP_BUTTON)
        firstNodeWithTag(TestTags.TRACK_STOP_BUTTON).performClick()
    }

    fun assertStopButtonVisible(): TrackRobot = apply {
        waitUntilTagExists(TestTags.TRACK_STOP_BUTTON)
    }
}

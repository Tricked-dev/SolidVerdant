/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises every primary destination with a deliberately oversized server dataset. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LargeDataScrollE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @Test
    fun largeDatasetScrollsAcrossPrimaryScreens() {
        e2e.mockServer.presetStressWorld()
        e2e.seedTemplates()
        e2e.launchApp()

        e2e.composeRule.waitForTag(TestTags.TRACK_HISTORY_LIST)
        e2e.composeRule.tapTag("project_task_selector")
        e2e.composeRule.waitForTag("project_task_list")
        e2e.composeRule.swipeTag("project_task_list", times = 12)
        Espresso.pressBack()
        e2e.composeRule.swipeScrollable(times = 12)

        listOf("calendar", "stats", "review").forEach { route ->
            e2e.composeRule.tapTag("main_nav_$route")
            e2e.composeRule.waitForScrollable()
            e2e.composeRule.swipeScrollable(times = 8)
        }

        e2e.composeRule.tapTag("review_more_actions")
        e2e.composeRule.tapTag("review_manage_templates")
        e2e.composeRule.waitForTag("templates_list")
        e2e.composeRule.swipeScrollable(times = 12)

        // The settings drawer is itself a long, independently scrollable surface.
        e2e.composeRule.tapTag("main_nav_track")
        e2e.composeRule.tapTag(TestTags.TRACK_SETTINGS_BUTTON)
        e2e.composeRule.waitForTag(TestTags.TRACK_LOGOUT_BUTTON)
        e2e.composeRule.swipeScrollable(times = 8)
    }
}

// The stress dataset takes noticeably longer to compose on the slow CI emulator, so these
// mirror the generous Robot.DEFAULT_TIMEOUT_MS budget rather than a tight 15s.
private const val SCROLL_WAIT_MS = 30_000L

private fun ComposeTestRule.waitForTag(tag: String) {
    waitUntil(SCROLL_WAIT_MS) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
}

private fun ComposeTestRule.tapTag(tag: String) {
    val matcher = hasTestTag(tag) and isEnabled()
    waitUntil(SCROLL_WAIT_MS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    onAllNodes(matcher).onFirst().performClick()
}

private fun ComposeTestRule.waitForScrollable() {
    waitUntil(SCROLL_WAIT_MS) { onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
}

private fun ComposeTestRule.swipeScrollable(times: Int) {
    repeat(times) {
        val nodes = onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        if (nodes.isEmpty()) return
        onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
    }
}

private fun ComposeTestRule.swipeTag(tag: String, times: Int) {
    repeat(times) {
        onAllNodes(hasTestTag(tag)).onFirst().performTouchInput { swipeUp() }
    }
}

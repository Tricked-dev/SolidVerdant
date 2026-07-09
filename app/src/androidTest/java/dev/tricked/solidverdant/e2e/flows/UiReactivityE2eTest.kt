/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import dev.tricked.solidverdant.e2e.assumeApi30OrNewer
import dev.tricked.solidverdant.e2e.mock.MockSolidtimeServer
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import dev.tricked.solidverdant.ui.tracking.TrackingTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The UI must react to every source of change, not only to taps it initiated itself:
 * server-side edits and additions land after a user refresh, and the running timer visibly
 * ticks. These pin the reactive pipeline (network -> Room -> combine -> Compose) end to end.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UiReactivityE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @Test
    fun serverSideEditShowsAfterPullToRefresh() {
        e2e.mockServer.presetLoggedInWorld() // seeds "Seeded work" (id seed-entry-1)
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertEntryVisible("Seeded work")

        // Another client renames the entry on the server.
        val renamed = e2e.mockServer.timeEntries.first { it.id == "seed-entry-1" }
            .copy(description = "Renamed on server")
        e2e.mockServer.timeEntries.removeAll { it.id == "seed-entry-1" }
        e2e.mockServer.addTimeEntry(renamed)

        pullToRefresh()

        robot.assertEntryVisible("Renamed on server")
    }

    @Test
    fun entryCreatedElsewhereShowsAfterPullToRefresh() {
        assumeApi30OrNewer()
        e2e.mockServer.presetLoggedInWorld()
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertEntryVisible("Seeded work")

        e2e.mockServer.addTimeEntry(
            MockSolidtimeServer.defaultCompletedEntry(
                id = "created-elsewhere-1",
                description = "Booked from the web app",
            ),
        )

        pullToRefresh()

        robot.assertEntryVisible("Booked from the web app")
    }

    @Test
    fun elapsedTimerVisiblyTicksWhileTracking() {
        assumeApi30OrNewer()
        e2e.mockServer.presetLoggedInWorld(seededEntry = null)
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory()

        robot.tapStart().assertStopButtonVisible()

        val initial = elapsedText()
        // The per-second ticker must advance the rendered time (real time, not TestClock:
        // the ticker derives from the entry's start instant and the system clock).
        e2e.composeRule.waitUntil(10_000) { elapsedText() != initial }

        robot.tapStop()
    }

    private fun pullToRefresh() {
        e2e.composeRule.onAllNodes(hasTestTag(TestTags.TRACK_HISTORY_LIST)).onFirst()
            .performTouchInput { swipeDown(durationMillis = 400) }
        e2e.composeRule.waitForIdle()
    }

    private fun elapsedText(): String = e2e.composeRule.onAllNodes(hasTestTag(TrackingTestTags.ELAPSED_TIMER))
        .fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString() ?: ""
}

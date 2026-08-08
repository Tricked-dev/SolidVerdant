/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
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

    @BackendPortable
    @Test
    fun serverSideEditShowsAfterPullToRefresh() {
        val source = e2e.prepare(E2eFixture.Completed(e2e.completedFixtureEntry()))
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertEntryVisible("Seeded work")

        // Another client renames the entry on the server.
        e2e.updateOnServer(requireNotNull(source.logicalId)) { it.copy(description = "Renamed on server") }

        refreshAndAwait(robot)

        robot.assertEntryVisible("Renamed on server")
    }

    @BackendPortable
    @Test
    fun entryCreatedElsewhereShowsAfterPullToRefresh() {
        e2e.prepare(E2eFixture.Completed(e2e.completedFixtureEntry()))
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertEntryVisible("Seeded work")

        e2e.createOnServer(
            e2e.completedFixtureEntry(
                logicalId = "created-elsewhere-1",
                description = "Booked from the web app",
                start = java.time.Instant.ofEpochMilli(e2e.testClock.nowMs).minusSeconds(3_600),
            ),
        )

        refreshAndAwait(robot)

        robot.assertEntryVisible("Booked from the web app")
    }

    @Test
    fun elapsedTimerVisiblyTicksWhileTracking() {
        e2e.requireMockBackend().presetLoggedInWorld(seededEntry = null)
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory()

        robot.tapStart().assertStopButtonVisible()

        val initial = elapsedText()
        // The per-second ticker must advance the rendered time (real time, not TestClock:
        // the ticker derives from the entry's start instant and the system clock).
        e2e.composeRule.waitUntil(10_000) { elapsedText() != initial }

        robot.tapStop()
    }

    private fun elapsedText(): String = e2e.composeRule.onAllNodes(
        hasTestTag(TrackingTestTags.ELAPSED_TIMER),
        useUnmergedTree = true,
    )
        .fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString() ?: ""

    private fun refreshAndAwait(robot: TrackRobot) {
        robot.tapRefresh()
    }
}

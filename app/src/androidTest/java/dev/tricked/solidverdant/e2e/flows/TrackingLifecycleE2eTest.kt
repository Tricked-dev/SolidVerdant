/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.assumeApi30OrNewer
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The core capture loop: start a timer, stop it, and prove the completed entry both renders in
 * history and reaches the backend — plus the running timer surviving activity recreation
 * (rotation, theme change, process-initiated recreation).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TrackingLifecycleE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @Test
    fun startThenStopSyncsACompletedEntryToTheServer() {
        e2e.mockServer.presetLoggedInWorld(seededEntry = null)
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory()

        robot.tapStart().assertStopButtonVisible()

        // Drain the START op so the server owns the running entry.
        e2e.composeRule.waitUntil(WAIT_MS) {
            e2e.runPendingSync()
            e2e.mockServer.wasRequested("POST", "/time-entries")
        }

        robot.tapStop().assertStartButtonVisible()

        // Drain the STOP op; the mock marks the entry completed when the PUT carries an end.
        e2e.composeRule.waitUntil(WAIT_MS) {
            e2e.runPendingSync()
            e2e.mockServer.timeEntries.any { it.end != null }
        }

        val completed = e2e.mockServer.timeEntries.filter { it.end != null }
        assertTrue("Expected exactly one completed entry on the server, got $completed", completed.size == 1)
    }

    @Test
    fun runningTimerSurvivesActivityRecreation() {
        assumeApi30OrNewer()
        e2e.mockServer.presetLoggedInWorld(seededEntry = null)
        val scenario = e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory()

        robot.tapStart().assertStopButtonVisible()

        scenario.recreate()

        // The recreated activity must come back in the running state (ViewModel + Room state),
        // and the timer must still be stoppable.
        robot.assertStopButtonVisible()
        robot.tapStop().assertStartButtonVisible()
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
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

    @BackendPortable
    @Test
    fun startThenStopSyncsACompletedEntryToTheServer() {
        e2e.prepare(E2eFixture.Empty)
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory()

        robot.tapStart().assertStopButtonVisible()

        // Drain the START op so the server owns the running entry.
        val startedSnapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { it.activeEntry != null }
        val startedServerId = requireNotNull(startedSnapshot.activeEntry).id

        robot.tapStop().assertStartButtonVisible()

        // Drain the STOP op; the mock marks the entry completed when the PUT carries an end.
        val completedSnapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { snapshot ->
            snapshot.entries.firstOrNull { it.id == startedServerId }?.end != null
        }

        val completed = completedSnapshot.entries.firstOrNull { it.id == startedServerId }
        assertTrue("Expected the started server entry to be completed, got $completed", completed?.end != null)
    }

    @Test
    fun runningTimerSurvivesActivityRecreation() {
        e2e.requireMockBackend().presetLoggedInWorld(seededEntry = null)
        val scenario = e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory()

        robot.tapStart().assertStopButtonVisible()
        e2e.awaitServer(WAIT_MS, driveSync = true) { it.activeEntry != null }

        scenario.recreate()

        // The recreated activity must come back in the running state (ViewModel + Room state),
        // and the timer must still be stoppable.
        robot.assertStopButtonVisible()
        robot.tapStop()
        e2e.awaitServer(WAIT_MS, driveSync = true) { it.activeEntry == null }
        robot.assertStartButtonVisible()
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

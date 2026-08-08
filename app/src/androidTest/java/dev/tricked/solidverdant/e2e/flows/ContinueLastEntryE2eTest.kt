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
 * "Continue last entry" must start a timer that carries the previous entry's work context
 * (description) all the way into the START request the server receives — not just flip the UI
 * into a running state.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ContinueLastEntryE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @BackendPortable
    @Test
    fun continueCarriesLastEntryDescriptionIntoTheServerStart() {
        val source = e2e.prepare(E2eFixture.Completed(e2e.completedFixtureEntry()))
        val sourceServerId = requireNotNull(source.serverId)
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertEntryVisible("Seeded work")

        robot.tapContinueLastEntry().assertStopButtonVisible()

        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            (current.entries + listOfNotNull(current.activeEntry)).any {
                it.id != sourceServerId && it.description == "Seeded work" && it.end == null
            }
        }

        assertTrue(
            "Continued timer should persist the source description on a distinct active entry",
            (snapshot.entries + listOfNotNull(snapshot.activeEntry)).any {
                it.id != sourceServerId && it.description == "Seeded work" && it.end == null
            },
        )
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

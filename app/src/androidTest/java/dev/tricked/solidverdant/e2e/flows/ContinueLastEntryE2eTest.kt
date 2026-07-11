/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.data.model.StartTimeEntryRequest
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun continueCarriesLastEntryDescriptionIntoTheServerStart() {
        e2e.mockServer.presetLoggedInWorld() // seeds completed entry "Seeded work"
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertEntryVisible("Seeded work")

        robot.tapContinueLastEntry().assertStopButtonVisible()

        e2e.composeRule.waitUntil(WAIT_MS) {
            e2e.runPendingSync()
            e2e.mockServer.wasRequested("POST", "/time-entries")
        }

        val startRequest = e2e.mockServer.callsMatching("POST", "/time-entries").first()
        val payload = json.decodeFromString<StartTimeEntryRequest>(startRequest.body)
        assertEquals("Seeded work", payload.description)
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create -> sync -> server flow.
 *
 * Steps exercised:
 *  1. Launch logged-in with an empty history.
 *  2. Tap Start: the app writes the entry to Room + enqueues an outbox op (optimistic, "offline"),
 *     and requests a sync. The Track screen flips to the running/stop state.
 *  3. Deterministically run the enqueued work via the WorkManager TestDriver
 *     ([E2eRule.runPendingSync]) so [dev.tricked.solidverdant.sync.SyncWorker] drains the outbox.
 *  4. Assert the app POSTed the new entry to MockWebServer.
 *
 * NOTE: this validates the START outbox path (tap Start). The add-completed-entry ("+" dialog ->
 * CREATE op) path is a straightforward follow-up: preset catalogue, open the add dialog, fill and
 * save, then runPendingSync and assert the same POST. Left for the next wave to keep this skeleton
 * focused; the harness (mock statefulness + testDriver) already supports it.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OfflineCreateSyncE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @Test
    fun startedEntryIsPostedToServerOnSync() {
        // Logged-in world with no pre-existing entries.
        e2e.mockServer.presetLoggedInWorld(seededEntry = null)

        e2e.launchApp()

        val robot = TrackRobot(e2e.composeRule).waitForHistory()

        // Start tracking (optimistic local write + outbox enqueue + sync request).
        robot.tapStart().assertStopButtonVisible()

        // Deterministically drain the outbox through the real SyncWorker.
        e2e.composeRule.waitUntil(WAIT_MS) {
            e2e.runPendingSync()
            e2e.mockServer.wasRequested("POST", "/time-entries")
        }

        assertTrue(
            "Expected the started entry to be POSTed to the mock backend",
            e2e.mockServer.wasRequested("POST", "/time-entries"),
        )
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.data.local.db.SyncState
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @BackendPortable
    @Test
    fun startedEntryIsPostedToServerOnSync() {
        // Logged-in world with no pre-existing entries.
        e2e.prepare(E2eFixture.Empty)

        e2e.launchApp()

        val robot = TrackRobot(e2e.composeRule).waitForHistory()

        // Start tracking (optimistic local write + outbox enqueue + sync request).
        robot.tapStart().assertStopButtonVisible()

        // Deterministically drain the outbox through the real SyncWorker.
        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { it.activeEntry != null }

        assertTrue(
            "Expected the started entry to persist as the server's active timer",
            snapshot.activeEntry != null,
        )
    }

    @BackendPortable
    @Test
    fun completedEntryCreatedFromTrackIsPostedAndReconciledToRoom() {
        e2e.prepare(E2eFixture.Empty)
        e2e.launchApp()

        val robot = TrackRobot(e2e.composeRule).waitForHistory()
        robot.openAddEntry()
            .replaceSheetDescription("Created completed work")
            .saveSheet()
            .assertEntryVisible("Created completed work")

        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            current.entries.any { it.description == "Created completed work" && it.end != null }
        }
        val persisted = snapshot.entries.firstOrNull { it.description == "Created completed work" }
        assertNotNull("The completed CREATE operation must reach Solidtime", persisted)
        val serverEntry = requireNotNull(persisted)
        assertTrue("The created entry must be completed, not a second active timer", serverEntry.end != null)
        assertEquals("The CREATE outbox operation must be drained", 0, e2e.pendingOutboxCount())
        e2e.awaitLocalEntry(serverEntry.id, WAIT_MS) {
            it.end != null && it.syncState == SyncState.SYNCED
        }
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

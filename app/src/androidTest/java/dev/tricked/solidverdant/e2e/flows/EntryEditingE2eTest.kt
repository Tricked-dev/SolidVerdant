/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Correction flows on past entries: edit-and-sync, and delete-with-undo.
 *
 * Undo is exercised BEFORE any sync runs (WorkManager is driven manually by the harness), which
 * pins the product behavior that an undone delete must cancel the queued outbox op instead of
 * sending a DELETE the server would have to reverse.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EntryEditingE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun editedDescriptionRendersAndSyncsToServer() {
        e2e.mockServer.presetLoggedInWorld() // seeds "Seeded work" (id seed-entry-1)
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertEntryVisible("Seeded work")

        robot.tapFirstEntryEdit()
            .replaceSheetDescription("Edited via e2e")
            .saveSheet()

        // Optimistic local write renders immediately.
        robot.assertEntryVisible("Edited via e2e")

        // Outbox drains an UPDATE for the seeded id carrying the new description.
        e2e.composeRule.waitUntil(WAIT_MS) {
            e2e.runPendingSync()
            e2e.mockServer.callsMatching("PUT", "seed-entry-1").any { it.body.contains("Edited via e2e") }
        }
        assertTrue(
            "Server never received the edited description",
            e2e.mockServer.callsMatching("PUT", "seed-entry-1").any { it.body.contains("Edited via e2e") },
        )
    }

    @Test
    fun deletedEntryIsRestoredByUndoAndNoDeleteReachesTheServer() {
        e2e.mockServer.presetLoggedInWorld()
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertEntryVisible("Seeded work")

        robot.tapFirstEntryDelete().waitUntilEntryGone("Seeded work")

        robot.tapSnackbarAction(context.getString(R.string.undo))
        robot.assertEntryVisible("Seeded work")

        // Draining sync after the undo must not carry the delete to the server.
        e2e.runPendingSync()
        assertFalse(
            "Undo should have cancelled the queued DELETE",
            e2e.mockServer.wasRequested("DELETE", "seed-entry-1"),
        )
        assertTrue(
            "Seeded entry must still exist on the server",
            e2e.mockServer.timeEntries.any { it.id == "seed-entry-1" },
        )
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

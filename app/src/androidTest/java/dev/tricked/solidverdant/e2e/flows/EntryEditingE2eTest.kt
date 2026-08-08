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
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.robots.TrackRobot
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

    @BackendPortable
    @Test
    fun editedDescriptionRendersAndSyncsToServer() {
        val source = e2e.prepare(E2eFixture.Completed(e2e.completedFixtureEntry()))
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertEntryVisible("Seeded work")

        robot.tapFirstEntryEdit()
            .replaceSheetDescription("Edited via e2e")
            .saveSheet()

        // Optimistic local write renders immediately.
        robot.assertEntryVisible("Edited via e2e")

        // Outbox drains an UPDATE for the seeded id carrying the new description.
        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            current.entry(source)?.description == "Edited via e2e"
        }
        assertTrue(
            "Server never persisted the edited description",
            snapshot.entry(source)?.description == "Edited via e2e",
        )
    }

    @BackendPortable
    @Test
    fun duplicatedEntryCreatesANewServerEntryInsteadOfAdoptingItsSource() {
        val source = e2e.prepare(E2eFixture.Completed(e2e.completedFixtureEntry()))
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertEntryVisible("Seeded work")

        robot.tapFirstEntryEdit().duplicateOpenEntry()

        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            current.entries.count { it.description == "Seeded work" } == 2
        }
        val matchingEntries = snapshot.entries.filter { it.description == "Seeded work" }
        assertTrue("Duplicate should leave both the source and a new server entry", matchingEntries.size == 2)
        assertTrue("Duplicate and source must have distinct server ids", matchingEntries.map { it.id }.distinct().size == 2)
        assertTrue("Duplicate must not replace its source", matchingEntries.any { it.id == source.serverId })
    }

    @BackendPortable
    @Test
    fun deletedEntryIsRestoredByUndoAndNoDeleteReachesTheServer() {
        val source = e2e.prepare(E2eFixture.Completed(e2e.completedFixtureEntry()))
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertEntryVisible("Seeded work")

        robot.tapFirstEntryDelete().waitUntilEntryGone("Seeded work")

        robot.tapSnackbarAction(context.getString(R.string.undo))
        robot.assertEntryVisible("Seeded work")

        // Draining sync after the undo must not carry the delete to the server.
        e2e.runPendingSync()
        assertTrue(
            "Undo should leave the seeded entry persisted on the server",
            e2e.serverSnapshot().entry(source) != null,
        )
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

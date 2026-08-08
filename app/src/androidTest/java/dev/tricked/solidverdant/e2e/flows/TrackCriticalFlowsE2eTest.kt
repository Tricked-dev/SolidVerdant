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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * High-value Track workflows that must work against both the stateful mock and official Solidtime.
 *
 * These tests intentionally assert the server's resulting entries after driving the real app's
 * Room/outbox/sync path. UI-only assertions are limited to user-visible validation and control
 * state, so a green test proves both the interaction and the API contract.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TrackCriticalFlowsE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @BackendPortable
    @Test
    fun editingPersistsAllEntrySettingsToServer() {
        val original = e2e.completedFixtureEntry(description = "Original settings")
        val source = e2e.prepare(E2eFixture.Completed(original))
        val catalog = e2e.catalogFixture()
        e2e.launchApp()

        TrackRobot(e2e.composeRule)
            .waitForHistory()
            .assertEntryVisible(original.description!!)
            .tapFirstEntryEdit()
            .assertEditSettingsVisible()
            .replaceSheetDescription("Edited all settings")
            .selectSheetProjectTask(catalog.task.name)
            .selectSheetTag(catalog.tag.id)
            .toggleSheetBillable()
            .replaceSheetDuration("90")
            .saveSheet()

        val expectedEnd = Instant.parse(original.start).plusSeconds(90 * 60L).toString()
        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            current.entry(source)?.let { persisted ->
                persisted.description == "Edited all settings" &&
                    persisted.projectId == catalog.project.id &&
                    persisted.taskId == catalog.task.id &&
                    persisted.tags.any { it.id == catalog.tag.id } &&
                    persisted.billable &&
                    persisted.start == original.start &&
                    persisted.end == expectedEnd
            } == true
        }
        val persisted = requireNotNull(snapshot.entry(source))
        assertEquals("Edited all settings", persisted.description)
        assertEquals(catalog.project.id, persisted.projectId)
        assertEquals(catalog.task.id, persisted.taskId)
        assertTrue("Edited tag should reach Solidtime", persisted.tags.any { it.id == catalog.tag.id })
        assertTrue("Billable setting should reach Solidtime", persisted.billable)
        assertEquals(original.start, persisted.start)
        assertEquals(expectedEnd, persisted.end)
        assertEquals(90 * 60, persisted.duration)
    }

    @BackendPortable
    @Test
    fun duplicatePreservesEveryEditableSettingAndCreatesANewServerEntry() {
        val catalog = e2e.catalogFixture()
        val original = e2e.completedFixtureEntry(description = "Duplicate all settings").copy(
            projectId = catalog.project.id,
            taskId = catalog.task.id,
            tags = listOf(catalog.tag),
            billable = true,
        )
        val source = e2e.prepare(E2eFixture.Completed(original))
        e2e.launchApp()

        TrackRobot(e2e.composeRule)
            .waitForHistory()
            .assertEntryVisible(original.description!!)
            .tapFirstEntryEdit()
            .duplicateOpenEntry()

        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            current.entries.count { it.description == original.description } == 2
        }
        val copies = snapshot.entries.filter { it.description == original.description }
        assertEquals(2, copies.size)
        assertEquals(2, copies.map { it.id }.distinct().size)
        copies.forEach { copy ->
            assertEquals(original.start, copy.start)
            assertEquals(original.end, copy.end)
            assertEquals(catalog.project.id, copy.projectId)
            assertEquals(catalog.task.id, copy.taskId)
            assertTrue(copy.tags.any { it.id == catalog.tag.id })
            assertTrue(copy.billable)
        }
        assertTrue("Duplicate must retain the source entry", copies.any { it.id == source.serverId })
    }

    @BackendPortable
    @Test
    fun splitCreatesAdjacentServerEntriesWithMetadataOnBothHalves() {
        val catalog = e2e.catalogFixture()
        val original = e2e.completedFixtureEntry(
            description = "Split all settings",
            durationSeconds = 2 * 60 * 60,
        ).copy(
            projectId = catalog.project.id,
            taskId = catalog.task.id,
            tags = listOf(catalog.tag),
            billable = true,
        )
        val source = e2e.prepare(E2eFixture.Completed(original))
        e2e.launchApp()

        TrackRobot(e2e.composeRule)
            .waitForHistory()
            .assertEntryVisible(original.description!!)
            .tapFirstEntryEdit()
            .tapSplitAndConfirm()

        val midpoint = Instant.parse(original.start).plusSeconds(60 * 60L).toString()
        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            val halves = current.entries.filter { it.description == original.description }
            halves.size == 2 &&
                halves.any { it.id == source.serverId && it.end == midpoint } &&
                halves.any { it.id != source.serverId && it.start == midpoint && it.end == original.end }
        }
        val halves = snapshot.entries.filter { it.description == original.description }
        assertEquals(2, halves.size)
        halves.forEach { half ->
            assertEquals(catalog.project.id, half.projectId)
            assertEquals(catalog.task.id, half.taskId)
            assertTrue(half.tags.any { it.id == catalog.tag.id })
            assertTrue(half.billable)
        }
        assertEquals(midpoint, halves.first { it.id == source.serverId }.end)
        assertEquals(midpoint, halves.first { it.id != source.serverId }.start)
    }

    @BackendPortable
    @Test
    fun endBeforeStartShowsAnErrorDisablesSaveAndLeavesServerUnchanged() {
        val original = e2e.completedFixtureEntry(description = "Invalid interval")
        val source = e2e.prepare(E2eFixture.Completed(original))
        e2e.launchApp()
        val invalidEndDate = Instant.parse(original.start)
            .atZone(e2e.session.zone)
            .toLocalDate()
            .minusDays(1)

        TrackRobot(e2e.composeRule)
            .waitForHistory()
            .assertEntryVisible(original.description!!)
            .tapFirstEntryEdit()
            .changeSheetEndDate(invalidEndDate)
            .assertValidationText(context.getString(R.string.entry_error_end_before_start))
            .assertSheetSaveDisabled()
            .tapSheetCancel()

        e2e.runPendingSync()
        val persisted = requireNotNull(e2e.serverSnapshot().entry(source))
        assertEquals(original.start, persisted.start)
        assertEquals(original.end, persisted.end)
        assertEquals(original.duration, persisted.duration)
    }

    @BackendPortable
    @Test
    fun zeroDurationInputDisablesSaveAndLeavesServerUnchanged() {
        val original = e2e.completedFixtureEntry(description = "Invalid duration")
        val source = e2e.prepare(E2eFixture.Completed(original))
        e2e.launchApp()

        TrackRobot(e2e.composeRule)
            .waitForHistory()
            .assertEntryVisible(original.description!!)
            .tapFirstEntryEdit()
            .replaceSheetDuration("0")
            .assertSheetSaveDisabled()
            .tapSheetCancel()

        e2e.runPendingSync()
        val persisted = requireNotNull(e2e.serverSnapshot().entry(source))
        assertEquals(original.start, persisted.start)
        assertEquals(original.end, persisted.end)
        assertEquals(original.duration, persisted.duration)
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

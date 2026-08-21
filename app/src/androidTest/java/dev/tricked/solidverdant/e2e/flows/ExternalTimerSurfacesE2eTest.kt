/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.db.SyncState
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.E2eServerSnapshot
import dev.tricked.solidverdant.e2e.TestTags
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import dev.tricked.solidverdant.service.TimeTrackingNotificationService
import dev.tricked.solidverdant.service.TimeTrackingTileService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExternalTimerSurfacesE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val tileComponent = ComponentName(context, TimeTrackingTileService::class.java).flattenToString()

    @Before
    fun resetExternalSurfaces() {
        // Android rejects an ongoing chronometer notification whose timestamp is too old. Keep
        // this notification-focused class near the device clock; calendar tests retain the fixed
        // harness date used for deterministic day layouts.
        e2e.testClock.nowMs = Instant.now().truncatedTo(ChronoUnit.SECONDS).toEpochMilli()
        shell("cmd statusbar remove-tile $tileComponent")
        // The service is intentionally non-exported, so shell `am stopservice` is denied on the
        // device. Stop it through the app context or a previous test can leak its coroutine scope
        // and notification action state into this test.
        context.stopService(Intent(context, TimeTrackingNotificationService::class.java))
        notificationManager.cancelAll()
        context.getSharedPreferences(NOTIFICATION_STATE_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun cleanExternalSurfaces() {
        runCatching { shell("cmd statusbar remove-tile $tileComponent") }
        context.stopService(Intent(context, TimeTrackingNotificationService::class.java))
        notificationManager.cancelAll()
    }

    @BackendPortable
    @Test
    fun real_quick_settings_tile_starts_then_stops_a_server_timer() {
        e2e.prepare(E2eFixture.Empty)
        grantNotificationPermission()
        e2e.launchApp()
        TrackRobot(e2e.composeRule).waitForHistory().assertStartButtonVisible()

        shell("cmd statusbar remove-tile $tileComponent")
        shell("cmd statusbar add-tile $tileComponent")

        var lastTileClickAt = 0L
        e2e.composeRule.waitUntil(TEST_TIMEOUT_MS) {
            // Opening the tile's separate ProjectSelectionActivity briefly removes the main
            // activity's Compose root. Treat that transition as a retryable state instead of
            // failing the whole E2E run, and avoid hammering the shell command while it settles.
            val pickerVisible = runCatching {
                e2e.composeRule.onAllNodesWithTag(TestTags.TILE_PROJECT_SELECTION_START_BUTTON)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }.getOrDefault(false)
            if (!pickerVisible && SystemClock.uptimeMillis() - lastTileClickAt >= TILE_CLICK_RETRY_MS) {
                shell("cmd statusbar click-tile $tileComponent")
                lastTileClickAt = SystemClock.uptimeMillis()
            }
            pickerVisible
        }
        e2e.composeRule.onNodeWithTag(TestTags.TILE_PROJECT_SELECTION_START_BUTTON).performClick()

        val started = e2e.awaitServer(TEST_TIMEOUT_MS) { it.activeEntry != null }.activeEntry
        val startedId = requireNotNull(started).id
        waitForNotificationAction(R.string.pause)

        shell("cmd statusbar click-tile $tileComponent")

        val stopped = e2e.awaitServer(TEST_TIMEOUT_MS) { it.activeEntry == null }
        assertNotEquals(
            "The tile stop must leave the started entry as completed server history",
            null,
            stopped.entries.firstOrNull { it.id == startedId }?.end,
        )
    }

    @BackendPortable
    @Test
    fun notification_actions_execute_pause_resume_stop_against_an_externally_started_timer() {
        val original = e2e.completedFixtureEntry(
            logicalId = "externally-started",
            description = "External timer",
        ).copy(end = null, duration = null)
        val originalHandle = e2e.prepare(E2eFixture.Active(original))
        grantNotificationPermission()
        e2e.launchApp()
        TrackRobot(e2e.composeRule).waitForHistory().assertStopButtonVisible()

        waitForNotificationAction(R.string.pause).send()
        e2e.awaitServer(TEST_TIMEOUT_MS) { it.activeEntry == null }
        val resumeAction = waitForNotificationAction(R.string.resume)

        resumeAction.send()
        val stopAction = waitForNotificationAction(R.string.stop_tracking)
        val resumed = requireNotNull(
            e2e.awaitServer(TEST_TIMEOUT_MS) { it.activeEntry != null }.activeEntry,
        )
        assertNotEquals("Resume must create a new active server entry", originalHandle.serverId, resumed.id)

        stopAction.send()
        e2e.awaitServer(TEST_TIMEOUT_MS) { it.activeEntry == null }
    }

    @BackendPortable
    @Test
    fun stale_quick_start_surface_does_not_create_a_second_server_timer() {
        val originalStart = Instant.ofEpochMilli(e2e.testClock.nowMs).minus(7, ChronoUnit.HOURS)
        val original = e2e.completedFixtureEntry(
            logicalId = "external-before-quick-start",
            description = "External timer",
            start = originalStart,
        ).copy(end = null, duration = null)
        val originalHandle = e2e.prepare(E2eFixture.Active(original))
        val originalServerId = requireNotNull(originalHandle.serverId)
        grantNotificationPermission()
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertStopButtonVisible()
        waitForNotificationAtStart(originalStart)

        TimeTrackingNotificationService.quickStart(
            context = context,
            projectId = null,
            taskId = null,
            description = "Stale quick start",
            projectName = null,
            taskName = null,
        )

        // Quick Start first publishes optimistic state. Observe that new timestamp, then wait for
        // the original seven-hour timestamp to return. This proves the authoritative active-entry
        // recheck completed before we inspect the server, rather than letting an immediate
        // snapshot pass before the service coroutine ran.
        waitForNotificationStartOtherThan(originalStart)
        waitForNotificationAtStart(originalStart)
        val unchanged = e2e.awaitServer(TEST_TIMEOUT_MS) { snapshot ->
            snapshot.activeEntry?.id == originalServerId &&
                snapshot.distinctEntryIds() == setOf(originalServerId)
        }
        assertEquals(originalServerId, unchanged.activeEntry?.id)

        robot.tapStop()
        val stopped = e2e.awaitServer(TEST_TIMEOUT_MS, driveSync = true) { snapshot ->
            snapshot.activeEntry == null &&
                snapshot.distinctEntryIds() == setOf(originalServerId) &&
                snapshot.entries.firstOrNull { it.id == originalServerId }?.end != null
        }
        assertEquals(setOf(originalServerId), stopped.distinctEntryIds())
    }

    @BackendPortable
    @Test
    fun stale_resume_uses_a_newer_external_timer_instead_of_creating_another_one() {
        val now = Instant.ofEpochMilli(e2e.testClock.nowMs)
        val originalStart = now.minus(7, ChronoUnit.HOURS)
        val original = e2e.completedFixtureEntry(
            logicalId = "paused-before-external-replacement",
            description = "Original external timer",
            start = originalStart,
        ).copy(end = null, duration = null)
        val originalHandle = e2e.prepare(E2eFixture.Active(original))
        val originalServerId = requireNotNull(originalHandle.serverId)
        grantNotificationPermission()
        e2e.launchApp()
        TrackRobot(e2e.composeRule).waitForHistory().assertStopButtonVisible()

        // A seven-hour timer correctly shows the long-timer warning actions rather than Pause.
        // Invoke the same service entrypoint with the payload its notification PendingIntent
        // carries so this stays deterministic even if Android has recreated the service process.
        context.startForegroundService(
            Intent(context, TimeTrackingNotificationService::class.java)
                .setAction(TimeTrackingNotificationService.ACTION_PAUSE_TRACKING)
                .putExtra(TimeTrackingNotificationService.EXTRA_START_TIME, originalStart.toEpochMilli())
                .putExtra(TimeTrackingNotificationService.EXTRA_ORGANIZATION_ID, original.organizationId)
                .putExtra(TimeTrackingNotificationService.EXTRA_DESCRIPTION, original.description),
        )
        e2e.awaitServer(TEST_TIMEOUT_MS) { it.activeEntry == null }
        val staleResume = waitForNotificationAction(R.string.resume)

        val replacementStart = now.minus(150, ChronoUnit.MINUTES)
        val replacement = e2e.completedFixtureEntry(
            logicalId = "newer-external-replacement",
            description = "Newer external timer",
            start = replacementStart,
        ).copy(end = null, duration = null)
        val replacementHandle = e2e.createOnServer(replacement)
        val replacementServerId = requireNotNull(replacementHandle.serverId)

        staleResume.send()
        val stopReplacement = waitForNotificationAction(R.string.stop_tracking, replacementStart)
        val unchanged = e2e.awaitServer(TEST_TIMEOUT_MS) { snapshot ->
            snapshot.activeEntry?.id == replacementServerId &&
                snapshot.distinctEntryIds() == setOf(originalServerId, replacementServerId)
        }
        assertEquals(replacementServerId, unchanged.activeEntry?.id)

        stopReplacement.send()
        val stopped = e2e.awaitServer(TEST_TIMEOUT_MS) { snapshot ->
            snapshot.activeEntry == null &&
                snapshot.distinctEntryIds() == setOf(originalServerId, replacementServerId) &&
                snapshot.entries.count { it.end != null } == 2
        }
        assertEquals(setOf(originalServerId, replacementServerId), stopped.distinctEntryIds())
    }

    @BackendPortable
    @Test
    fun track_timer_edit_button_opens_running_entry_editor_without_stopping_timer() {
        val original = e2e.completedFixtureEntry(
            logicalId = "track-editable-running-entry",
            description = "Track editable timer",
        ).copy(end = null, duration = null)
        val originalHandle = e2e.prepare(E2eFixture.Active(original))
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertStopButtonVisible()

        e2e.composeRule.onNodeWithTag(TestTags.TRACK_EDIT_ACTIVE_ENTRY, useUnmergedTree = true).performClick()
        robot.assertRunningEditSettingsVisible().tapSheetCancel()

        assertEquals(originalHandle.serverId, e2e.serverSnapshot().activeEntry?.id)
    }

    @BackendPortable
    @Test
    fun track_timer_start_date_edit_updates_the_running_entry_without_stopping_timer() {
        val original = e2e.completedFixtureEntry(
            logicalId = "track-start-correction",
            description = "Track start correction",
        ).copy(end = null, duration = null)
        val originalHandle = e2e.prepare(E2eFixture.Active(original))
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory().assertStopButtonVisible()
        val adjustedStart = Instant.parse(original.start).minus(1, ChronoUnit.DAYS)

        e2e.composeRule.onNodeWithTag(TestTags.TRACK_EDIT_ACTIVE_ENTRY, useUnmergedTree = true).performClick()
        robot
            .assertRunningEditSettingsVisible()
            .changeSheetStartDate(adjustedStart.atZone(e2e.session.zone).toLocalDate())
            .saveSheet()

        val snapshot = e2e.awaitServer(TEST_TIMEOUT_MS, driveSync = true) { current ->
            current.activeEntry?.id == originalHandle.serverId &&
                current.activeEntry?.end == null &&
                current.activeEntry?.let { Instant.parse(it.start) == adjustedStart } == true
        }
        val active = requireNotNull(snapshot.activeEntry)
        assertEquals(originalHandle.serverId, active.id)
        assertEquals(adjustedStart, Instant.parse(active.start))
        assertTrue("Editing the start must leave the timer running", active.end == null)
    }

    @BackendPortable
    @Test
    fun app_outbox_room_notification_and_tile_converge_through_a_multi_action_journey() {
        e2e.prepare(E2eFixture.Empty)
        grantNotificationPermission()
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory()

        // App action: optimistic Room row + queued START, then worker rekey to the server id.
        robot.tapStart().assertStopButtonVisible(TEST_TIMEOUT_MS)
        assertTrue("App start must be represented by a pending outbox operation", e2e.pendingOutboxCount() > 0)
        val started = requireNotNull(
            e2e.awaitServer(TEST_TIMEOUT_MS, driveSync = true) { it.activeEntry != null }.activeEntry,
        )
        waitForPendingOutboxToDrain()
        e2e.awaitLocalEntry(started.id, TEST_TIMEOUT_MS) {
            it.end == null && it.syncState == SyncState.SYNCED
        }

        // Notification action: stop as Pause, then force the normal server -> Room refresh path.
        waitForNotificationAction(R.string.pause).send()
        e2e.awaitServer(TEST_TIMEOUT_MS) { it.activeEntry == null }
        val resumeAction = waitForNotificationAction(R.string.resume)
        e2e.testClock.advanceBy(1L)
        robot.tapRefresh().assertStartButtonVisible()
        e2e.awaitLocalEntry(started.id, TEST_TIMEOUT_MS) {
            it.end != null && it.syncState == SyncState.SYNCED
        }
        waitForPendingOutboxToDrain()

        // Resume from the notification creates a different server timer. Pull it into Room, then
        // stop it from the actual Quick Settings tile and prove the DAO converges again.
        resumeAction.send()
        val resumed = requireNotNull(
            e2e.awaitServer(TEST_TIMEOUT_MS) { it.activeEntry != null }.activeEntry,
        )
        assertNotEquals(started.id, resumed.id)
        waitForNotificationAction(R.string.stop_tracking)
        e2e.testClock.advanceBy(1L)
        robot.tapRefresh().assertStopButtonVisible()
        e2e.awaitLocalEntry(resumed.id, TEST_TIMEOUT_MS) {
            it.end == null && it.syncState == SyncState.SYNCED
        }

        shell("cmd statusbar remove-tile $tileComponent")
        shell("cmd statusbar add-tile $tileComponent")

        stopActiveTimerFromTile()
        e2e.testClock.advanceBy(1L)
        robot.tapRefresh().assertStartButtonVisible()
        e2e.awaitLocalEntry(resumed.id, TEST_TIMEOUT_MS) {
            it.end != null && it.syncState == SyncState.SYNCED
        }
        waitForPendingOutboxToDrain()
    }

    private fun waitForNotificationAction(labelRes: Int, expectedStart: Instant? = null): PendingIntent {
        var pendingIntent: PendingIntent? = null
        val expectedLabel = context.getString(labelRes)
        var observedLabels = emptyList<String>()
        try {
            e2e.composeRule.waitUntil(TEST_TIMEOUT_MS) {
                val notifications = notificationManager.activeNotifications
                    .asSequence()
                    .filter { expectedStart == null || it.notification.`when` == expectedStart.toEpochMilli() }
                    .toList()
                val actions = notifications
                    .asSequence()
                    .flatMap { status -> status.notification.actions.orEmpty().asSequence() }
                    .toList()
                observedLabels = actions.map { it.title.toString() }
                pendingIntent = actions.firstOrNull { it.title.toString() == expectedLabel }?.actionIntent
                pendingIntent != null
            }
        } catch (error: Throwable) {
            throw AssertionError(
                "Notification action '$expectedLabel' was not available; observed actions: $observedLabels",
                error,
            )
        }
        return requireNotNull(pendingIntent)
    }

    private fun waitForNotificationStartOtherThan(originalStart: Instant) {
        val pauseLabel = context.getString(R.string.pause)
        e2e.composeRule.waitUntil(TEST_TIMEOUT_MS) {
            notificationManager.activeNotifications.any { status ->
                status.notification.`when` != originalStart.toEpochMilli() &&
                    status.notification.actions.orEmpty().any { action ->
                        action.title.toString() == pauseLabel
                    }
            }
        }
    }

    private fun waitForNotificationAtStart(expectedStart: Instant) {
        e2e.composeRule.waitUntil(TEST_TIMEOUT_MS) {
            notificationManager.activeNotifications.any { status ->
                status.notification.`when` == expectedStart.toEpochMilli()
            }
        }
    }

    private fun stopActiveTimerFromTile(): E2eServerSnapshot {
        var lastFailure: AssertionError? = null
        repeat(TILE_CLICK_ATTEMPTS) {
            shell("cmd statusbar click-tile $tileComponent")
            try {
                return e2e.awaitServer(TILE_CLICK_ATTEMPT_TIMEOUT_MS) { it.activeEntry == null }
            } catch (failure: AssertionError) {
                lastFailure = failure
            }
        }
        throw AssertionError("Quick Settings tile did not stop the active server timer", lastFailure)
    }

    private fun grantNotificationPermission() {
        shell("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
    }

    private fun waitForPendingOutboxToDrain() {
        e2e.composeRule.waitUntil(TEST_TIMEOUT_MS) {
            e2e.runPendingSync()
            e2e.pendingOutboxCount() == 0
        }
        assertEquals(0, e2e.pendingOutboxCount())
    }

    private fun shell(command: String): String = device.executeShellCommand(command)

    private fun E2eServerSnapshot.distinctEntryIds(): Set<String> = buildSet {
        entries.mapTo(this) { it.id }
        activeEntry?.id?.let(::add)
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 15_000L
        const val TILE_CLICK_RETRY_MS = 500L
        const val TILE_CLICK_ATTEMPTS = 3
        const val TILE_CLICK_ATTEMPT_TIMEOUT_MS = 5_000L
        const val NOTIFICATION_STATE_PREFERENCES = "time_tracking_notification_state"
    }
}

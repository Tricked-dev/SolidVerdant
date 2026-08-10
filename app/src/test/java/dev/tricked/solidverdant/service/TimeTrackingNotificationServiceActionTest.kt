/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Organization
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TimeTrackingNotificationServiceActionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private val organization = Organization(
        id = "organization-id",
        name = "Organization",
        currency = "EUR",
    )
    private val membership = Membership(
        id = "membership-id",
        role = "member",
        organization = organization,
    )
    private val user = User(
        id = "user-id",
        name = "Test User",
        email = "test@example.invalid",
    )
    private val project = Project(
        id = "project-id",
        name = "Common project",
        color = "#123456",
    )
    private val task = Task(
        id = "task-id",
        name = "Common task",
        projectId = project.id,
        createdAt = "2026-08-10T08:00:00Z",
        updatedAt = "2026-08-10T08:00:00Z",
    )
    private val activeEntry = TimeEntry(
        id = "active-entry-id",
        description = "Common work",
        userId = user.id,
        start = "2026-08-10T08:00:00Z",
        projectId = project.id,
        taskId = task.id,
        organizationId = organization.id,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        notificationManager.cancelAll()
        context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun repeated_pause_actions_do_not_start_a_second_mutation_while_the_first_is_in_flight() {
        val authRepository = mockk<AuthRepository>()
        val lookupStarted = CompletableDeferred<Unit>()
        val releaseLookup = CompletableDeferred<Unit>()
        coEvery { authRepository.getActiveTimeEntry() } coAnswers {
            lookupStarted.complete(Unit)
            releaseLookup.await()
            Result.failure(RuntimeException("test cancellation"))
        }

        val service = Robolectric.buildService(TimeTrackingNotificationService::class.java)
            .create()
            .get()
            .also { it.authRepository = authRepository }

        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_PAUSE_TRACKING), 0, 1)
        assertTrue("The first pause action should reach the repository", lookupStarted.isCompleted)

        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_PAUSE_TRACKING), 0, 2)
        coVerify(exactly = 1) { authRepository.getActiveTimeEntry() }

        releaseLookup.complete(Unit)
    }

    @Test
    fun repeated_stop_actions_do_not_start_a_second_mutation_while_the_first_is_in_flight() {
        val authRepository = mockk<AuthRepository>()
        val lookupStarted = CompletableDeferred<Unit>()
        val releaseLookup = CompletableDeferred<Unit>()
        coEvery { authRepository.getActiveTimeEntry() } coAnswers {
            lookupStarted.complete(Unit)
            releaseLookup.await()
            Result.failure(RuntimeException("test cancellation"))
        }
        val service = createService(authRepository)
        service.onStartCommand(startTrackingIntent(), 0, 1)

        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_STOP_TRACKING), 0, 2)
        assertTrue("The first stop action should reach the repository", lookupStarted.isCompleted)
        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_STOP_TRACKING), 0, 3)

        coVerify(exactly = 1) { authRepository.getActiveTimeEntry() }
        releaseLookup.complete(Unit)
    }

    @Test
    fun repeated_resume_actions_do_not_start_a_second_timer_while_the_first_is_in_flight() {
        val authRepository = mockk<AuthRepository>()
        val lookupStarted = CompletableDeferred<Unit>()
        val releaseLookup = CompletableDeferred<Unit>()
        coEvery { authRepository.getCurrentUser() } coAnswers {
            lookupStarted.complete(Unit)
            releaseLookup.await()
            Result.failure(RuntimeException("test cancellation"))
        }
        val service = createService(authRepository)
        service.onStartCommand(startTrackingIntent(), 0, 1)
        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_SHOW_PAUSED), 0, 2)

        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_RESUME_TRACKING), 0, 3)
        assertTrue("The first resume action should reach the repository", lookupStarted.isCompleted)
        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_RESUME_TRACKING), 0, 4)

        coVerify(exactly = 1) { authRepository.getCurrentUser() }
        releaseLookup.complete(Unit)
    }

    @Test
    fun idle_refresh_during_pause_mutation_does_not_cancel_or_replace_the_tracking_surface() {
        val authRepository = mockk<AuthRepository>()
        val lookupStarted = CompletableDeferred<Unit>()
        val releaseLookup = CompletableDeferred<Unit>()
        coEvery { authRepository.getActiveTimeEntry() } coAnswers {
            lookupStarted.complete(Unit)
            releaseLookup.await()
            Result.failure(RuntimeException("test network failure"))
        }
        val service = createService(authRepository)
        service.onStartCommand(startTrackingIntent(), 0, 1)

        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_PAUSE_TRACKING), 0, 2)
        assertTrue("The pause action should reach the repository", lookupStarted.isCompleted)
        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_SHOW_IDLE), 0, 3)

        val notification = checkNotNull(shadowOf(service).lastForegroundNotification)
        notificationActionIntent(notification, R.string.pause)
        releaseLookup.complete(Unit)
    }

    @Test
    fun failed_pause_action_allows_a_subsequent_retry() {
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.getActiveTimeEntry() } returns Result.failure(
            RuntimeException("test network failure"),
        )

        val service = Robolectric.buildService(TimeTrackingNotificationService::class.java)
            .create()
            .get()
            .also { it.authRepository = authRepository }

        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_PAUSE_TRACKING), 0, 1)
        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_PAUSE_TRACKING), 0, 2)

        coVerify(exactly = 2) { authRepository.getActiveTimeEntry() }
    }

    @Test
    fun failed_resume_action_allows_a_subsequent_retry() {
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.getCurrentUser() } returns Result.failure(
            RuntimeException("test network failure"),
        )

        val service = Robolectric.buildService(TimeTrackingNotificationService::class.java)
            .create()
            .get()
            .also { it.authRepository = authRepository }
        val resumeIntent = actionIntent(TimeTrackingNotificationService.ACTION_RESUME_TRACKING)

        service.onStartCommand(resumeIntent, 0, 1)
        service.onStartCommand(resumeIntent, 0, 2)

        coVerify(exactly = 2) { authRepository.getCurrentUser() }
    }

    @Test
    fun pause_treats_a_failed_stop_response_as_success_when_the_server_confirms_no_active_entry() {
        val pauseIntent = trackingActionIntent(R.string.pause)
        val authRepository = mockk<AuthRepository>(relaxed = true) {
            coEvery { getActiveTimeEntry() } returnsMany listOf(
                Result.success(activeEntry),
                Result.success(null),
            )
            coEvery { getCurrentUser() } returns Result.success(user)
            coEvery { stopTimeEntry(any(), any(), any(), any()) } returns Result.failure(
                RuntimeException("response could not be decoded"),
            )
        }
        val service = createService(authRepository)

        service.onStartCommand(pauseIntent, 0, 1)

        coVerify(exactly = 2) { authRepository.getActiveTimeEntry() }
        val notification = checkNotNull(shadowOf(notificationManager).getNotification(NOTIFICATION_ID))
        assertEquals(context.getString(R.string.notification_paused_title), notificationTitle(notification))
        notificationActionIntent(notification, R.string.resume)
    }

    @Test
    fun pause_keeps_tracking_controls_and_shows_an_error_when_stop_and_confirmation_both_fail() {
        val pauseIntent = trackingActionIntent(R.string.pause)
        val authRepository = mockk<AuthRepository>(relaxed = true) {
            coEvery { getActiveTimeEntry() } returns Result.success(activeEntry)
            coEvery { getCurrentUser() } returns Result.success(user)
            coEvery { stopTimeEntry(any(), any(), any(), any()) } returns Result.failure(
                RuntimeException("network failure"),
            )
        }
        val service = createService(authRepository)

        service.onStartCommand(pauseIntent, 0, 1)

        coVerify(exactly = 2) { authRepository.getActiveTimeEntry() }
        val notification = checkNotNull(shadowOf(service).lastForegroundNotification)
        notificationActionIntent(notification, R.string.pause)
        val errorNotification = checkNotNull(shadowOf(notificationManager).getNotification(NOTIFICATION_ID_ERROR))
        assertEquals(context.getString(R.string.notification_tracking_action_failed), notificationTitle(errorNotification))
    }

    @Test
    fun stop_treats_a_failed_response_as_success_when_server_confirms_timer_is_already_inactive() {
        val stopIntent = trackingActionIntent(R.string.stop_tracking)
        val authRepository = mockk<AuthRepository>(relaxed = true) {
            coEvery { getActiveTimeEntry() } returnsMany listOf(
                Result.success(activeEntry),
                Result.success(null),
            )
            coEvery { getCurrentUser() } returns Result.success(user)
            coEvery { stopTimeEntry(any(), any(), any(), any()) } returns Result.failure(
                RuntimeException("response lost after commit"),
            )
        }
        val service = createService(authRepository)

        service.onStartCommand(stopIntent, 0, 1)

        coVerify(exactly = 2) { authRepository.getActiveTimeEntry() }
        assertNull(shadowOf(notificationManager).getNotification(NOTIFICATION_ID_ERROR))
    }

    @Test
    fun pause_from_notification_after_process_recreation_stops_entry_and_restores_paused_context() {
        val pauseIntent = trackingActionIntent(R.string.pause)
        val authRepository = mockk<AuthRepository>(relaxed = true) {
            coEvery { getActiveTimeEntry() } returns Result.success(activeEntry)
            coEvery { getCurrentUser() } returns Result.success(user)
            coEvery {
                stopTimeEntry(
                    organizationId = organization.id,
                    timeEntryId = activeEntry.id,
                    userId = user.id,
                    startTime = activeEntry.start,
                )
            } returns Result.success(activeEntry.copy(end = "2026-08-10T09:00:00Z"))
        }
        val service = createService(authRepository)

        service.onStartCommand(pauseIntent, 0, 1)

        coVerify(exactly = 1) {
            authRepository.stopTimeEntry(
                organizationId = organization.id,
                timeEntryId = activeEntry.id,
                userId = user.id,
                startTime = activeEntry.start,
            )
        }
        val pausedNotification = checkNotNull(shadowOf(notificationManager).getNotification(NOTIFICATION_ID))
        assertEquals(context.getString(R.string.notification_paused_title), notificationTitle(pausedNotification))
        val resumeIntent = notificationActionIntent(pausedNotification, R.string.resume)
        assertEquals(project.name, resumeIntent.getStringExtra(TimeTrackingNotificationService.EXTRA_PROJECT_NAME))
        assertEquals(task.name, resumeIntent.getStringExtra(TimeTrackingNotificationService.EXTRA_TASK_NAME))
        assertEquals(activeEntry.description, resumeIntent.getStringExtra(TimeTrackingNotificationService.EXTRA_DESCRIPTION))
    }

    @Test
    fun queued_refresh_for_the_just_paused_entry_does_not_replace_the_resume_notification() {
        val authRepository = mockk<AuthRepository>(relaxed = true) {
            coEvery { getActiveTimeEntry() } returns Result.success(activeEntry)
            coEvery { getCurrentUser() } returns Result.success(user)
            coEvery { stopTimeEntry(any(), any(), any(), any()) } returns Result.success(
                activeEntry.copy(end = "2026-08-10T09:00:00Z"),
            )
        }
        val service = createService(authRepository)

        service.onStartCommand(startTrackingIntent(), 0, 1)
        service.onStartCommand(trackingActionIntent(R.string.pause), 0, 2)
        service.onStartCommand(startTrackingIntent(), 0, 3)

        val notification = checkNotNull(shadowOf(notificationManager).getNotification(NOTIFICATION_ID))
        assertEquals(context.getString(R.string.notification_paused_title), notificationTitle(notification))
        notificationActionIntent(notification, R.string.resume)
    }

    @Test
    fun queued_refresh_after_service_recreation_reasserts_the_resume_notification() {
        val authRepository = mockk<AuthRepository>(relaxed = true) {
            coEvery { getActiveTimeEntry() } returns Result.success(activeEntry)
            coEvery { getCurrentUser() } returns Result.success(user)
            coEvery { stopTimeEntry(any(), any(), any(), any()) } returns Result.success(
                activeEntry.copy(end = "2026-08-10T09:00:00Z"),
            )
        }
        val originalService = createService(authRepository)
        originalService.onStartCommand(startTrackingIntent(), 0, 1)
        originalService.onStartCommand(trackingActionIntent(R.string.pause), 0, 2)

        val recreatedService = createService(authRepository)
        recreatedService.onStartCommand(startTrackingIntent(), 0, 1)

        val notification = checkNotNull(shadowOf(notificationManager).getNotification(NOTIFICATION_ID))
        assertEquals(context.getString(R.string.notification_paused_title), notificationTitle(notification))
        notificationActionIntent(notification, R.string.resume)
    }

    @Test
    fun always_show_idle_refresh_does_not_replace_the_paused_resume_notification() {
        val authRepository = mockk<AuthRepository>(relaxed = true) {
            coEvery { getActiveTimeEntry() } returns Result.success(activeEntry)
            coEvery { getCurrentUser() } returns Result.success(user)
            coEvery { stopTimeEntry(any(), any(), any(), any()) } returns Result.success(
                activeEntry.copy(end = "2026-08-10T09:00:00Z"),
            )
        }
        val service = createService(authRepository)
        service.onStartCommand(startTrackingIntent(), 0, 1)
        service.onStartCommand(trackingActionIntent(R.string.pause), 0, 2)

        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_SHOW_IDLE), 0, 3)

        val notification = checkNotNull(shadowOf(notificationManager).getNotification(NOTIFICATION_ID))
        assertEquals(context.getString(R.string.notification_paused_title), notificationTitle(notification))
        notificationActionIntent(notification, R.string.resume)
    }

    @Test
    fun genuinely_new_external_timer_replaces_persisted_paused_state() {
        val service = createService(mockk(relaxed = true))
        service.onStartCommand(startTrackingIntent(), 0, 1)
        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_SHOW_PAUSED), 0, 2)
        val newStart = Instant.parse(activeEntry.start).plusSeconds(60)

        service.onStartCommand(
            startTrackingIntent().putExtra(TimeTrackingNotificationService.EXTRA_START_TIME, newStart.toEpochMilli()),
            0,
            3,
        )

        val notification = checkNotNull(shadowOf(service).lastForegroundNotification)
        assertEquals(context.getString(R.string.time_tracking_notification_title), notificationTitle(notification))
        notificationActionIntent(notification, R.string.pause)
        assertFalse(context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE).contains(PAUSED_START_PREFERENCE))
    }

    @Test
    fun resume_from_notification_after_process_recreation_starts_matching_entry_and_restores_tracking_context() {
        val resumeIntent = pausedActionIntent(R.string.resume)
        val resumedEntry = activeEntry.copy(
            id = "resumed-entry-id",
            start = "2026-08-10T10:00:00Z",
        )
        val authRepository = mockk<AuthRepository>(relaxed = true) {
            coEvery { getCurrentUser() } returns Result.success(user)
            coEvery { getCurrentMembership() } returns membership
            coEvery { getProjects(organization.id) } returns Result.success(listOf(project))
            coEvery { getTasks(organization.id) } returns Result.success(listOf(task))
            coEvery {
                startTimeEntry(
                    organizationId = organization.id,
                    memberId = membership.id,
                    userId = user.id,
                    projectId = project.id,
                    taskId = task.id,
                    description = activeEntry.description.orEmpty(),
                )
            } returns Result.success(resumedEntry)
        }
        val service = createService(authRepository)

        service.onStartCommand(resumeIntent, 0, 1)

        coVerify(exactly = 1) {
            authRepository.startTimeEntry(
                organizationId = organization.id,
                memberId = membership.id,
                userId = user.id,
                projectId = project.id,
                taskId = task.id,
                description = activeEntry.description.orEmpty(),
            )
        }
        val trackingNotification = checkNotNull(shadowOf(service).lastForegroundNotification)
        assertEquals(context.getString(R.string.time_tracking_notification_title), notificationTitle(trackingNotification))
        val nextPauseIntent = notificationActionIntent(trackingNotification, R.string.pause)
        assertEquals(
            Instant.parse(resumedEntry.start).toEpochMilli(),
            nextPauseIntent.getLongExtra(TimeTrackingNotificationService.EXTRA_START_TIME, -1L),
        )
        assertEquals(project.name, nextPauseIntent.getStringExtra(TimeTrackingNotificationService.EXTRA_PROJECT_NAME))
        assertEquals(task.name, nextPauseIntent.getStringExtra(TimeTrackingNotificationService.EXTRA_TASK_NAME))
        assertEquals(activeEntry.description, nextPauseIntent.getStringExtra(TimeTrackingNotificationService.EXTRA_DESCRIPTION))
    }

    @Test
    fun stop_active_timer_from_notification_after_process_recreation_stops_server_entry() {
        val stopIntent = trackingActionIntent(R.string.stop_tracking)
        val authRepository = mockk<AuthRepository>(relaxed = true) {
            coEvery { getActiveTimeEntry() } returns Result.success(activeEntry)
            coEvery { getCurrentUser() } returns Result.success(user)
            coEvery { stopTimeEntry(any(), any(), any(), any()) } returns Result.success(
                activeEntry.copy(end = "2026-08-10T09:00:00Z"),
            )
        }
        val service = createService(authRepository)

        service.onStartCommand(stopIntent, 0, 1)

        coVerify(exactly = 1) {
            authRepository.stopTimeEntry(
                organizationId = organization.id,
                timeEntryId = activeEntry.id,
                userId = user.id,
                startTime = activeEntry.start,
            )
        }
    }

    @Test
    fun stop_paused_timer_from_notification_after_process_recreation_does_not_stop_an_entry_twice() {
        val stopIntent = pausedActionIntent(R.string.stop)
        val authRepository = mockk<AuthRepository>(relaxed = true)
        val service = createService(authRepository)

        service.onStartCommand(stopIntent, 0, 1)

        coVerify(exactly = 0) { authRepository.getActiveTimeEntry() }
        coVerify(exactly = 0) { authRepository.stopTimeEntry(any(), any(), any(), any()) }
    }

    private fun actionIntent(action: String) = Intent(
        context,
        TimeTrackingNotificationService::class.java,
    ).apply {
        this.action = action
    }

    private fun createService(authRepository: AuthRepository) = Robolectric.buildService(TimeTrackingNotificationService::class.java)
        .create()
        .get()
        .also {
            it.authRepository = authRepository
            it.settingsDataStore = mockk<SettingsDataStore>(relaxed = true) {
                every { alwaysShowNotification } returns flowOf(false)
                every { longTimerHours } returns flowOf(8)
            }
        }

    private fun trackingActionIntent(actionLabel: Int): Intent {
        val service = Robolectric.buildService(TimeTrackingNotificationService::class.java)
            .create()
            .get()
        service.onStartCommand(startTrackingIntent(), 0, 1)
        val notification = checkNotNull(shadowOf(service).lastForegroundNotification)
        return notificationActionIntent(notification, actionLabel)
    }

    private fun pausedActionIntent(actionLabel: Int): Intent {
        val service = Robolectric.buildService(TimeTrackingNotificationService::class.java)
            .create()
            .get()
        service.onStartCommand(startTrackingIntent(), 0, 1)
        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_SHOW_PAUSED), 0, 2)
        val notification = checkNotNull(shadowOf(notificationManager).getNotification(NOTIFICATION_ID))
        return notificationActionIntent(notification, actionLabel)
    }

    private fun startTrackingIntent() = Intent(
        context,
        TimeTrackingNotificationService::class.java,
    ).apply {
        action = TimeTrackingNotificationService.ACTION_START_TRACKING
        putExtra(TimeTrackingNotificationService.EXTRA_START_TIME, Instant.parse(activeEntry.start).toEpochMilli())
        putExtra(TimeTrackingNotificationService.EXTRA_PROJECT_NAME, project.name)
        putExtra(TimeTrackingNotificationService.EXTRA_TASK_NAME, task.name)
        putExtra(TimeTrackingNotificationService.EXTRA_DESCRIPTION, activeEntry.description)
    }

    private fun notificationActionIntent(notification: Notification, actionLabel: Int): Intent {
        val action = notification.actions.single { it.title == context.getString(actionLabel) }
        return checkNotNull(shadowOf(action.actionIntent).savedIntent)
    }

    private fun notificationTitle(notification: Notification) = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private companion object {
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_ID_ERROR = 1002
        const val STATE_PREFERENCES = "time_tracking_notification_state"
        const val PAUSED_START_PREFERENCE = "paused_start_epoch_ms"
    }
}

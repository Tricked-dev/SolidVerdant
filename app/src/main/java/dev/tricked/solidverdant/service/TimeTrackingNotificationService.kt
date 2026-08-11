/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.tricked.solidverdant.MainActivity
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.ui.tile.ProjectSelectionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * Foreground service that displays a persistent notification while time tracking is active.
 * This provides better background network access and gives users control over tracking.
 */
@AndroidEntryPoint
class TimeTrackingNotificationService : Service() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var startTime: Instant? = null
    private var organizationId: String? = null
    private var projectId: String? = null
    private var taskId: String? = null
    private var projectName: String? = null
    private var taskName: String? = null
    private var description: String? = null
    private var isTracking: Boolean = false
    private var isPaused: Boolean = false
    private var isForeground: Boolean = false
    private var pausedAt: Instant? = null
    private var elapsedBeforePauseSeconds: Long = 0
    private var mutationInProgress: Boolean = false
    private var liveUpdateEnabled: Boolean = false
    private var longWarningJob: Job? = null
    private var longTimerWarningVisible = false
    private var stateGeneration = 0L

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val statePreferences by lazy {
        getSharedPreferences(STATE_PREFERENCES, MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            settingsDataStore.liveUpdateEnabled.collect { enabled ->
                val changed = liveUpdateEnabled != enabled
                liveUpdateEnabled = enabled
                if (changed && isTracking) {
                    refreshNotificationIfVisible()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("NotificationService onStartCommand: action=${intent?.action}")
        if (intent != null && isStaleNotificationAction(intent)) {
            Timber.d("Ignoring notification action for a replaced timer")
            refreshNotificationIfVisible()
            return START_NOT_STICKY
        }
        if (intent != null && intent.action in FOREGROUND_ACTIONS) {
            restoreEntryState(intent)
            if (!isForeground) {
                // Notification actions can relaunch this service after Android has reclaimed the
                // detached paused-service process. Promote it before starting any live API work so
                // the action coroutine is not cancelled while the service is in the background.
                val bootstrapNotification = if (intent.action == ACTION_SHOW_LONG_TIMER_WARNING) {
                    buildRestoringTrackingNotification()
                } else {
                    buildNotification()
                }
                startForegroundCompat(bootstrapNotification)
            }
        }
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val requestedStartTime = Instant.ofEpochMilli(
                    intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis()),
                )
                // An active-entry refresh may already be queued when Pause finishes its server
                // mutation. Do not let that stale refresh resurrect the just-paused entry and
                // replace its Resume notification. A genuinely new active entry has a different
                // start timestamp and is still accepted below.
                if (isPaused && startTime == requestedStartTime || isPersistedPausedStart(requestedStartTime)) {
                    Timber.d("Ignoring stale active-entry refresh for the paused timer")
                    isTracking = false
                    isPaused = true
                    publishNotification()
                    return START_NOT_STICKY
                }
                // Active-entry refreshes also flow through this action to update notification
                // metadata. Re-arming an already-due warning here creates an immediate
                // WorkManager -> service -> refresh loop and also discards Keep Running snoozes.
                val isSameEntryRefresh = isTracking && startTime == requestedStartTime

                stateGeneration += 1
                isTracking = true
                isPaused = false
                clearPersistedPausedStart()
                startTime = requestedStartTime
                organizationId = intent.getStringExtra(EXTRA_ORGANIZATION_ID)
                projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
                taskId = intent.getStringExtra(EXTRA_TASK_ID)
                projectName = intent.getStringExtra(EXTRA_PROJECT_NAME)
                taskName = intent.getStringExtra(EXTRA_TASK_NAME)
                description = intent.getStringExtra(EXTRA_DESCRIPTION)

                if (!isSameEntryRefresh) {
                    longTimerWarningVisible = false
                }
                publishNotification()
                if (!isSameEntryRefresh) {
                    scheduleLongTimerWarning()
                }
            }

            ACTION_SHOW_IDLE -> {
                if (!hasPersistedPausedStart()) showIdleNotification(startId)
            }

            ACTION_STOP_TRACKING -> {
                handleStopTracking(startId)
            }

            ACTION_PAUSE_TRACKING -> {
                handlePauseTracking()
            }

            ACTION_SHOW_PAUSED -> {
                showPausedNotification()
            }

            ACTION_RESUME_TRACKING -> {
                handleResumeTracking(intent)
            }

            ACTION_KEEP_RUNNING -> {
                longTimerWarningVisible = false
                publishNotification()
                scheduleLongTimerWarning(snoozeSeconds = 3600)
            }

            ACTION_REFRESH_LONG_TIMER -> {
                if (isTracking) {
                    longTimerWarningVisible = false
                    publishNotification()
                    scheduleLongTimerWarning()
                }
            }

            ACTION_SHOW_LONG_TIMER_WARNING -> {
                handleShowLongTimerWarning(
                    entryStartEpochMs = intent.getLongExtra(EXTRA_ENTRY_START_EPOCH_MS, -1L),
                )
            }

            ACTION_QUICK_START -> handleQuickStart(intent)
        }

        // State is restored explicitly from the server by the app and BootReceiver. A sticky
        // restart has no Intent extras to rebuild the timer and can replay stale notification state.
        return START_NOT_STICKY
    }

    /**
     * A PendingIntent from an already-replaced notification can still be delivered after the new
     * timer is visible. Reject it before [restoreEntryState] can overwrite the current surface.
     * Process-restored actions remain valid because there is no in-memory timer to compare yet.
     */
    private fun isStaleNotificationAction(intent: Intent): Boolean {
        if (intent.action !in ENTRY_MUTATION_ACTIONS || !intent.hasExtra(EXTRA_START_TIME)) return false
        val currentStart = startTime ?: return false
        if (!isTracking && !isPaused) return false
        val requestedStart = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_START_TIME, 0L))
        return requestedStart != currentStart
    }

    private fun handleQuickStart(intent: Intent) {
        if (mutationInProgress) return
        mutationInProgress = true
        stateGeneration += 1
        val quickStartGeneration = stateGeneration
        clearPersistedPausedStart()
        isTracking = true
        isPaused = false
        startTime = Instant.now()
        projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        taskId = intent.getStringExtra(EXTRA_TASK_ID)
        projectName = intent.getStringExtra(EXTRA_PROJECT_NAME)
        taskName = intent.getStringExtra(EXTRA_TASK_NAME)
        description = intent.getStringExtra(EXTRA_DESCRIPTION)
        publishNotification()
        scheduleLongTimerWarning()

        serviceScope.launch {
            val membership = authRepository.getCurrentMembership()
            val user = authRepository.getCurrentUser().getOrNull()
            if (membership == null || user == null) {
                mutationInProgress = false
                Timber.e("Quick start failed: missing membership or user")
                if (stateGeneration == quickStartGeneration) stopService()
                return@launch
            }
            if (stateGeneration != quickStartGeneration) {
                // An authoritative external-timer refresh replaced this optimistic Quick Start
                // while authentication was loading. Do not POST a second active timer.
                mutationInProgress = false
                return@launch
            }
            organizationId = membership.organizationId

            authRepository.startTimeEntry(
                organizationId = membership.organizationId,
                memberId = membership.id,
                userId = user.id,
                projectId = intent.getStringExtra(EXTRA_PROJECT_ID),
                taskId = intent.getStringExtra(EXTRA_TASK_ID),
                description = description.orEmpty(),
            ).onSuccess { entry ->
                mutationInProgress = false
                if (stateGeneration == quickStartGeneration) {
                    organizationId = entry.organizationId
                    startTime = Instant.parse(entry.start)
                    refreshNotificationIfVisible()
                }
            }.onFailure { error ->
                mutationInProgress = false
                Timber.e(error, "Quick start failed")
                if (stateGeneration == quickStartGeneration) stopService()
            }
        }
    }

    /**
     * Durable entrypoint used by [LongTimerWarningWorker]: shows the forgotten-timer warning even
     * if this service process died and was relaunched fresh (no in-memory tracking state). Falls
     * back to querying the server for the active entry, mirroring [dev.tricked.solidverdant.receiver.BootReceiver]'s
     * restore path, and only shows the warning if it is still the same entry the warning was
     * scheduled for.
     */
    private fun handleShowLongTimerWarning(entryStartEpochMs: Long) {
        if (isTracking) {
            val currentStartMatches = entryStartEpochMs < 0 ||
                startTime?.toEpochMilli() == entryStartEpochMs
            if (currentStartMatches) {
                longTimerWarningVisible = true
                publishNotification()
            }
            return
        }

        serviceScope.launch {
            val activeEntry = authRepository.getActiveTimeEntry().getOrNull()
            if (activeEntry == null) {
                Timber.d("Long timer warning skipped: no active entry")
                stopService()
                return@launch
            }
            val activeStart = runCatching { Instant.parse(activeEntry.start) }.getOrNull()
            if (activeStart == null ||
                (entryStartEpochMs >= 0 && activeStart.toEpochMilli() != entryStartEpochMs)
            ) {
                Timber.d("Long timer warning skipped: active entry no longer matches")
                stopService()
                return@launch
            }

            isTracking = true
            startTime = activeStart
            organizationId = activeEntry.organizationId
            projectId = activeEntry.projectId
            taskId = activeEntry.taskId
            description = activeEntry.description
            longTimerWarningVisible = true
            publishNotification()
        }
    }

    private fun handleStopTracking(startId: Int) {
        if (mutationInProgress) return
        val wasPaused = isPaused
        val stopTargetStart = startTime
        val stopGeneration = stateGeneration
        mutationInProgress = true

        serviceScope.launch {
            val stopped = if (wasPaused) {
                Result.success(false)
            } else {
                stopActiveEntry(stopTargetStart)
            }

            stopped.fold(
                onSuccess = {
                    mutationInProgress = false
                    notificationManager.cancel(NOTIFICATION_ID_ERROR)
                    if (stateGeneration == stopGeneration) {
                        clearPersistedPausedStart()
                        if (settingsDataStore.alwaysShowNotification.first()) {
                            showIdleNotification(startId)
                        } else {
                            stopService()
                        }
                    }
                },
                onFailure = { error ->
                    mutationInProgress = false
                    Timber.e(error, "Failed to stop time entry from notification")
                    if (stateGeneration == stopGeneration) {
                        showMutationError(R.string.notification_stop_failed)
                    }
                },
            )
        }
    }

    private fun showMutationError(messageRes: Int) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            ERROR_ACTION_REQUEST_CODE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ERROR)
            .setContentTitle(getString(R.string.notification_tracking_action_failed))
            .setContentText(getString(messageRes))
            .setSmallIcon(R.drawable.ic_timer)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
    }

    private fun restoreEntryState(intent: Intent) {
        intent.getStringExtra(EXTRA_PROJECT_NAME)?.let { projectName = it }
        intent.getStringExtra(EXTRA_TASK_NAME)?.let { taskName = it }
        intent.getStringExtra(EXTRA_DESCRIPTION)?.let { description = it }
        intent.getStringExtra(EXTRA_PROJECT_ID)?.let { projectId = it }
        intent.getStringExtra(EXTRA_TASK_ID)?.let { taskId = it }
        intent.getStringExtra(EXTRA_ORGANIZATION_ID)?.let { organizationId = it }
        if (intent.hasExtra(EXTRA_START_TIME)) {
            startTime = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_START_TIME, 0L))
        }
        if (intent.hasExtra(EXTRA_PAUSED)) {
            isPaused = intent.getBooleanExtra(EXTRA_PAUSED, false)
            isTracking = !isPaused
        }
    }

    private fun confirmPausedState() {
        cancelLongTimerWarning()
        longTimerWarningVisible = false
        val now = Instant.now()
        pausedAt = now
        elapsedBeforePauseSeconds = startTime?.let { now.epochSecond - it.epochSecond } ?: 0
        isPaused = true
        isTracking = false
        persistPausedStart()
        publishNotification()
    }

    private suspend fun resumeActiveEntry(
        requestedOrganizationId: String?,
        requestedProjectId: String?,
        requestedTaskId: String?,
        requestedProjectName: String?,
        requestedTaskName: String?,
        requestedDescription: String?,
    ): Result<Instant> = runCatching {
        val user = authRepository.getCurrentUser().getOrThrow()
        val membership = authRepository.getCurrentMembership()
            ?: error("No current membership")
        check(requestedOrganizationId == null || membership.organizationId == requestedOrganizationId) {
            "The paused timer belongs to a different organization"
        }
        // Exact IDs survive duplicate names and catalogue renames. Name lookup remains only for
        // notification PendingIntents created by an older app version before IDs were included.
        val resolvedProjectId = requestedProjectId ?: authRepository.getProjects(membership.organizationId)
            .getOrThrow()
            .find { it.name == requestedProjectName }
            ?.id
        val resolvedTaskId = requestedTaskId ?: authRepository.getTasks(membership.organizationId)
            .getOrThrow()
            .find { it.name == requestedTaskName }
            ?.id

        val entry = authRepository.startTimeEntry(
            organizationId = membership.organizationId,
            memberId = membership.id,
            userId = user.id,
            projectId = resolvedProjectId,
            taskId = resolvedTaskId,
            description = requestedDescription.orEmpty(),
        ).getOrThrow()
        Instant.parse(entry.start)
    }

    /** Stop the account-wide active entry. Used only by explicit notification actions. */
    private suspend fun stopActiveEntry(expectedStart: Instant?): Result<Boolean> {
        val activeEntry = authRepository.getActiveTimeEntry()
            .getOrElse { return Result.failure(it) }
            ?: return Result.success(false)
        if (expectedStart != null) {
            val activeStart = runCatching { Instant.parse(activeEntry.start) }.getOrNull()
            if (activeStart?.toEpochMilli() != expectedStart.toEpochMilli()) {
                Timber.d("Ignoring stale notification action for a replaced active timer")
                return Result.success(false)
            }
        }
        val user = authRepository.getCurrentUser()
            .getOrElse { return Result.failure(it) }

        val stopResult = authRepository.stopTimeEntry(
            organizationId = activeEntry.organizationId,
            timeEntryId = activeEntry.id,
            userId = user.id,
            startTime = activeEntry.start,
        )
        if (stopResult.isSuccess) return Result.success(true)

        // The remote update may have committed even if its response could not be decoded or the
        // connection dropped while returning it. Confirm the authoritative state before showing
        // a retry error: retrying an already-completed stop is both confusing and unnecessary.
        val confirmedActiveEntry = authRepository.getActiveTimeEntry()
        if (confirmedActiveEntry.isSuccess && confirmedActiveEntry.getOrNull() == null) {
            Timber.d("Stop response failed, but the server confirms there is no active entry")
            return Result.success(true)
        }
        return Result.failure(checkNotNull(stopResult.exceptionOrNull()))
    }

    private fun showIdleNotification(startId: Int) {
        cancelLongTimerWarning()
        clearPersistedPausedStart()
        longTimerWarningVisible = false
        isTracking = false
        isPaused = false
        startTime = null
        organizationId = null
        projectId = null
        taskId = null
        projectName = null
        taskName = null
        description = null

        // The idle quick-start prompt must not hold a foreground service. Drop the FGS (if we
        // held one for an active timer) and post the prompt as a normal notification instead.
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_DETACH)
            isForeground = false
        }
        // Cancel first so the prompt is re-posted on the low-importance idle channel rather
        // than inheriting the active channel from a previous tracking notification.
        notificationManager.cancel(NOTIFICATION_ID)
        notificationManager.notify(NOTIFICATION_ID, buildIdleNotification(this))

        // No timer is running, so nothing needs a live service. The notification persists
        // because it was posted via NotificationManager, not tied to the foreground lifecycle.
        // Only stop the service for the command that produced this idle state. A newer Pause or
        // Start command may already be queued; plain stopSelf() would cancel that newer work and
        // leave its previous tracking notification stranded.
        stopSelfResult(startId)
    }

    private fun handlePauseTracking() {
        if (mutationInProgress) return
        val pauseTargetStart = startTime
        val pauseGeneration = stateGeneration
        mutationInProgress = true
        // Reserve the paused surface before the remote mutation. The active-entry observer can
        // see the server transition to idle before this coroutine resumes; without this marker it
        // may hide/idle the service and cancel the successful action before Resume is published.
        persistPausedStart()
        serviceScope.launch {
            stopActiveEntry(pauseTargetStart).fold(
                onSuccess = {
                    mutationInProgress = false
                    notificationManager.cancel(NOTIFICATION_ID_ERROR)
                    // A different timer may have appeared externally while the old stop request
                    // was in flight. Its refresh owns the notification now; do not turn that new
                    // running timer into a false paused surface when the old request completes.
                    if (stateGeneration == pauseGeneration) confirmPausedState()
                },
                onFailure = { error ->
                    mutationInProgress = false
                    Timber.e(error, "Failed to stop time entry during pause")
                    if (stateGeneration == pauseGeneration) {
                        clearPersistedPausedStart()
                        isPaused = false
                        isTracking = true
                        publishNotification()
                        showMutationError(R.string.notification_pause_failed)
                    }
                },
            )
        }
    }

    private fun showPausedNotification() {
        cancelLongTimerWarning()
        // Calculate elapsed time before pausing
        val now = Instant.now()
        pausedAt = now
        elapsedBeforePauseSeconds = if (startTime != null) {
            now.epochSecond - startTime!!.epochSecond
        } else {
            0
        }

        // Immediately show paused notification
        isPaused = true
        isTracking = false
        persistPausedStart()

        publishNotification()
    }

    private fun handleResumeTracking(intent: Intent) {
        if (mutationInProgress) return

        val requestedProjectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: projectName
        val requestedTaskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: taskName
        val requestedDescription = intent.getStringExtra(EXTRA_DESCRIPTION) ?: description
        val requestedOrganizationId = intent.getStringExtra(EXTRA_ORGANIZATION_ID) ?: organizationId
        val requestedProjectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: projectId
        val requestedTaskId = intent.getStringExtra(EXTRA_TASK_ID) ?: taskId
        val resumeGeneration = stateGeneration

        mutationInProgress = true
        serviceScope.launch {
            resumeActiveEntry(
                requestedOrganizationId,
                requestedProjectId,
                requestedTaskId,
                requestedProjectName,
                requestedTaskName,
                requestedDescription,
            ).fold(
                onSuccess = { resumedAt ->
                    mutationInProgress = false
                    notificationManager.cancel(NOTIFICATION_ID_ERROR)
                    if (stateGeneration == resumeGeneration) {
                        organizationId = requestedOrganizationId
                        projectId = requestedProjectId
                        taskId = requestedTaskId
                        projectName = requestedProjectName
                        taskName = requestedTaskName
                        description = requestedDescription
                        startTime = resumedAt
                        isPaused = false
                        isTracking = true
                        clearPersistedPausedStart()
                        publishNotification()
                        scheduleLongTimerWarning()
                    }
                },
                onFailure = { error ->
                    mutationInProgress = false
                    Timber.e(error, "Failed to start time entry during resume")
                    if (stateGeneration == resumeGeneration) {
                        showMutationError(R.string.notification_resume_failed)
                    }
                },
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * The single path for creating or replacing the status notification.
     *
     * A foreground service is held only while a timer is actually running. Paused/idle states
     * show a normal notification and drop the foreground service so we never keep an FGS alive
     * just to display a prompt (Google Play foreground-service policy).
     */
    private fun publishNotification() {
        val notification = buildNotification()
        if (isTracking) {
            startForegroundCompat(notification)
        } else {
            if (isForeground) {
                // Keep the notification posted but detach it from the (now stopping) FGS.
                stopForeground(STOP_FOREGROUND_DETACH)
                isForeground = false
            } else {
                // The service may have been launched fresh via startForegroundService (e.g.
                // pausing after process death). That call obligates a startForeground() within
                // ~5s, so satisfy the contract and then immediately detach: paused/idle states
                // must not keep a foreground service alive.
                startForegroundCompat(notification)
                stopForeground(STOP_FOREGROUND_DETACH)
                isForeground = false
            }
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    /** Promote to a foreground service with the dataSync type (matches the manifest). */
    private fun startForegroundCompat(notification: Notification) {
        // minSdk is 29, so the typed startForeground overload is always available.
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        isForeground = true
    }

    private fun refreshNotificationIfVisible() {
        // The notification can remain posted after Android detaches the service from the
        // foreground-service slot (for example after the dataSync timeout), so setting changes
        // must refresh any active timer notification, not only an attached FGS notification.
        if (isTracking) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun persistPausedStart() {
        startTime?.let { statePreferences.edit().putLong(PREF_PAUSED_START_EPOCH_MS, it.toEpochMilli()).apply() }
    }

    private fun isPersistedPausedStart(requestedStart: Instant): Boolean =
        statePreferences.getLong(PREF_PAUSED_START_EPOCH_MS, Long.MIN_VALUE) == requestedStart.toEpochMilli()

    private fun hasPersistedPausedStart(): Boolean = statePreferences.contains(PREF_PAUSED_START_EPOCH_MS)

    private fun clearPersistedPausedStart() {
        statePreferences.edit().remove(PREF_PAUSED_START_EPOCH_MS).apply()
    }

    private fun createNotificationChannel() = ensureChannels(this)

    private fun buildNotification(): Notification = when {
        isTracking -> buildTrackingNotification()
        isPaused -> buildPausedNotification()
        else -> buildIdleNotification(this)
    }

    private fun buildTrackingNotification(): Notification {
        // Build content text with project/task info
        val contentText = buildString {
            if (!description.isNullOrBlank()) {
                append(description)
            } else {
                append(getString(R.string.notification_tracking_default))
            }

            if (!projectName.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append(projectName)
                if (!taskName.isNullOrBlank()) {
                    append(" / ")
                    append(taskName)
                }
            }
        }

        // Intent to open the app when notification is tapped
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val pausePendingIntent = buildServiceActionPendingIntent(
            action = ACTION_PAUSE_TRACKING,
            requestCode = 2,
        )
        val stopPendingIntent = buildServiceActionPendingIntent(
            action = ACTION_STOP_TRACKING,
            requestCode = 1,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_ACTIVE)
            .setContentTitle(
                if (longTimerWarningVisible) {
                    getString(
                        R.string.long_timer_notification_title,
                    )
                } else {
                    getString(R.string.time_tracking_notification_title)
                },
            )
            .setContentText(if (longTimerWarningVisible) getString(R.string.long_timer_notification_text) else contentText)
            .setSmallIcon(R.drawable.ic_timer)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .setWhen(startTime?.toEpochMilli() ?: System.currentTimeMillis())
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (longTimerWarningVisible) {
            val keepPendingIntent = buildServiceActionPendingIntent(
                action = ACTION_KEEP_RUNNING,
                requestCode = KEEP_RUNNING_REQUEST_CODE,
            )
            val adjustPendingIntent = PendingIntent.getActivity(
                this,
                ADJUST_END_TIME_REQUEST_CODE,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_EDIT_ACTIVE_ENTRY, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.drawable.ic_stop, getString(R.string.stop_now), stopPendingIntent)
                .addAction(R.drawable.ic_timer, getString(R.string.keep_running), keepPendingIntent)
                .addAction(R.drawable.ic_edit, getString(R.string.adjust_end_time), adjustPendingIntent)
        } else {
            builder.addAction(R.drawable.ic_timer, getString(R.string.pause), pausePendingIntent)
                .addAction(R.drawable.ic_timer, getString(R.string.stop_tracking), stopPendingIntent)
        }
        if (liveUpdateEnabled) {
            builder.setShortCriticalText(getString(R.string.live_timer_status_working))
        }
        builder.setLiveUpdateRequested(liveUpdateEnabled)
        builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(buildRedactedPublicNotification(R.string.notification_public_tracking_title))
        return builder.build()
    }

    /**
     * A lock-screen-safe stand-in for a work-bearing notification: no description, project, task,
     * tags, organization, or duration. Used as [NotificationCompat.Builder.setPublicVersion] so the
     * rich content stays private while the lock screen still shows that a timer is active.
     */
    private fun buildRedactedPublicNotification(titleRes: Int): Notification = NotificationCompat.Builder(this, CHANNEL_ID_ACTIVE)
        .setContentTitle(getString(titleRes))
        .setSmallIcon(R.drawable.ic_timer)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()

    /** Privacy-safe placeholder shown only while a worker-triggered warning validates the timer. */
    private fun buildRestoringTrackingNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID_ACTIVE)
        .setContentTitle(getString(R.string.time_tracking_notification_title))
        .setContentText(getString(R.string.notification_tracking_default))
        .setSmallIcon(R.drawable.ic_timer)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(buildRedactedPublicNotification(R.string.notification_public_tracking_title))
        .build()

    /**
     * Persists the next warning deadline and schedules it with WorkManager so the warning survives
     * process death (a plain in-process `delay()` does not: this service can be killed and returns
     * START_NOT_STICKY, so nothing would ever fire the warning again). [LongTimerWarningWorker]
     * reads the persisted deadline back and posts the warning if it's still applicable.
     */
    private fun scheduleLongTimerWarning(snoozeSeconds: Long? = null) {
        longWarningJob?.cancel()
        val entryStart = startTime ?: return
        longWarningJob = serviceScope.launch {
            val waitSeconds = snoozeSeconds ?: run {
                val threshold = settingsDataStore.longTimerHours.first() * SECONDS_PER_HOUR
                val elapsed = Instant.now().epochSecond - entryStart.epochSecond
                (threshold - elapsed).coerceAtLeast(0L)
            }
            val deadlineEpochMs = System.currentTimeMillis() + waitSeconds * MILLIS_PER_SECOND
            settingsDataStore.setLongTimerWarningDeadline(
                deadlineEpochMs = deadlineEpochMs,
                entryStartEpochMs = entryStart.toEpochMilli(),
            )
            LongTimerWarningWorker.schedule(
                context = this@TimeTrackingNotificationService,
                delaySeconds = waitSeconds,
                entryStartEpochMs = entryStart.toEpochMilli(),
            )
        }
    }

    /** Cancel any pending warning: the timer stopped, paused, or is no longer eligible. */
    private fun cancelLongTimerWarning() {
        longWarningJob?.cancel()
        LongTimerWarningWorker.cancel(this)
        serviceScope.launch {
            settingsDataStore.clearLongTimerWarningDeadline()
        }
    }

    private fun buildPausedNotification(): Notification {
        // Format elapsed time before pause
        val trackedTime = formatDuration(elapsedBeforePauseSeconds)

        // Build content text with project/task info and tracked duration
        val contentText = buildString {
            append(getString(R.string.notification_paused_tracked, trackedTime))
            if (!projectName.isNullOrBlank()) {
                append(" • ")
                append(projectName)
                if (!taskName.isNullOrBlank()) {
                    append(" / ")
                    append(taskName)
                }
            }
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val resumePendingIntent = buildServiceActionPendingIntent(
            action = ACTION_RESUME_TRACKING,
            requestCode = RESUME_REQUEST_CODE,
        )
        val stopPendingIntent = buildServiceActionPendingIntent(
            action = ACTION_STOP_TRACKING,
            requestCode = 1,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_ACTIVE)
            .setContentTitle(getString(R.string.notification_paused_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_timer)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            // Use chronometer to show time since paused
            .setWhen(pausedAt?.toEpochMilli() ?: System.currentTimeMillis())
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .setSubText(getString(R.string.notification_paused_since))
            .addAction(
                R.drawable.ic_timer,
                getString(R.string.resume),
                resumePendingIntent,
            )
            .addAction(
                R.drawable.ic_timer,
                getString(R.string.stop),
                stopPendingIntent,
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(buildRedactedPublicNotification(R.string.notification_public_paused_title))
            .build()
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun buildServiceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TimeTrackingNotificationService::class.java).apply {
            this.action = action
            putExtra(EXTRA_PAUSED, isPaused)
            startTime?.let { putExtra(EXTRA_START_TIME, it.toEpochMilli()) }
            organizationId?.let { putExtra(EXTRA_ORGANIZATION_ID, it) }
            projectId?.let { putExtra(EXTRA_PROJECT_ID, it) }
            taskId?.let { putExtra(EXTRA_TASK_ID, it) }
            projectName?.let { putExtra(EXTRA_PROJECT_NAME, it) }
            taskName?.let { putExtra(EXTRA_TASK_NAME, it) }
            description?.let { putExtra(EXTRA_DESCRIPTION, it) }
        }
        return PendingIntent.getForegroundService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopService() {
        cancelLongTimerWarning()
        isForeground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Android 15+ (API 35) enforces a cumulative daily runtime limit on dataSync foreground
     * services (~6h/24h). A full-workday timer can hit it; when it does the system calls this and
     * requires the FGS to stop promptly. Degrade gracefully: detach so the elapsed-timer
     * notification stays posted (the entry is server-authoritative and keeps running) while we
     * drop the foreground status, rather than being force-stopped/crashed.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Timber.w("dataSync foreground service timed out; detaching to a plain notification")
        if (isForeground) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
            stopForeground(STOP_FOREGROUND_DETACH)
            isForeground = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isForeground = false
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID_ACTIVE = "time_tracking_active"
        private const val CHANNEL_ID_IDLE = "time_tracking_idle"
        private const val CHANNEL_ID_ERROR = "time_tracking_error"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ID_ERROR = 1002
        private const val STATE_PREFERENCES = "time_tracking_notification_state"
        private const val PREF_PAUSED_START_EPOCH_MS = "paused_start_epoch_ms"
        private const val ERROR_ACTION_REQUEST_CODE = 4
        private const val KEEP_RUNNING_REQUEST_CODE = 5
        private const val ADJUST_END_TIME_REQUEST_CODE = 6
        private const val RESUME_REQUEST_CODE = 3
        private const val RESUME_PROMPT_REQUEST_CODE = 7
        private const val SECONDS_PER_HOUR = 3600L
        private const val SECONDS_PER_MINUTE = 60L
        private const val MILLIS_PER_SECOND = 1000L

        const val ACTION_START_TRACKING =
            "dev.tricked.solidverdant.ACTION_START_TRACKING_NOTIFICATION"
        const val ACTION_SHOW_IDLE = "dev.tricked.solidverdant.ACTION_SHOW_IDLE_NOTIFICATION"
        const val ACTION_STOP_TRACKING =
            "dev.tricked.solidverdant.ACTION_STOP_TRACKING_NOTIFICATION"
        const val ACTION_PAUSE_TRACKING =
            "dev.tricked.solidverdant.ACTION_PAUSE_TRACKING_NOTIFICATION"
        const val ACTION_SHOW_PAUSED =
            "dev.tricked.solidverdant.ACTION_SHOW_PAUSED_NOTIFICATION"
        const val ACTION_RESUME_TRACKING =
            "dev.tricked.solidverdant.ACTION_RESUME_TRACKING_NOTIFICATION"
        const val ACTION_QUICK_START = "dev.tricked.solidverdant.ACTION_QUICK_START"
        const val ACTION_KEEP_RUNNING = "dev.tricked.solidverdant.ACTION_KEEP_RUNNING"
        const val ACTION_REFRESH_LONG_TIMER = "dev.tricked.solidverdant.ACTION_REFRESH_LONG_TIMER"

        /** Durable trigger posted by [LongTimerWarningWorker]; see [handleShowLongTimerWarning]. */
        const val ACTION_SHOW_LONG_TIMER_WARNING =
            "dev.tricked.solidverdant.ACTION_SHOW_LONG_TIMER_WARNING"

        const val EXTRA_START_TIME = "start_time"
        const val EXTRA_PROJECT_NAME = "project_name"
        const val EXTRA_TASK_NAME = "task_name"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_ORGANIZATION_ID = "organization_id"
        private const val EXTRA_PAUSED = "paused"

        /** Epoch millis of the entry's start time the warning was scheduled for; see [ACTION_SHOW_LONG_TIMER_WARNING]. */
        const val EXTRA_ENTRY_START_EPOCH_MS = "entry_start_epoch_ms"

        private val FOREGROUND_ACTIONS = setOf(
            ACTION_STOP_TRACKING,
            ACTION_PAUSE_TRACKING,
            ACTION_RESUME_TRACKING,
            ACTION_KEEP_RUNNING,
            ACTION_SHOW_LONG_TIMER_WARNING,
        )
        private val ENTRY_MUTATION_ACTIONS = setOf(
            ACTION_STOP_TRACKING,
            ACTION_PAUSE_TRACKING,
            ACTION_RESUME_TRACKING,
            ACTION_KEEP_RUNNING,
        )

        /**
         * Show the idle "quick start" prompt.
         *
         * This is a normal (non-foreground) notification: it never starts or keeps a foreground
         * service alive. If a tracking service is currently running we deliver ACTION_SHOW_IDLE
         * so it demotes itself; otherwise we post the prompt directly. We deliberately use
         * startService (not startForegroundService) so the idle prompt can never become an FGS.
         */
        fun showIdle(context: Context) {
            val intent = Intent(context, TimeTrackingNotificationService::class.java).apply {
                action = ACTION_SHOW_IDLE
            }
            try {
                context.startService(intent)
            } catch (e: IllegalStateException) {
                // No running service and background service starts are disallowed. Post directly.
                Timber.d(e, "Posting idle notification without a service")
                ensureChannels(context)
                try {
                    NotificationManagerCompat.from(context)
                        .notify(NOTIFICATION_ID, buildIdleNotification(context))
                } catch (se: SecurityException) {
                    Timber.w(se, "Idle notification suppressed: notification permission missing")
                }
            }
        }

        /**
         * Fallback used when a running timer cannot be restored into a foreground service (e.g.
         * a ForegroundServiceStartNotAllowedException after boot on some OEMs). Posts a plain
         * notification prompting the user to reopen the app to restore the timer display.
         */
        fun showResumePrompt(context: Context) {
            ensureChannels(context)
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                RESUME_PROMPT_REQUEST_CODE,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // Reuses existing strings so no new resource is required: title "Time Tracking",
            // body "Tracking time" — accurate (a timer is still running) and tapping opens the app.
            // A timer is running, so this states work state; keep it private on the lock screen
            // with a redacted public version, same as the other tracking notifications.
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_IDLE)
                .setContentTitle(context.getString(R.string.time_tracking_notification_title))
                .setContentText(context.getString(R.string.notification_tracking_default))
                .setSmallIcon(R.drawable.ic_timer)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(context, CHANNEL_ID_IDLE)
                        .setContentTitle(context.getString(R.string.notification_public_tracking_title))
                        .setSmallIcon(R.drawable.ic_timer)
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .build(),
                )
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (se: SecurityException) {
                Timber.w(se, "Resume prompt suppressed: notification permission missing")
            }
        }

        /** Create the notification channels. Safe to call repeatedly. */
        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val activeChannel = NotificationChannel(
                CHANNEL_ID_ACTIVE,
                context.getString(R.string.notification_channel_active_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_active_description)
                setShowBadge(false)
                // Work-bearing notifications posted on this channel (description, project, task)
                // must not leak to the lock screen; setPublicVersion(...) on each notification
                // supplies the redacted lock-screen content.
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(null, null) // Silent
            }

            val idleChannel = NotificationChannel(
                CHANNEL_ID_IDLE,
                context.getString(R.string.notification_channel_idle_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_idle_description)
                setShowBadge(false)
                // The resume-prompt notification posted here states a timer is running; keep the
                // channel default private so its setPublicVersion(...) redaction is respected.
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(null, null) // Silent
            }

            val errorChannel = NotificationChannel(
                CHANNEL_ID_ERROR,
                context.getString(R.string.notification_channel_error_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_error_description)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannels(listOf(activeChannel, idleChannel, errorChannel))
        }

        /** Build the idle quick-start prompt notification (usable without a service instance). */
        private fun buildIdleNotification(context: Context): Notification {
            val projectSelectionIntent = Intent(context, ProjectSelectionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val projectSelectionPendingIntent = PendingIntent.getActivity(
                context,
                0,
                projectSelectionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            return NotificationCompat.Builder(context, CHANNEL_ID_IDLE)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notification_quick_start_ready))
                .setSmallIcon(R.drawable.ic_timer)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(projectSelectionPendingIntent)
                .addAction(
                    R.drawable.ic_timer,
                    context.getString(R.string.quick_start),
                    projectSelectionPendingIntent,
                )
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        }

        fun quickStart(
            context: Context,
            projectId: String?,
            taskId: String?,
            description: String,
            projectName: String?,
            taskName: String?,
        ) {
            val intent = Intent(context, TimeTrackingNotificationService::class.java).apply {
                action = ACTION_QUICK_START
                putExtra(EXTRA_PROJECT_ID, projectId)
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_DESCRIPTION, description)
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_TASK_NAME, taskName)
            }
            context.startForegroundService(intent)
        }

        /**
         * Start the notification service for time tracking
         */
        fun startTracking(
            context: Context,
            startTime: Instant,
            projectName: String? = null,
            taskName: String? = null,
            description: String? = null,
            projectId: String? = null,
            taskId: String? = null,
            organizationId: String? = null,
        ) {
            val intent = Intent(context, TimeTrackingNotificationService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_START_TIME, startTime.toEpochMilli())
                putExtra(EXTRA_ORGANIZATION_ID, organizationId)
                putExtra(EXTRA_PROJECT_ID, projectId)
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_TASK_NAME, taskName)
                putExtra(EXTRA_DESCRIPTION, description)
            }
            context.startForegroundService(intent)
        }

        /**
         * Pause tracking - stops the time entry but keeps notification in paused state
         */
        fun showPaused(context: Context) {
            val intent = Intent(context, TimeTrackingNotificationService::class.java).apply {
                action = ACTION_SHOW_PAUSED
            }
            context.startForegroundService(intent)
        }

        /**
         * Hide the notification without changing any server-side timer state.
         */
        fun hide(context: Context) {
            // Pausing intentionally leaves a Resume/Stop prompt after the server entry becomes
            // inactive. The regular active-entry refresh observes that same server transition and
            // calls hide(); preserve the paused surface until the user resumes, stops, or starts a
            // genuinely new timer.
            val pausedPromptVisible = context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
                .contains(PREF_PAUSED_START_EPOCH_MS)
            if (pausedPromptVisible) {
                // The service that executed Pause may have been recreated or stopped while the
                // server transition was in flight. Reassert Resume instead of merely preserving
                // whichever (possibly stale tracking) notification Android currently holds.
                showPaused(context)
                return
            }
            context.stopService(Intent(context, TimeTrackingNotificationService::class.java))
        }

        /** Remove every account-owned timer surface before logout switches or clears the account. */
        fun clearForLogout(context: Context) {
            context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            runCatching { LongTimerWarningWorker.cancel(context) }
                .onFailure { Timber.w(it, "Could not cancel long timer warning during logout") }
            context.getSystemService(NotificationManager::class.java)?.let { manager ->
                manager.cancel(NOTIFICATION_ID)
                manager.cancel(NOTIFICATION_ID_ERROR)
            }
            context.stopService(Intent(context, TimeTrackingNotificationService::class.java))
        }

        fun refreshLongTimerWarning(context: Context) {
            context.startService(
                Intent(context, TimeTrackingNotificationService::class.java).apply {
                    action = ACTION_REFRESH_LONG_TIMER
                },
            )
        }

        fun snoozeLongTimerWarning(context: Context) {
            context.startService(
                Intent(context, TimeTrackingNotificationService::class.java).apply {
                    action = ACTION_KEEP_RUNNING
                },
            )
        }

        /**
         * Durable trigger invoked by [LongTimerWarningWorker]. May run with the service process
         * already dead, so this uses `startForegroundService`: [handleShowLongTimerWarning] will
         * re-promote to a foreground service via [publishNotification] once it confirms the entry
         * is still running.
         */
        fun showLongTimerWarning(context: Context, entryStartEpochMs: Long) {
            val intent = Intent(context, TimeTrackingNotificationService::class.java).apply {
                action = ACTION_SHOW_LONG_TIMER_WARNING
                putExtra(EXTRA_ENTRY_START_EPOCH_MS, entryStartEpochMs)
            }
            try {
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                Timber.w(e, "Could not deliver long timer warning: background start not allowed")
            }
        }
    }
}

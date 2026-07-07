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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
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
    private var projectName: String? = null
    private var taskName: String? = null
    private var description: String? = null
    private var isTracking: Boolean = false
    private var isPaused: Boolean = false
    private var isForeground: Boolean = false
    private var pausedAt: Instant? = null
    private var elapsedBeforePauseSeconds: Long = 0
    private var mutationInProgress: Boolean = false
    private var longWarningJob: Job? = null
    private var longTimerWarningVisible = false

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("NotificationService onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                isTracking = true
                startTime = Instant.ofEpochMilli(
                    intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis()),
                )
                projectName = intent.getStringExtra(EXTRA_PROJECT_NAME)
                taskName = intent.getStringExtra(EXTRA_TASK_NAME)
                description = intent.getStringExtra(EXTRA_DESCRIPTION)

                publishNotification()
                scheduleLongTimerWarning()
            }

            ACTION_SHOW_IDLE -> {
                showIdleNotification()
            }

            ACTION_STOP_TRACKING -> {
                handleStopTracking()
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

            ACTION_QUICK_START -> handleQuickStart(intent)
        }

        // State is restored explicitly from the server by the app and BootReceiver. A sticky
        // restart has no Intent extras to rebuild the timer and can replay stale notification state.
        return START_NOT_STICKY
    }

    private fun handleQuickStart(intent: Intent) {
        isTracking = true
        isPaused = false
        startTime = Instant.now()
        projectName = intent.getStringExtra(EXTRA_PROJECT_NAME)
        taskName = intent.getStringExtra(EXTRA_TASK_NAME)
        description = intent.getStringExtra(EXTRA_DESCRIPTION)
        publishNotification()
        scheduleLongTimerWarning()

        serviceScope.launch {
            val membership = authRepository.getCurrentMembership()
            val user = authRepository.getCurrentUser().getOrNull()
            if (membership == null || user == null) {
                Timber.e("Quick start failed: missing membership or user")
                stopService()
                return@launch
            }

            authRepository.startTimeEntry(
                organizationId = membership.organizationId,
                memberId = membership.id,
                userId = user.id,
                projectId = intent.getStringExtra(EXTRA_PROJECT_ID),
                taskId = intent.getStringExtra(EXTRA_TASK_ID),
                description = description.orEmpty(),
            ).onSuccess { entry ->
                startTime = Instant.parse(entry.start)
                refreshNotificationIfVisible()
            }.onFailure { error ->
                Timber.e(error, "Quick start failed")
                stopService()
            }
        }
    }

    private fun handleStopTracking() {
        if (mutationInProgress) return
        val wasPaused = isPaused
        mutationInProgress = true

        serviceScope.launch {
            val stopped = if (wasPaused) {
                Result.success(false)
            } else {
                stopActiveEntry()
            }

            stopped.fold(
                onSuccess = {
                    mutationInProgress = false
                    notificationManager.cancel(NOTIFICATION_ID_ERROR)
                    if (settingsDataStore.alwaysShowNotification.first()) {
                        showIdleNotification()
                    } else {
                        stopService()
                    }
                },
                onFailure = { error ->
                    mutationInProgress = false
                    Timber.e(error, "Failed to stop time entry from notification")
                    showMutationError(R.string.notification_stop_failed)
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
            4,
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

    private fun confirmPausedState() {
        longWarningJob?.cancel()
        longTimerWarningVisible = false
        val now = Instant.now()
        pausedAt = now
        elapsedBeforePauseSeconds = startTime?.let { now.epochSecond - it.epochSecond } ?: 0
        isPaused = true
        isTracking = false
        publishNotification()
    }

    private suspend fun resumeActiveEntry(
        requestedProjectName: String?,
        requestedTaskName: String?,
        requestedDescription: String?,
    ): Result<Instant> = runCatching {
        val user = authRepository.getCurrentUser().getOrThrow()
        val membership = authRepository.getCurrentMembership()
            ?: error("No current membership")
        val projects = authRepository.getProjects(membership.organizationId).getOrThrow()
        val tasks = authRepository.getTasks(membership.organizationId).getOrThrow()
        val projectId = projects.find { it.name == requestedProjectName }?.id
        val taskId = tasks.find { it.name == requestedTaskName }?.id

        val entry = authRepository.startTimeEntry(
            organizationId = membership.organizationId,
            memberId = membership.id,
            userId = user.id,
            projectId = projectId,
            taskId = taskId,
            description = requestedDescription.orEmpty(),
        ).getOrThrow()
        Instant.parse(entry.start)
    }

    /** Stop the account-wide active entry. Used only by explicit notification actions. */
    private suspend fun stopActiveEntry(): Result<Boolean> {
        val activeEntry = authRepository.getActiveTimeEntry()
            .getOrElse { return Result.failure(it) }
            ?: return Result.success(false)
        val user = authRepository.getCurrentUser()
            .getOrElse { return Result.failure(it) }

        return authRepository.stopTimeEntry(
            organizationId = activeEntry.organizationId,
            timeEntryId = activeEntry.id,
            userId = user.id,
            startTime = activeEntry.start,
        ).map { true }
    }

    private fun showIdleNotification() {
        longWarningJob?.cancel()
        longTimerWarningVisible = false
        isTracking = false
        isPaused = false
        startTime = null
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
        stopSelf()
    }

    private fun handlePauseTracking() {
        if (mutationInProgress) return
        mutationInProgress = true
        serviceScope.launch {
            stopActiveEntry().fold(
                onSuccess = {
                    mutationInProgress = false
                    notificationManager.cancel(NOTIFICATION_ID_ERROR)
                    confirmPausedState()
                },
                onFailure = { error ->
                    mutationInProgress = false
                    Timber.e(error, "Failed to stop time entry during pause")
                    showMutationError(R.string.notification_pause_failed)
                },
            )
        }
    }

    private fun showPausedNotification() {
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

        publishNotification()
    }

    private fun handleResumeTracking(intent: Intent) {
        if (mutationInProgress) return

        val requestedProjectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: projectName
        val requestedTaskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: taskName
        val requestedDescription = intent.getStringExtra(EXTRA_DESCRIPTION) ?: description

        mutationInProgress = true
        serviceScope.launch {
            resumeActiveEntry(
                requestedProjectName,
                requestedTaskName,
                requestedDescription,
            ).fold(
                onSuccess = { resumedAt ->
                    mutationInProgress = false
                    notificationManager.cancel(NOTIFICATION_ID_ERROR)
                    projectName = requestedProjectName
                    taskName = requestedTaskName
                    description = requestedDescription
                    startTime = resumedAt
                    isPaused = false
                    isTracking = true
                    publishNotification()
                    scheduleLongTimerWarning()
                },
                onFailure = { error ->
                    mutationInProgress = false
                    Timber.e(error, "Failed to start time entry during resume")
                    showMutationError(R.string.notification_resume_failed)
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
        if (isForeground) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
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

        val pauseIntent = Intent(this, TimeTrackingNotificationService::class.java).apply {
            action = ACTION_PAUSE_TRACKING
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            2,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, TimeTrackingNotificationService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (longTimerWarningVisible) {
            val keepPendingIntent = PendingIntent.getService(
                this,
                5,
                Intent(this, TimeTrackingNotificationService::class.java).apply { action = ACTION_KEEP_RUNNING },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val adjustPendingIntent = PendingIntent.getActivity(
                this,
                6,
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
        return builder.build()
    }

    private fun scheduleLongTimerWarning(snoozeSeconds: Long? = null) {
        longWarningJob?.cancel()
        longWarningJob = serviceScope.launch {
            val waitSeconds = snoozeSeconds ?: run {
                val threshold = settingsDataStore.longTimerHours.first() * 3600L
                val elapsed = startTime?.let { Instant.now().epochSecond - it.epochSecond } ?: 0L
                (threshold - elapsed).coerceAtLeast(0L)
            }
            delay(waitSeconds * 1000L)
            if (isTracking) {
                longTimerWarningVisible = true
                publishNotification()
            }
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

        val resumeIntent = Intent(this, TimeTrackingNotificationService::class.java).apply {
            action = ACTION_RESUME_TRACKING
        }
        val resumePendingIntent = PendingIntent.getService(
            this,
            3,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, TimeTrackingNotificationService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun stopService() {
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

        const val EXTRA_START_TIME = "start_time"
        const val EXTRA_PROJECT_NAME = "project_name"
        const val EXTRA_TASK_NAME = "task_name"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_TASK_ID = "task_id"

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
                7,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // Reuses existing strings so no new resource is required: title "Time Tracking",
            // body "Tracking time" — accurate (a timer is still running) and tapping opens the app.
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_IDLE)
                .setContentTitle(context.getString(R.string.time_tracking_notification_title))
                .setContentText(context.getString(R.string.notification_tracking_default))
                .setSmallIcon(R.drawable.ic_timer)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // Silent
            }

            val idleChannel = NotificationChannel(
                CHANNEL_ID_IDLE,
                context.getString(R.string.notification_channel_idle_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_idle_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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
        ) {
            val intent = Intent(context, TimeTrackingNotificationService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_START_TIME, startTime.toEpochMilli())
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
    }
}

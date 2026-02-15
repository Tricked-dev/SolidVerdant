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
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.tricked.solidverdant.MainActivity
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.ui.tile.ProjectSelectionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
                    intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis())
                )
                projectName = intent.getStringExtra(EXTRA_PROJECT_NAME)
                taskName = intent.getStringExtra(EXTRA_TASK_NAME)
                description = intent.getStringExtra(EXTRA_DESCRIPTION)

                if (!isForeground) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    isForeground = true
                } else {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification())
                }
            }

            ACTION_SHOW_IDLE -> {
                isTracking = false
                startTime = null
                projectName = null
                taskName = null
                description = null

                if (!isForeground) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    isForeground = true
                } else {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification())
                }
            }

            ACTION_STOP_TRACKING -> {
                handleStopTracking()
            }

            ACTION_PAUSE_TRACKING -> {
                handlePauseTracking()
            }

            ACTION_RESUME_TRACKING -> {
                handleResumeTracking(intent)
            }

            ACTION_UPDATE_INFO -> {
                projectName = intent.getStringExtra(EXTRA_PROJECT_NAME)
                taskName = intent.getStringExtra(EXTRA_TASK_NAME)
                description = intent.getStringExtra(EXTRA_DESCRIPTION)

                // Update notification with new info
                if (isForeground) {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification())
                }
            }
        }

        return START_STICKY
    }

    private fun handleStopTracking() {
        val wasPaused = isPaused
        isPaused = false
        isTracking = false

        serviceScope.launch {
            try {
                // If paused, entry is already stopped - just clean up
                if (wasPaused) {
                    val alwaysShow = settingsDataStore.alwaysShowNotification.first()
                    if (alwaysShow) {
                        showIdle(this@TimeTrackingNotificationService)
                    }
                    stopService()
                    return@launch
                }

                // Get active time entry
                authRepository.getActiveTimeEntry()
                    .onSuccess { activeEntry ->
                        if (activeEntry != null) {
                            // Get user info for userId
                            authRepository.getCurrentUser()
                                .onSuccess { user ->
                                    // Stop the active time entry
                                    authRepository.stopTimeEntry(
                                        organizationId = activeEntry.organizationId,
                                        timeEntryId = activeEntry.id,
                                        userId = user.id,
                                        startTime = activeEntry.start
                                    ).onSuccess {
                                        // Update notification state based on settings
                                        val alwaysShow = settingsDataStore.alwaysShowNotification.first()
                                        if (alwaysShow) {
                                            // Show idle notification
                                            showIdle(this@TimeTrackingNotificationService)
                                        }
                                        // If not alwaysShow, stopService() in finally will clean up
                                    }.onFailure { error ->
                                        Timber.e(
                                            error,
                                            "Failed to stop time entry from notification"
                                        )
                                    }
                                }.onFailure { error ->
                                    Timber.e(error, "Failed to get user info")
                                }
                        } else {
                            Timber.w("No active time entry to stop")
                            // Check if we should show idle notification
                            val alwaysShow = settingsDataStore.alwaysShowNotification.first()
                            if (alwaysShow) {
                                showIdle(this@TimeTrackingNotificationService)
                            }
                            // If not alwaysShow, stopService() in finally will clean up
                        }
                    }.onFailure { error ->
                        Timber.e(error, "Failed to get active time entry")
                    }
            } finally {
                stopService()
            }
        }
    }

    private fun handlePauseTracking() {
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

        if (!isForeground) {
            startForeground(NOTIFICATION_ID, buildNotification())
            isForeground = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }

        // Stop the active time entry via API in the background
        serviceScope.launch {
            try {
                authRepository.getActiveTimeEntry()
                    .onSuccess { activeEntry ->
                        if (activeEntry != null) {
                            authRepository.getCurrentUser()
                                .onSuccess { user ->
                                    authRepository.stopTimeEntry(
                                        organizationId = activeEntry.organizationId,
                                        timeEntryId = activeEntry.id,
                                        userId = user.id,
                                        startTime = activeEntry.start
                                    ).onFailure { error ->
                                        Timber.e(error, "Failed to stop time entry during pause")
                                    }
                                }.onFailure { error ->
                                    Timber.e(error, "Failed to get user info during pause")
                                }
                        }
                    }.onFailure { error ->
                        Timber.e(error, "Failed to get active time entry during pause")
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during pause")
            }
        }
    }

    private fun handleResumeTracking(intent: Intent) {
        // Immediately show tracking notification
        isPaused = false
        isTracking = true
        startTime = Instant.now()

        // Use provided values, fall back to saved values from pause
        if (intent.hasExtra(EXTRA_PROJECT_NAME)) {
            projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: projectName
        }
        if (intent.hasExtra(EXTRA_TASK_NAME)) {
            taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: taskName
        }
        if (intent.hasExtra(EXTRA_DESCRIPTION)) {
            description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: description
        }

        if (!isForeground) {
            startForeground(NOTIFICATION_ID, buildNotification())
            isForeground = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }

        // Start a new time entry via API in the background
        serviceScope.launch {
            try {
                val user = authRepository.getCurrentUser().getOrNull() ?: return@launch
                val memberships = authRepository.getMyMemberships().getOrNull() ?: return@launch
                val membership = memberships.firstOrNull() ?: return@launch

                // Find project/task IDs from names if needed
                val projects = authRepository.getProjects(membership.organizationId).getOrNull()
                val tasks = authRepository.getTasks(membership.organizationId).getOrNull()
                val projectId = projects?.find { it.name == projectName }?.id
                val taskId = tasks?.find { it.name == taskName }?.id

                authRepository.startTimeEntry(
                    organizationId = membership.organizationId,
                    memberId = membership.id,
                    userId = user.id,
                    projectId = projectId,
                    taskId = taskId,
                    description = description ?: ""
                ).onSuccess { entry ->
                    Timber.d("Resumed tracking with new entry: ${entry.id}")
                    startTime = Instant.parse(entry.start)
                    notificationManager.notify(NOTIFICATION_ID, buildNotification())
                }.onFailure { error ->
                    Timber.e(error, "Failed to start time entry during resume")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during resume")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Active tracking channel - higher importance for ongoing timer
            val activeChannel = NotificationChannel(
                CHANNEL_ID_ACTIVE,
                getString(R.string.notification_channel_active_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_active_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // Silent
            }

            // Idle/Quick start channel - low importance
            val idleChannel = NotificationChannel(
                CHANNEL_ID_IDLE,
                getString(R.string.notification_channel_idle_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_idle_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // Silent
            }

            // Error/Sync issues channel - default importance
            val errorChannel = NotificationChannel(
                CHANNEL_ID_ERROR,
                getString(R.string.notification_channel_error_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_error_description)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannels(
                listOf(activeChannel, idleChannel, errorChannel)
            )
        }
    }

    private fun buildNotification(): Notification {
        return when {
            isTracking -> buildTrackingNotification()
            isPaused -> buildPausedNotification()
            else -> buildIdleNotification()
        }
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, TimeTrackingNotificationService::class.java).apply {
            action = ACTION_PAUSE_TRACKING
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TimeTrackingNotificationService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }

        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_ACTIVE)
            .setContentTitle(getString(R.string.time_tracking_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_timer)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .setWhen(startTime?.toEpochMilli() ?: System.currentTimeMillis())
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .addAction(
                R.drawable.ic_timer,
                getString(R.string.pause),
                pausePendingIntent
            )
            .addAction(
                R.drawable.ic_timer,
                getString(R.string.stop_tracking),
                stopPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
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
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = Intent(this, TimeTrackingNotificationService::class.java).apply {
            action = ACTION_RESUME_TRACKING
        }
        val resumePendingIntent = PendingIntent.getService(
            this, 3, resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TimeTrackingNotificationService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
                resumePendingIntent
            )
            .addAction(
                R.drawable.ic_timer,
                getString(R.string.stop),
                stopPendingIntent
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

    private fun buildIdleNotification(): Notification {
        // Intent to open project selection overlay (same as tile)
        val projectSelectionIntent = Intent(this, ProjectSelectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val projectSelectionPendingIntent = PendingIntent.getActivity(
            this,
            0,
            projectSelectionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_IDLE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_quick_start_ready))
            .setSmallIcon(R.drawable.ic_timer)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(projectSelectionPendingIntent)
            .addAction(
                R.drawable.ic_timer,
                getString(R.string.quick_start),
                projectSelectionPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun stopService() {
        isForeground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        const val ACTION_RESUME_TRACKING =
            "dev.tricked.solidverdant.ACTION_RESUME_TRACKING_NOTIFICATION"
        const val ACTION_UPDATE_INFO = "dev.tricked.solidverdant.ACTION_UPDATE_INFO"

        const val EXTRA_START_TIME = "start_time"
        const val EXTRA_PROJECT_NAME = "project_name"
        const val EXTRA_TASK_NAME = "task_name"
        const val EXTRA_DESCRIPTION = "description"

        /**
         * Show idle notification (quick start)
         */
        fun showIdle(context: Context) {
            val intent = Intent(context, TimeTrackingNotificationService::class.java).apply {
                action = ACTION_SHOW_IDLE
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
            description: String? = null
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
        fun pauseTracking(context: Context) {
            val intent = Intent(context, TimeTrackingNotificationService::class.java).apply {
                action = ACTION_PAUSE_TRACKING
            }
            context.startService(intent)
        }

        /**
         * Resume tracking - starts a new session with the same info
         */
        fun resumeTracking(
            context: Context,
            startTime: Instant,
            projectName: String? = null,
            taskName: String? = null,
            description: String? = null
        ) {
            val intent = Intent(context, TimeTrackingNotificationService::class.java).apply {
                action = ACTION_RESUME_TRACKING
                putExtra(EXTRA_START_TIME, startTime.toEpochMilli())
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_TASK_NAME, taskName)
                putExtra(EXTRA_DESCRIPTION, description)
            }
            context.startForegroundService(intent)
        }

        /**
         * Stop the notification service
         */
        fun stopTracking(context: Context) {
            val intent = Intent(context, TimeTrackingNotificationService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }

        /**
         * Update tracking information (project, task, description)
         */
        fun updateTrackingInfo(
            context: Context,
            projectName: String? = null,
            taskName: String? = null,
            description: String? = null
        ) {
            val intent = Intent(context, TimeTrackingNotificationService::class.java).apply {
                action = ACTION_UPDATE_INFO
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_TASK_NAME, taskName)
                putExtra(EXTRA_DESCRIPTION, description)
            }
            context.startService(intent)
        }
    }
}
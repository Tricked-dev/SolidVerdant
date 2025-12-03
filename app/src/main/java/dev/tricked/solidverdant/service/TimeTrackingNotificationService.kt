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
import dev.tricked.solidverdant.MainActivity
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.tile.ProjectSelectionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Foreground service that displays a persistent notification while time tracking is active.
 * This provides better background network access and gives users control over tracking.
 */
class TimeTrackingNotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var updateJob: Job? = null
    private var startTime: Instant? = null
    private var projectName: String? = null
    private var taskName: String? = null
    private var description: String? = null
    private var isTracking: Boolean = false
    private var isForeground: Boolean = false

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        timber.log.Timber.d("NotificationService onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                timber.log.Timber.d("Starting tracking notification")
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
                startUpdatingNotification()
            }

            ACTION_SHOW_IDLE -> {
                isTracking = false
                startTime = null
                projectName = null
                taskName = null
                description = null
                updateJob?.cancel()

                if (!isForeground) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    isForeground = true
                } else {
                    notificationManager.notify(NOTIFICATION_ID, buildNotification())
                }
            }

            ACTION_STOP_TRACKING -> {
                stopService()
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

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_tracking_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_tracking_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (isTracking) {
            buildTrackingNotification()
        } else {
            buildIdleNotification()
        }
    }

    private fun buildTrackingNotification(): Notification {
        val elapsedTime = startTime?.let { start ->
            val duration = Duration.between(start, Instant.now())
            formatDuration(duration)
        } ?: "00:00:00"

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

        // Intent to stop tracking from notification
        val stopIntent = Intent(ACTION_STOP_TRACKING_BROADCAST).apply {
            setPackage(packageName)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(elapsedTime)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_timer)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_timer,
                getString(R.string.stop_tracking),
                stopPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
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

    private fun startUpdatingNotification() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (isActive) {
                delay(1000) // Update every second
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    private fun stopService() {
        updateJob?.cancel()
        isForeground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        isForeground = false
    }

    private fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart()
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    companion object {
        private const val CHANNEL_ID = "time_tracking_unified"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_TRACKING =
            "dev.tricked.solidverdant.ACTION_START_TRACKING_NOTIFICATION"
        const val ACTION_SHOW_IDLE = "dev.tricked.solidverdant.ACTION_SHOW_IDLE_NOTIFICATION"
        const val ACTION_STOP_TRACKING =
            "dev.tricked.solidverdant.ACTION_STOP_TRACKING_NOTIFICATION"
        const val ACTION_UPDATE_INFO = "dev.tricked.solidverdant.ACTION_UPDATE_INFO"
        const val ACTION_STOP_TRACKING_BROADCAST =
            "dev.tricked.solidverdant.ACTION_STOP_TRACKING_FROM_NOTIFICATION"

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
         * Hide the notification service completely
         */
        fun hide(context: Context) {
            val intent = Intent(context, TimeTrackingNotificationService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }

        /**
         * Stop the notification service (alias for hide)
         */
        fun stopTracking(context: Context) {
            hide(context)
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

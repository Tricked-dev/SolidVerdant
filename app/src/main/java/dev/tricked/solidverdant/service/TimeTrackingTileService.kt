package dev.tricked.solidverdant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.remote.ApiClientFactory
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.ui.tile.ProjectSelectionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Quick Settings Tile for time tracking
 * Allows starting/stopping time tracking from the quick settings panel
 */
@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class TimeTrackingTileService : TileService() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var apiClientFactory: ApiClientFactory

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Cache for project and task names to avoid repeated API calls
    private var cachedProjects: List<dev.tricked.solidverdant.data.model.Project>? = null
    private var cachedTasks: List<dev.tricked.solidverdant.data.model.Task>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 60_000L // 1 minute

    // Optimistic state for stopping/starting
    private var optimisticallyStopped = false
    private var optimisticallyStarted = false
    private var optimisticProjectName: String? = null
    private var optimisticTaskName: String? = null

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "time_tracking_errors"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_TIME_TRACKING_STARTED =
            "dev.tricked.solidverdant.TIME_TRACKING_STARTED"
    }

    private val trackingStartedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TIME_TRACKING_STARTED) {
                val projectName = intent.getStringExtra("PROJECT_NAME")
                val taskName = intent.getStringExtra("TASK_NAME")
                Timber.d("Received tracking started broadcast: project=$projectName, task=$taskName")

                // Set optimistic state
                optimisticallyStarted = true
                optimisticProjectName = projectName
                optimisticTaskName = taskName

                // Update tile immediately
                updateTileStateImmediate()

                // Clear optimistic state after delay
                serviceScope.launch {
                    kotlinx.coroutines.delay(3000)
                    optimisticallyStarted = false
                    optimisticProjectName = null
                    optimisticTaskName = null
                    requestListeningState()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Register broadcast receiver
        val filter = IntentFilter(ACTION_TIME_TRACKING_STARTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(trackingStartedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(trackingStartedReceiver, filter)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        Timber.d("Tile starting listening, updating state")
        updateTileState()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Timber.d("Tile added")
        updateTileState()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Timber.d("Tile removed")
    }

    override fun onClick() {
        super.onClick()

        serviceScope.launch {
            try {
                // Check if logged in
                val isLoggedIn = authRepository.isLoggedIn.first()
                if (!isLoggedIn) {
                    Timber.w("Not logged in, cannot track time")
                    showAuthenticationRequired()
                    return@launch
                }

                // Check for active time entry
                val activeEntry = authRepository.getActiveTimeEntry().getOrNull()

                if (activeEntry != null) {
                    // Stop tracking
                    stopTracking(
                        activeEntry.organizationId,
                        activeEntry.userId,
                        activeEntry.id,
                        activeEntry.start
                    )
                } else {
                    // Start tracking - show project selection
                    showProjectSelection()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle tile click")
            }
        }
    }

    private fun updateTileState() {
        serviceScope.launch {
            try {
                val isLoggedIn = authRepository.isLoggedIn.first()
                if (!isLoggedIn) {
                    qsTile?.apply {
                        state = Tile.STATE_INACTIVE
                        label = "Time Tracking"
                        subtitle = "Not logged in"
                        icon = Icon.createWithResource(
                            this@TimeTrackingTileService,
                            R.drawable.ic_timer
                        )
                        updateTile()
                    }
                    return@launch
                }

                val activeEntry = authRepository.getActiveTimeEntry().getOrNull()

                qsTile?.apply {
                    // Check optimistic state first
                    if (optimisticallyStopped) {
                        state = Tile.STATE_INACTIVE
                        label = "Time Tracking"
                        subtitle = "Stopping..."
                        icon = Icon.createWithResource(
                            this@TimeTrackingTileService,
                            R.drawable.ic_timer
                        )
                    } else if (optimisticallyStarted) {
                        state = Tile.STATE_ACTIVE
                        label = optimisticProjectName ?: "Starting..."
                        subtitle = optimisticTaskName ?: "Starting tracking..."
                        icon = Icon.createWithResource(
                            this@TimeTrackingTileService,
                            R.drawable.ic_stop
                        )
                    } else if (activeEntry != null) {
                        // Load cached project and task names
                        val (projectName, taskName) = loadProjectAndTaskNames(activeEntry)

                        // Build display text
                        val label = projectName ?: "Tracking"
                        val subtitle = buildString {
                            if (taskName != null) {
                                append(taskName)
                            } else if (activeEntry.description?.isNotEmpty() == true) {
                                append(activeEntry.description)
                            } else {
                                append("Tap to stop")
                            }
                        }

                        state = Tile.STATE_ACTIVE
                        this.label = label
                        this.subtitle = subtitle
                        icon = Icon.createWithResource(
                            this@TimeTrackingTileService,
                            R.drawable.ic_stop
                        )
                    } else {
                        state = Tile.STATE_INACTIVE
                        label = "Time Tracking"
                        subtitle = "Tap to start"
                        icon = Icon.createWithResource(
                            this@TimeTrackingTileService,
                            R.drawable.ic_play
                        )
                    }
                    updateTile()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update tile state")
            }
        }
    }

    /**
     * Load project and task names with caching
     */
    private suspend fun loadProjectAndTaskNames(
        activeEntry: dev.tricked.solidverdant.data.model.TimeEntry
    ): Pair<String?, String?> {
        return try {
            val memberships = authRepository.getMyMemberships().getOrNull()
            val organizationId = memberships?.firstOrNull()?.organizationId
                ?: return Pair(null, null)

            // Check if cache is valid
            val now = System.currentTimeMillis()
            val cacheExpired = (now - cacheTimestamp) > CACHE_DURATION_MS

            // Refresh cache if needed
            if (cachedProjects == null || cachedTasks == null || cacheExpired) {
                try {
                    val endpoint = authRepository.endpoint.first()
                    val api = apiClientFactory.createApi(endpoint)

                    cachedProjects = api.getProjects(organizationId).data
                    cachedTasks = api.getTasks(organizationId).data
                    cacheTimestamp = now
                    Timber.d("Refreshed project/task cache")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to refresh cache, using old data if available")
                }
            }

            // Look up names from cache
            val projectName = activeEntry.projectId?.let { projectId ->
                cachedProjects?.find { it.id == projectId }?.name
            }
            val taskName = activeEntry.taskId?.let { taskId ->
                cachedTasks?.find { it.id == taskId }?.name
            }

            Pair(projectName, taskName)
        } catch (e: Exception) {
            Timber.w(e, "Failed to load project/task info")
            Pair(null, null)
        }
    }

    private fun showProjectSelection() {
        val intent = Intent(this, ProjectSelectionActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(pendingIntent)
        }

        // Refresh tile state after activity finishes
        // Multiple refreshes to ensure we catch the state change
        serviceScope.launch {
            // First refresh after 1 second (in case it's quick)
            kotlinx.coroutines.delay(1000)
            Timber.d("First refresh after project selection")
            requestListeningState()

            // Second refresh after 3 seconds (in case API is slow)
            kotlinx.coroutines.delay(2000)
            Timber.d("Second refresh after project selection")
            requestListeningState()
        }
    }

    /**
     * Request tile to refresh its state
     */
    private fun requestListeningState() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestListeningState(
                    this,
                    ComponentName(this, TimeTrackingTileService::class.java)
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to request listening state")
            // Fallback to direct update
            updateTileState()
        }
    }

    private fun showAuthenticationRequired() {
        // For now, just update the tile to show not logged in
        updateTileState()
    }

    private fun stopTracking(
        organizationId: String,
        userId: String,
        timeEntryId: String,
        startTime: String
    ) {
        // Optimistically mark as stopped
        optimisticallyStopped = true
        updateTileStateImmediate()

        serviceScope.launch {
            try {
                authRepository.stopTimeEntry(organizationId, timeEntryId, userId, startTime)
                    .onSuccess {
                        Timber.d("Time entry stopped from tile")
                        optimisticallyStopped = false
                        // Force tile refresh
                        kotlinx.coroutines.delay(500)
                        requestListeningState()
                    }
                    .onFailure { error ->
                        Timber.e(error, "Failed to stop time entry from tile")
                        // Revert optimistic state and show error
                        optimisticallyStopped = false
                        showStopFailedNotification()
                        requestListeningState()
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error stopping time entry")
                // Revert optimistic state and show error
                optimisticallyStopped = false
                showStopFailedNotification()
                requestListeningState()
            }
        }
    }

    /**
     * Update tile state immediately (synchronously)
     */
    private fun updateTileStateImmediate() {
        qsTile?.apply {
            if (optimisticallyStarted) {
                state = Tile.STATE_ACTIVE
                label = optimisticProjectName ?: "Starting..."
                subtitle = optimisticTaskName ?: "Starting tracking..."
                icon = Icon.createWithResource(this@TimeTrackingTileService, R.drawable.ic_stop)
            } else if (optimisticallyStopped) {
                state = Tile.STATE_INACTIVE
                label = "Time Tracking"
                subtitle = "Stopping..."
                icon = Icon.createWithResource(this@TimeTrackingTileService, R.drawable.ic_timer)
            }
            updateTile()
        }
    }

    /**
     * Create notification channel for error notifications
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Time Tracking Errors"
            val descriptionText = "Notifications for time tracking errors"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Show notification when stopping time tracking fails
     */
    private fun showStopFailedNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Failed to stop time tracking")
            .setContentText("Tap to retry or open the app to check your time entries")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(trackingStartedReceiver)
        } catch (e: Exception) {
            Timber.w(e, "Failed to unregister receiver")
        }
        serviceScope.cancel()
    }
}

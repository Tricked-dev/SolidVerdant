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
import androidx.core.content.edit
import dagger.hilt.android.AndroidEntryPoint
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.CacheDataStore
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.remote.ApiClientFactory
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.ui.tile.ProjectSelectionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Quick Settings Tile for time tracking
 *
 * Optimistic UX approach:
 * - Happy path is instant (tile updates immediately, activity closes immediately)
 * - API calls happen in background in TileService (survives activity close)
 * - Failures shown via notifications
 * - Optimistic state persisted in SharedPreferences (survives service recreation)
 */
@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class TimeTrackingTileService : TileService() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var apiClientFactory: ApiClientFactory

    @Inject
    lateinit var cacheDataStore: CacheDataStore

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isProcessing = AtomicBoolean(false)
    private val isUpdating = AtomicBoolean(false)
    private var lastUpdateTime = 0L

    private val prefs by lazy {
        getSharedPreferences("tile_state", MODE_PRIVATE)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "time_tracking_errors"
        private const val NOTIFICATION_ID = 1001

        // Actions
        const val ACTION_START_TRACKING = "dev.tricked.solidverdant.ACTION_START_TRACKING"
        const val ACTION_REFRESH_TILE = "dev.tricked.solidverdant.ACTION_REFRESH_TILE"

        // Extras for start tracking
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_PROJECT_NAME = "project_name"
        const val EXTRA_TASK_NAME = "task_name"

        // Prefs keys
        private const val PREF_OPTIMISTIC_STATE = "optimistic_state"
        private const val PREF_OPTIMISTIC_PROJECT = "optimistic_project"
        private const val PREF_OPTIMISTIC_TASK = "optimistic_task"
        private const val PREF_OPTIMISTIC_TIMESTAMP = "optimistic_timestamp"
        private const val PREF_LAST_ENTRY_ID = "last_entry_id"
        private const val PREF_LAST_PROJECT_NAME = "last_project_name"
        private const val PREF_LAST_TASK_NAME = "last_task_name"
        private const val PREF_LAST_PROJECT_ID = "last_project_id"
        private const val PREF_LAST_TASK_ID = "last_task_id"
        private const val PREF_LAST_ORG_ID = "last_org_id"
        private const val PREF_LAST_USER_ID = "last_user_id"
        private const val PREF_LAST_START_TIME = "last_start_time"

        private const val OPTIMISTIC_TIMEOUT_MS = 30_000L
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Timber.d("TileService BroadcastReceiver.onReceive called - action=${intent?.action}, context=$context")
            when (intent?.action) {
                ACTION_START_TRACKING -> {
                    val projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
                    val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                    val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
                    val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME)
                    val taskName = intent.getStringExtra(EXTRA_TASK_NAME)

                    Timber.d("Received start tracking broadcast: project=$projectName, task=$taskName, projectId=$projectId, taskId=$taskId")

                    // Optimistic update immediately
                    setOptimisticStarting(projectName, taskName)
                    updateTileImmediate()

                    // API call in background
                    doStartTracking(projectId, taskId, description, projectName, taskName)
                }

                ACTION_REFRESH_TILE -> {
                    Timber.d("Received refresh request")
                    refreshTile()
                }

                TimeTrackingNotificationService.ACTION_STOP_TRACKING_BROADCAST -> {
                    Timber.d("Received stop tracking from notification")
                    // Handle stop tracking from notification
                    serviceScope.launch {
                        try {
                            val activeEntry = try {
                                withTimeoutOrNull(3000L) {
                                    authRepository.getActiveTimeEntry().getOrNull()
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "Network failed, checking cache")
                                null
                            }

                            when {
                                activeEntry != null -> {
                                    stopTracking(activeEntry)
                                }

                                else -> {
                                    val cachedEntry = getCachedEntryForStop()
                                    if (cachedEntry != null) {
                                        stopTrackingWithCache(cachedEntry)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to handle stop from notification")
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("TileService onCreate called")
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(ACTION_START_TRACKING)
            addAction(ACTION_REFRESH_TILE)
            addAction(TimeTrackingNotificationService.ACTION_STOP_TRACKING_BROADCAST)
        }

        Timber.d("TileService registering broadcast receiver with actions: ${filter.actionsIterator().asSequence().toList()}")
        registerReceiver(broadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onStartListening() {
        super.onStartListening()
        Timber.d("onStartListening")
        checkExpiredOptimisticState()
        updateTileState()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Timber.d("Tile added")
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        Timber.d("onClick")

        if (isProcessing.getAndSet(true)) {
            Timber.d("Already processing, ignoring click")
            return
        }

        serviceScope.launch {
            try {
                val isLoggedIn = authRepository.isLoggedIn.first()
                if (!isLoggedIn) {
                    Timber.w("Not logged in")
                    updateTileImmediate()
                    return@launch
                }

                // Try to get active entry from network
                val activeEntry = try {
                    withTimeoutOrNull(3000L) {
                        authRepository.getActiveTimeEntry().getOrNull()
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Network failed, checking cache")
                    null
                }

                when {
                    activeEntry != null -> {
                        // Network success - stop using real entry
                        stopTracking(activeEntry)
                    }

                    else -> {
                        // Network failed or no active entry
                        val cachedEntry = getCachedEntryForStop()
                        if (cachedEntry != null) {
                            // We have cached entry data - try to stop using it
                            Timber.d("Using cached entry for stop")
                            stopTrackingWithCache(cachedEntry)
                        } else {
                            // No active entry and no cache - show project selection
                            showProjectSelection()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle tile click")
            } finally {
                isProcessing.set(false)
            }
        }
    }

    private fun doStartTracking(
        projectId: String?,
        taskId: String?,
        description: String,
        projectName: String?,
        taskName: String?
    ) {
        Timber.d("doStartTracking called: projectId=$projectId, taskId=$taskId, projectName=$projectName, taskName=$taskName")
        serviceScope.launch {
            try {
                Timber.d("Fetching memberships and user info...")
                val memberships = authRepository.getMyMemberships().getOrNull()
                val membership = memberships?.firstOrNull()
                val user = authRepository.getCurrentUser().getOrNull()

                if (membership == null || user == null) {
                    Timber.e("Missing membership or user - membership=$membership, user=$user")
                    clearOptimisticState()
                    showNotification("Failed to start tracking", "Missing user data")
                    refreshTile()
                    return@launch
                }

                Timber.d("Starting time entry with orgId=${membership.organizationId}, memberId=${membership.id}, userId=${user.id}")

                val result = authRepository.startTimeEntry(
                    organizationId = membership.organizationId,
                    memberId = membership.id,
                    userId = user.id,
                    projectId = projectId,
                    taskId = taskId,
                    description = description
                )

                result.onSuccess { entry ->
                    Timber.d("Tracking started: ${entry.id}")
                    clearOptimisticState()
                    cacheActiveEntry(entry, projectName, taskName)

                    // Start persistent notification
                    TimeTrackingNotificationService.startTracking(
                        context = this@TimeTrackingTileService,
                        startTime = Instant.parse(entry.start),
                        projectName = projectName,
                        taskName = taskName,
                        description = description.takeIf { it.isNotBlank() }
                    )

                    refreshTile()
                }.onFailure { error ->
                    Timber.e(error, "Failed to start tracking")
                    clearOptimisticState()
                    showNotification("Failed to start tracking", error.message ?: "Unknown error")
                    refreshTile()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error starting tracking")
                clearOptimisticState()
                showNotification("Failed to start tracking", e.message ?: "Unknown error")
                refreshTile()
            }
        }
    }

    private fun stopTracking(activeEntry: TimeEntry) {
        // Optimistic update
        setOptimisticStopping()
        updateTileImmediate()

        serviceScope.launch {
            try {
                val result = authRepository.stopTimeEntry(
                    organizationId = activeEntry.organizationId,
                    timeEntryId = activeEntry.id,
                    userId = activeEntry.userId,
                    startTime = activeEntry.start
                )

                result.onSuccess {
                    Timber.d("Tracking stopped")
                    clearOptimisticState()
                    clearCachedEntry()

                    // Update notification state based on settings
                    val alwaysShow = settingsDataStore.alwaysShowNotification.first()
                    if (alwaysShow) {
                        TimeTrackingNotificationService.showIdle(this@TimeTrackingTileService)
                    } else {
                        TimeTrackingNotificationService.stopTracking(this@TimeTrackingTileService)
                    }

                    refreshTile()
                }.onFailure { error ->
                    Timber.e(error, "Failed to stop tracking")
                    clearOptimisticState()
                    showNotification("Failed to stop tracking", error.message ?: "Unknown error")
                    refreshTile()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error stopping tracking")
                clearOptimisticState()
                showNotification("Failed to stop tracking", e.message ?: "Unknown error")
                refreshTile()
            }
        }
    }

    /**
     * Stop tracking using cached entry data (for when network check failed but we have cache)
     */
    private fun stopTrackingWithCache(cached: CachedEntry) {
        // Optimistic update
        setOptimisticStopping()
        updateTileImmediate()

        serviceScope.launch {
            try {
                val result = authRepository.stopTimeEntry(
                    organizationId = cached.organizationId,
                    timeEntryId = cached.entryId,
                    userId = cached.userId,
                    startTime = cached.startTime
                )

                result.onSuccess {
                    Timber.d("Tracking stopped (from cache)")
                    clearOptimisticState()
                    clearCachedEntry()

                    // Update notification state based on settings
                    val alwaysShow = settingsDataStore.alwaysShowNotification.first()
                    if (alwaysShow) {
                        TimeTrackingNotificationService.showIdle(this@TimeTrackingTileService)
                    } else {
                        TimeTrackingNotificationService.stopTracking(this@TimeTrackingTileService)
                    }

                    refreshTile()
                }.onFailure { error ->
                    Timber.e(error, "Failed to stop tracking (from cache)")
                    clearOptimisticState()
                    // Don't clear cache on failure - entry might still be active
                    showNotification("Failed to stop tracking", error.message ?: "Unknown error")
                    refreshTile()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error stopping tracking (from cache)")
                clearOptimisticState()
                showNotification("Failed to stop tracking", e.message ?: "Unknown error")
                refreshTile()
            }
        }
    }

    /**
     * Update tile state with debouncing and cache-first approach.
     * Shows cached state immediately, then refreshes from network to catch external changes.
     */
    private fun updateTileState(forceNetwork: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceNetwork && now - lastUpdateTime < 500) {
            Timber.d("Debouncing updateTileState")
            return
        }
        lastUpdateTime = now

        if (!isUpdating.compareAndSet(false, true)) {
            Timber.d("Update already in progress, skipping")
            return
        }

        serviceScope.launch {
            try {
                val tile = qsTile ?: return@launch

                val optimistic = getOptimisticState()
                if (optimistic != null) {
                    applyState(tile, optimistic)
                    return@launch
                }

                val isLoggedIn = authRepository.isLoggedIn.first()
                if (!isLoggedIn) {
                    applyState(tile, TileState.NotLoggedIn)
                    return@launch
                }

                val cachedId = prefs.getString(PREF_LAST_ENTRY_ID, null)
                if (cachedId != null) {
                    val cachedProject = prefs.getString(PREF_LAST_PROJECT_NAME, null)
                    val cachedTask = prefs.getString(PREF_LAST_TASK_NAME, null)
                    applyState(tile, TileState.Active(cachedProject, cachedTask, null))
                }

                // Fetch from network - Result.success(null) means no entry, Result.failure means network failed
                val result = try {
                    withTimeoutOrNull(5000L) {
                        authRepository.getActiveTimeEntry()
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Network fetch failed")
                    null
                }

                when {
                    result == null -> {
                        // Timeout or exception - keep cached state
                        Timber.d("Network timeout, keeping cached state")
                    }

                    result.isSuccess -> {
                        val activeEntry = result.getOrNull()
                        if (activeEntry != null) {
                            val entryChanged = activeEntry.id != cachedId ||
                                    activeEntry.projectId != prefs.getString(
                                PREF_LAST_PROJECT_ID,
                                null
                            ) ||
                                    activeEntry.taskId != prefs.getString(PREF_LAST_TASK_ID, null)

                            if (entryChanged) {
                                val (projectName, taskName) = loadNames(activeEntry)
                                cacheActiveEntry(activeEntry, projectName, taskName)
                                applyState(
                                    tile,
                                    TileState.Active(projectName, taskName, activeEntry.description)
                                )

                                // Start notification for externally started tracking
                                TimeTrackingNotificationService.startTracking(
                                    context = this@TimeTrackingTileService,
                                    startTime = Instant.parse(activeEntry.start),
                                    projectName = projectName,
                                    taskName = taskName,
                                    description = activeEntry.description
                                )
                            }
                        } else if (cachedId != null) {
                            // Network succeeded, no active entry - stopped externally
                            Timber.d("Entry stopped externally, clearing cache")
                            clearCachedEntry()
                            applyState(tile, TileState.Inactive)

                            // Update notification state based on settings
                            val alwaysShow = settingsDataStore.alwaysShowNotification.first()
                            if (alwaysShow) {
                                TimeTrackingNotificationService.showIdle(this@TimeTrackingTileService)
                            } else {
                                TimeTrackingNotificationService.stopTracking(this@TimeTrackingTileService)
                            }
                        } else {
                            applyState(tile, TileState.Inactive)
                        }
                    }

                    result.isFailure -> {
                        // Network failed - keep cached state
                        Timber.d("Network failed: ${result.exceptionOrNull()?.message}, keeping cached state")
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to update tile")
                applyFallbackState()
            } finally {
                isUpdating.set(false)
            }
        }
    }

    private fun updateTileImmediate() {
        val tile = qsTile ?: return
        val optimistic = getOptimisticState()
        if (optimistic != null) {
            applyStateSync(tile, optimistic)
        } else {
            // Show cached state immediately
            applyFallbackStateSync(tile)
        }
    }

    /**
     * Force a tile refresh with network call
     */
    private fun refreshTile() {
        try {
            requestListeningState(this, ComponentName(this, TimeTrackingTileService::class.java))
        } catch (e: Exception) {
            Timber.w(e, "requestListeningState failed")
        }
        // Force network refresh
        lastUpdateTime = 0 // Reset debounce
        isUpdating.set(false) // Allow new update
        updateTileState(forceNetwork = true)
    }

    private sealed class TileState {
        object NotLoggedIn : TileState()
        object Inactive : TileState()
        data class Active(
            val projectName: String?,
            val taskName: String?,
            val description: String?
        ) : TileState()

        data class Starting(val projectName: String?, val taskName: String?) : TileState()
        object Stopping : TileState()
    }

    private fun applyState(tile: Tile, state: TileState) {
        serviceScope.launch(Dispatchers.Main) {
            applyStateSync(tile, state)
        }
    }

    private fun applyStateSync(tile: Tile, state: TileState) {
        when (state) {
            is TileState.NotLoggedIn -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Time Tracking"
                tile.subtitle = "Not logged in"
                tile.icon = Icon.createWithResource(this, R.drawable.ic_timer)
            }

            is TileState.Inactive -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Time Tracking"
                tile.subtitle = "Tap to start"
                tile.icon = Icon.createWithResource(this, R.drawable.ic_timer)
            }

            is TileState.Active -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = state.projectName ?: "Tracking"
                tile.subtitle = state.taskName
                    ?: state.description?.takeIf { it.isNotEmpty() }
                            ?: "Tap to stop"
                tile.icon = Icon.createWithResource(this, R.drawable.ic_stop)
            }

            is TileState.Starting -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = state.projectName ?: "Starting..."
                tile.subtitle = state.taskName ?: "Starting..."
                tile.icon = Icon.createWithResource(this, R.drawable.ic_stop)
            }

            is TileState.Stopping -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Time Tracking"
                tile.subtitle = "Stopping..."
                tile.icon = Icon.createWithResource(this, R.drawable.ic_timer)
            }
        }
        tile.updateTile()
    }

    private fun applyFallbackState() {
        val tile = qsTile ?: return
        serviceScope.launch(Dispatchers.Main) {
            applyFallbackStateSync(tile)
        }
    }

    private fun applyFallbackStateSync(tile: Tile) {
        val cachedId = prefs.getString(PREF_LAST_ENTRY_ID, null)
        if (cachedId != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = prefs.getString(PREF_LAST_PROJECT_NAME, null) ?: "Tracking"
            tile.subtitle = prefs.getString(PREF_LAST_TASK_NAME, null) ?: "Tap to stop"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_stop)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Time Tracking"
            tile.subtitle = "Tap to start"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_timer)
        }
        tile.updateTile()
    }

    private fun setOptimisticStarting(projectName: String?, taskName: String?) {
        prefs.edit {
            putString(PREF_OPTIMISTIC_STATE, "starting")
            putString(PREF_OPTIMISTIC_PROJECT, projectName)
            putString(PREF_OPTIMISTIC_TASK, taskName)
            putLong(PREF_OPTIMISTIC_TIMESTAMP, System.currentTimeMillis())
        }
    }

    private fun setOptimisticStopping() {
        prefs.edit {
            putString(PREF_OPTIMISTIC_STATE, "stopping")
            putLong(PREF_OPTIMISTIC_TIMESTAMP, System.currentTimeMillis())
        }
    }

    private fun getOptimisticState(): TileState? {
        return when (prefs.getString(PREF_OPTIMISTIC_STATE, null)) {
            "starting" -> TileState.Starting(
                prefs.getString(PREF_OPTIMISTIC_PROJECT, null),
                prefs.getString(PREF_OPTIMISTIC_TASK, null)
            )

            "stopping" -> TileState.Stopping
            else -> null
        }
    }

    private fun clearOptimisticState() {
        prefs.edit {
            remove(PREF_OPTIMISTIC_STATE)
            remove(PREF_OPTIMISTIC_PROJECT)
            remove(PREF_OPTIMISTIC_TASK)
            remove(PREF_OPTIMISTIC_TIMESTAMP)
        }
    }

    private fun checkExpiredOptimisticState() {
        val timestamp = prefs.getLong(PREF_OPTIMISTIC_TIMESTAMP, 0)
        if (timestamp > 0 && System.currentTimeMillis() - timestamp > OPTIMISTIC_TIMEOUT_MS) {
            Timber.d("Clearing expired optimistic state")
            clearOptimisticState()
        }
    }

    private fun cacheActiveEntry(entry: TimeEntry, projectName: String?, taskName: String?) {
        prefs.edit {
            putString(PREF_LAST_ENTRY_ID, entry.id)
            putString(PREF_LAST_PROJECT_NAME, projectName)
            putString(PREF_LAST_TASK_NAME, taskName)
            putString(PREF_LAST_PROJECT_ID, entry.projectId)
            putString(PREF_LAST_TASK_ID, entry.taskId)
            putString(PREF_LAST_ORG_ID, entry.organizationId)
            putString(PREF_LAST_USER_ID, entry.userId)
            putString(PREF_LAST_START_TIME, entry.start)
        }
    }

    private fun clearCachedEntry() {
        prefs.edit {
            remove(PREF_LAST_ENTRY_ID)
            remove(PREF_LAST_PROJECT_NAME)
            remove(PREF_LAST_TASK_NAME)
            remove(PREF_LAST_PROJECT_ID)
            remove(PREF_LAST_TASK_ID)
            remove(PREF_LAST_ORG_ID)
            remove(PREF_LAST_USER_ID)
            remove(PREF_LAST_START_TIME)
        }
    }

    /**
     * Get cached entry for offline stop. Returns null if cache is incomplete.
     */
    private fun getCachedEntryForStop(): CachedEntry? {
        val entryId = prefs.getString(PREF_LAST_ENTRY_ID, null) ?: return null
        val orgId = prefs.getString(PREF_LAST_ORG_ID, null) ?: return null
        val userId = prefs.getString(PREF_LAST_USER_ID, null) ?: return null
        val startTime = prefs.getString(PREF_LAST_START_TIME, null) ?: return null
        return CachedEntry(entryId, orgId, userId, startTime)
    }

    private data class CachedEntry(
        val entryId: String,
        val organizationId: String,
        val userId: String,
        val startTime: String
    )

    private suspend fun loadNames(entry: TimeEntry): Pair<String?, String?> {
        val cachedProject = prefs.getString(PREF_LAST_PROJECT_NAME, null)
        val cachedTask = prefs.getString(PREF_LAST_TASK_NAME, null)
        val cachedId = prefs.getString(PREF_LAST_ENTRY_ID, null)
        val cachedProjectId = prefs.getString(PREF_LAST_PROJECT_ID, null)
        val cachedTaskId = prefs.getString(PREF_LAST_TASK_ID, null)

        // Use cache only if same entry AND same project/task
        val sameEntry = cachedId == entry.id
        val sameProject = cachedProjectId == entry.projectId
        val sameTask = cachedTaskId == entry.taskId

        if (sameEntry && sameProject && sameTask && (cachedProject != null || entry.projectId == null)) {
            return Pair(cachedProject, cachedTask)
        }

        var projects = cacheDataStore.getCachedProjects()
        var tasks = cacheDataStore.getCachedTasks()

        if ((entry.projectId != null && projects == null) || (entry.taskId != null && tasks == null)) {
            try {
                val memberships = authRepository.getMyMemberships().getOrNull()
                val orgId = memberships?.firstOrNull()?.organizationId ?: return Pair(null, null)
                val endpoint = authRepository.endpoint.first()
                val api = apiClientFactory.createApi(endpoint)

                if (projects == null) {
                    projects = api.getProjects(orgId).data
                    cacheDataStore.cacheProjects(projects)
                }
                if (tasks == null) {
                    tasks = api.getTasks(orgId).data
                    cacheDataStore.cacheTasks(tasks)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load projects/tasks")
                return Pair(null, null)
            }
        }

        val projectName = entry.projectId?.let { pid -> projects?.find { it.id == pid }?.name }
        val taskName = entry.taskId?.let { tid -> tasks?.find { it.id == tid }?.name }
        return Pair(projectName, taskName)
    }

    private fun showProjectSelection() {
        val intent = Intent(this, ProjectSelectionActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startActivityAndCollapse(pendingIntent)
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Time Tracking Errors",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
            Timber.w(e, "Failed to unregister receiver")
        }
        serviceScope.cancel()
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.AppThemeMode
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.domain.time.TemporalPolicy
import dev.tricked.solidverdant.domain.time.TemporalPolicyProvider
import dev.tricked.solidverdant.service.TimeTrackingNotificationService
import dev.tricked.solidverdant.sync.SyncTrigger
import dev.tricked.solidverdant.util.Clock
import dev.tricked.solidverdant.util.IsoTimes
import dev.tricked.solidverdant.widget.TimeTrackingWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val RATE_LIMIT_RETRY_ATTEMPTS = 3
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val MIN_RETRY_AFTER_SECONDS = 1
private const val MAX_RETRY_AFTER_SECONDS = 60
private const val DEFAULT_RETRY_AFTER_SECONDS = 5
private const val MILLIS_PER_SECOND = 1_000L
private const val TIMER_TICK_INTERVAL_MS = 1_000L
private const val HISTORY_PROGRESS_CAP = 0.9f

/** Which slice of history the user is currently looking at. */
internal enum class HistoryWindowMode { RECENT, PAGINATED }

/**
 * Single source of truth for how a Room emission from the recent-window collector combines with
 * the list currently on screen.
 *
 * In [HistoryWindowMode.RECENT] the collector owns the list and replaces it wholesale, so live
 * edits and the active-entry poll stay fresh. Once the user pages or jumps to an off-window slice
 * ([HistoryWindowMode.PAGINATED]) the network-fetched window is authoritative: its order and
 * membership are preserved (so scroll position survives a poll emission) while any fresher copy of
 * a still-visible entry carried by the recent collector is overlaid in place.
 */
internal object HistoryWindow {
    fun merge(mode: HistoryWindowMode, displayed: List<TimeEntry>, collected: List<TimeEntry>): List<TimeEntry> = when (mode) {
        HistoryWindowMode.RECENT -> collected
        HistoryWindowMode.PAGINATED -> {
            val collectedById = collected.associateBy { it.id }
            displayed.map { collectedById[it.id] ?: it }
        }
    }
}

/**
 * UI state for tracking screen
 */
@Stable
data class TrackingUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSyncing: Boolean = false,
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val currentTimeEntry: TimeEntry? = null,
    val timeEntries: List<TimeEntry> = emptyList(),
    val overlapCount: Int = 0,
    val hasLoadedTimeEntries: Boolean = false,
    val isLoadingMoreTimeEntries: Boolean = false,
    val hasMoreTimeEntries: Boolean = false,
    val totalTimeEntries: Int? = null,
    val historyJumpDate: LocalDate? = null,
    val historyJumpTarget: LocalDate? = null,
    val historyJumpProgress: Float? = null,
    val historyRateLimitWaitSeconds: Int? = null,
    val canLoadNewerHistory: Boolean = false,
    val cachedContinueEntry: TimeEntry? = null,
    val projects: List<Project> = emptyList(),
    val clients: List<Client> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val elapsedSeconds: Long = 0,
    val error: String? = null,
    // Current entry editing state
    val editingDescription: String = "",
    val editingProjectId: String? = null,
    val editingTaskId: String? = null,
    val editingTags: List<String> = emptyList(),
    val editingBillable: Boolean = false,
    val syncOperations: List<TimeEntryRepository.SyncOperation> = emptyList(),
    val conflictedEntryIds: Set<String> = emptySet(),
    /** Account temporal-policy zone; history filtering and new-entry pickers use it. */
    val zone: ZoneId = ZoneId.systemDefault(),
    /**
     * Roadmap #13: id of an entry the UI should open for editing right after a duplicate/split
     * (the freshly created copy / second half). One-shot: cleared via [TrackingViewModel.consumeEntryToEdit].
     */
    val entryToEditId: String? = null,
) {
    /** Mutations retain the legacy internal flag; refresh/sync have independent flags. */
    val isMutating: Boolean get() = isLoading
}

/**
 * ViewModel for time tracking operations
 */
@HiltViewModel
@Suppress("LargeClass")
class TrackingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsDataStore: SettingsDataStore,
    private val timeEntryRepository: TimeEntryRepository,
    private val syncTrigger: SyncTrigger,
    private val temporalPolicyProvider: TemporalPolicyProvider,
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ViewModel() {

    // Account temporal-policy zone. Seeded synchronously (first-frame correct) and kept current by
    // the collector in init. Provider owns the device-zone fallback.
    @Volatile
    private var currentPolicy: TemporalPolicy = runBlocking { temporalPolicyProvider.current() }

    private val cachedTrackingState = settingsDataStore.getCachedTrackingState()
    private val _uiState = MutableStateFlow(
        cachedTrackingState?.let { cached ->
            TrackingUiState(
                isTracking = cached.activeEntry != null,
                currentTimeEntry = cached.activeEntry,
                timeEntries = cached.timeEntries,
                overlapCount = cached.overlapCount,
                hasLoadedTimeEntries = true,
                cachedContinueEntry = cached.timeEntries
                    .firstOrNull { it.end != null && !it.description.isNullOrBlank() }
                    ?: settingsDataStore.getCachedContinueEntry(),
                projects = cached.projects,
                clients = cached.clients,
                tasks = cached.tasks,
                tags = cached.tags,
                editingDescription = cached.activeEntry?.description.orEmpty(),
                editingProjectId = cached.activeEntry?.projectId,
                editingTaskId = cached.activeEntry?.taskId,
                editingTags = cached.activeEntry?.tags?.map { it.id }.orEmpty(),
                editingBillable = cached.activeEntry?.billable ?: false,
                zone = currentPolicy.zone,
            )
        } ?: TrackingUiState(
            cachedContinueEntry = settingsDataStore.getCachedContinueEntry(),
            zone = currentPolicy.zone,
        ),
    )
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            temporalPolicyProvider.policy.collect { policy ->
                currentPolicy = policy
                _uiState.value = _uiState.value.copy(zone = policy.zone)
            }
        }
    }

    val alwaysShowNotifications = settingsDataStore.alwaysShowNotification
    val appTheme = settingsDataStore.appTheme
    val optimisticRefresh = settingsDataStore.optimisticRefresh
    val longTimerHours = settingsDataStore.longTimerHours
    private val _snapshotHydrated = MutableStateFlow(false)
    val snapshotHydrated: StateFlow<Boolean> = _snapshotHydrated.asStateFlow()
    private val _hasSnapshot = MutableStateFlow(false)
    val hasSnapshot: StateFlow<Boolean> = _hasSnapshot.asStateFlow()

    private var timerJob: Job? = null
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()
    private var loadDataJob: Job? = null
    private var loadingOrganizationId: String? = null
    private var activeEntryMonitorJob: Job? = null
    private var monitoredOrganizationId: String? = null
    private var dataCollectorJob: Job? = null
    private var syncCollectorJob: Job? = null
    private var firstFrameCacheJob: Job? = null
    private var hasCachedContinueEntry = false
    private var lastCachedContinueEntry: TimeEntry? = null
    private var collectingOrganizationId: String? = null
    private var lastCollectedActiveId: String? = null
    private var historyOrganizationId: String? = null
    private var historyMemberId: String? = null
    private var historyLoadStage = 0
    private var historyOffset = 0
    private var historyWindowStartOffset = 0
    private var historyWindowMode = HistoryWindowMode.RECENT
    private var isInitialized = false

    /**
     * Wall-clock of the last foreground-triggered full refresh. Returning to the app (or a rapid
     * start/stop) within [FOREGROUND_REFRESH_DEBOUNCE_MS] reuses the just-fetched data instead of
     * re-hitting the network, while an explicit user refresh remains unthrottled.
     */
    private var lastForegroundRefreshMs: Long? = null

    /**
     * SV-019: one in-flight "commit the delete once the undo window closes" job per soft-deleted
     * entry id. Only the local soft-delete happens synchronously in [deleteTimeEntry]; the actual
     * outbox commit (server DELETE, or cancelling an unsynced entry's START/CREATE - see
     * [TimeEntryRepository.commitDelete]) is deferred to this job so it can be cancelled outright
     * by [undoDelete] with nothing ever having reached the outbox to race the sync worker.
     */
    private val pendingDeleteCommitJobs = mutableMapOf<String, Job>()

    init {
        // Room is now the read source-of-truth; there is no separate snapshot to hydrate.
        _snapshotHydrated.value = true
        _hasSnapshot.value = cachedTrackingState != null
        // Monitor settings changes and update notification state
        viewModelScope.launch {
            settingsDataStore.alwaysShowNotification.collect { enabled ->
                // Only update notification state after initial data load
                if (isInitialized) {
                    updateNotificationState()
                }
            }
        }
    }

    /**
     * Set whether to always show notifications
     */
    fun setAlwaysShowNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAlwaysShowNotification(enabled)
            // updateNotificationState() is called automatically by the collector in init
        }
    }

    fun setAppTheme(theme: AppThemeMode) {
        viewModelScope.launch { settingsDataStore.setAppTheme(theme) }
    }

    fun setOptimisticRefresh(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setOptimisticRefresh(enabled) }
    }

    fun setLongTimerHours(hours: Int) {
        viewModelScope.launch {
            settingsDataStore.setLongTimerHours(hours)
            if (_uiState.value.isTracking) {
                TimeTrackingNotificationService.refreshLongTimerWarning(context)
            }
        }
    }

    /**
     * Update notification state based on tracking status and settings
     */
    private suspend fun updateNotificationState() {
        val alwaysShow = settingsDataStore.alwaysShowNotification.first()
        val isTracking = _uiState.value.isTracking

        if (isTracking) {
            // Active tracking always owns a foreground notification.
            return
        } else if (alwaysShow) {
            TimeTrackingNotificationService.showIdle(context)
        } else {
            TimeTrackingNotificationService.hide(context)
        }
    }

    /**
     * Load all data needed for the tracking screen
     */
    fun loadAllData(organizationId: String, memberId: String, userInitiated: Boolean = false) {
        historyOrganizationId = organizationId
        historyMemberId = memberId

        // Reads: continuously project the Room source-of-truth into UI state.
        startDataCollectors(organizationId)

        // Refresh: pull fresh data from the network into Room in the background. The
        // collectors above surface the upserts automatically.
        if (loadDataJob?.isActive == true && loadingOrganizationId == organizationId) {
            return
        }
        loadDataJob?.cancel()
        loadingOrganizationId = organizationId
        loadDataJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRefreshing = userInitiated,
                error = null,
            )
            timeEntryRepository.refreshAll(organizationId, memberId)
                .onFailure { error ->
                    Timber.w(error, "Background refresh failed; showing cached data")
                    if (userInitiated) _uiState.value = _uiState.value.copy(error = error.message)
                }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
            if (loadingOrganizationId == organizationId) {
                loadingOrganizationId = null
            }
            startActiveEntryMonitoring(organizationId)
        }
    }

    /** Collect the Room-backed flows for an organization into [TrackingUiState]. */
    private fun startDataCollectors(organizationId: String) {
        if (collectingOrganizationId == organizationId && dataCollectorJob?.isActive == true) {
            return
        }
        dataCollectorJob?.cancel()
        syncCollectorJob?.cancel()
        firstFrameCacheJob?.cancel()
        collectingOrganizationId = organizationId
        lastCollectedActiveId = null
        historyLoadStage = 1
        historyWindowStartOffset = 0
        historyOffset = 0
        historyWindowMode = HistoryWindowMode.RECENT
        dataCollectorJob = viewModelScope.launch {
            combine(
                combine(
                    timeEntryRepository.observeTimeEntries(organizationId),
                    timeEntryRepository.observeConflicts(organizationId),
                ) { entries, conflicts -> entries to conflicts.map { it.local.id }.toSet() },
                timeEntryRepository.observeProjects(organizationId),
                timeEntryRepository.observeTasks(organizationId),
                combine(
                    timeEntryRepository.observeTags(organizationId),
                    timeEntryRepository.observeClients(organizationId),
                ) { tags, clients -> tags to clients },
                timeEntryRepository.observeActiveEntry(organizationId),
            ) { entriesAndConflicts, projects, tasks, catalog, active ->
                TrackingData(
                    entries = entriesAndConflicts.first,
                    conflictedEntryIds = entriesAndConflicts.second,
                    projects = projects.filterNot { it.isArchived },
                    tasks = tasks.filterNot { it.isDone },
                    tags = catalog.first,
                    clients = catalog.second,
                    active = active,
                )
            }.distinctUntilChanged().map { data ->
                // Per-emission analysis is O(n) over the full entry window; flowOn(Default) below
                // keeps it (plus combine's TrackingData construction and the equality checks of
                // distinctUntilChanged) off the main thread so a Room emission burst cannot
                // produce a long frame mid-scroll.
                CollectedTracking(
                    data = data,
                    overlapCount = EntryTrustRules.overlapCount(data.entries),
                    continueEntry = data.entries
                        .filter { it.end != null && !it.description.isNullOrBlank() }
                        .maxByOrNull { it.start },
                )
            }.flowOn(Dispatchers.Default).conflate().collect { (data, overlapCount, continueEntry) ->
                val activeChanged = data.active?.id != lastCollectedActiveId
                val currentState = _uiState.value
                val mode = historyWindowMode
                // Single source of truth: the collector only owns the displayed list (and the
                // paging offset) while the recent slice is on screen. Once the user has paged or
                // jumped, loadMore/jump own the window and offset; here we merely refresh visible
                // entries in place so a poll emission cannot wipe the window or reset scroll.
                val displayedEntries = HistoryWindow.merge(mode, currentState.timeEntries, data.entries)
                if (mode == HistoryWindowMode.RECENT) {
                    historyOffset = data.entries.size
                }
                _uiState.value = currentState.copy(
                    timeEntries = displayedEntries,
                    overlapCount = overlapCount,
                    projects = data.projects,
                    tasks = data.tasks,
                    tags = data.tags,
                    clients = data.clients,
                    conflictedEntryIds = data.conflictedEntryIds,
                    currentTimeEntry = data.active,
                    isTracking = data.active != null,
                    hasLoadedTimeEntries = true,
                    // Heuristic: if we filled the refresh window there may be older history
                    // to page in from the network (see loadMoreTimeEntries). Preserve the flag
                    // maintained by loadMore/jump once the user is viewing a paginated window.
                    hasMoreTimeEntries = if (mode == HistoryWindowMode.RECENT) {
                        data.entries.size >= HISTORY_REFRESH_LIMIT
                    } else {
                        currentState.hasMoreTimeEntries
                    },
                    cachedContinueEntry = continueEntry,
                    isLoading = false,
                    // Only reset in-progress edits when the active entry itself changes,
                    // so a user's typing is not clobbered by a background emission.
                    editingDescription = if (activeChanged) data.active?.description.orEmpty() else currentState.editingDescription,
                    editingProjectId = if (activeChanged) data.active?.projectId else currentState.editingProjectId,
                    editingTaskId = if (activeChanged) data.active?.taskId else currentState.editingTaskId,
                    editingTags = if (activeChanged) data.active?.tags?.map { it.id }.orEmpty() else currentState.editingTags,
                    editingBillable = if (activeChanged) (data.active?.billable ?: false) else currentState.editingBillable,
                )
                // Both caches JSON-encode sizable object graphs; keep that (and the
                // SharedPreferences write) off the main thread, and skip no-op continue writes.
                if (!hasCachedContinueEntry || continueEntry != lastCachedContinueEntry) {
                    hasCachedContinueEntry = true
                    lastCachedContinueEntry = continueEntry
                    viewModelScope.launch(Dispatchers.IO) {
                        settingsDataStore.cacheContinueEntry(continueEntry)
                    }
                }
                firstFrameCacheJob?.cancel()
                firstFrameCacheJob = viewModelScope.launch(Dispatchers.IO) {
                    delay(FIRST_FRAME_CACHE_DEBOUNCE_MS)
                    settingsDataStore.cacheTrackingState(
                        SettingsDataStore.CachedTrackingState(
                            organizationId = organizationId,
                            timeEntries = data.entries.take(FIRST_FRAME_ENTRY_LIMIT),
                            projects = data.projects,
                            clients = data.clients,
                            tasks = data.tasks,
                            tags = data.tags,
                            activeEntry = data.active,
                            overlapCount = overlapCount,
                        ),
                    )
                    _hasSnapshot.value = true
                }
                if (activeChanged) {
                    lastCollectedActiveId = data.active?.id
                    if (data.active != null) startTimer(data.active.start) else stopTimer()
                }
            }
        }
        syncCollectorJob = viewModelScope.launch {
            timeEntryRepository.observeSyncOperations(organizationId)
                .distinctUntilChanged()
                .collect { operations ->
                    _uiState.value = _uiState.value.copy(syncOperations = operations)
                }
        }
    }

    private data class TrackingData(
        val entries: List<TimeEntry>,
        val conflictedEntryIds: Set<String>,
        val projects: List<Project>,
        val clients: List<Client>,
        val tasks: List<Task>,
        val tags: List<Tag>,
        val active: TimeEntry?,
    )

    /** [TrackingData] plus the derived values computed off the main thread. */
    private data class CollectedTracking(val data: TrackingData, val overlapCount: Int, val continueEntry: TimeEntry?)

    /**
     * Keep notification state in sync with timers started or stopped on another device while
     * this ViewModel is alive. Changing organizations replaces the old monitor immediately.
     */
    /** True while [entryId] still has a queued/retrying/failed outbox operation. */
    private fun hasUnsyncedOp(entryId: String): Boolean = _uiState.value.syncOperations.any {
        it.entryId == entryId && it.status != TimeEntryRepository.EntrySyncStatus.SYNCED
    }

    private fun startActiveEntryMonitoring(organizationId: String) {
        if (monitoredOrganizationId == organizationId && activeEntryMonitorJob?.isActive == true) {
            return
        }

        activeEntryMonitorJob?.cancel()
        monitoredOrganizationId = organizationId
        activeEntryMonitorJob = viewModelScope.launch {
            while (true) {
                delay(ACTIVE_ENTRY_REFRESH_INTERVAL_MS)
                loadActiveTimeEntry(organizationId, onlyIfChanged = true)
            }
        }
    }

    /** Pause network polling while the app is not visible. */
    fun onAppBackgrounded() {
        activeEntryMonitorJob?.cancel()
        activeEntryMonitorJob = null
    }

    /**
     * Resume polling, refreshing all visible data after a longer background pause.
     *
     * The full-refresh path is debounced on wall-clock so that returning within a few seconds (or a
     * rapid start/stop) does not spam the network; the in-screen poll and the collectors already
     * keep an open screen fresh, so the foreground refresh only matters when the screen has gone
     * stale. Pending local changes are flushed via [SyncTrigger.requestSync] on the same schedule.
     */
    fun onAppForegrounded(organizationId: String, memberId: String, refreshAll: Boolean) {
        if (refreshAll) {
            val now = clock.nowMs()
            val last = lastForegroundRefreshMs
            if (last != null && now - last < FOREGROUND_REFRESH_DEBOUNCE_MS) {
                // Debounced: the data fetched moments ago is still fresh. Keep polling alive.
                startActiveEntryMonitoring(organizationId)
                return
            }
            lastForegroundRefreshMs = now
            // Flush any queued local changes to the server before we re-read fresh state.
            syncTrigger.requestSync()
            loadAllData(organizationId, memberId)
        } else {
            // Returning from the tile picker is usually too brief to trigger a full refresh,
            // but its quick-start request may have changed the active entry.
            viewModelScope.launch {
                loadActiveTimeEntry(organizationId, onlyIfChanged = true)
            }
            startActiveEntryMonitoring(organizationId)
        }
    }

    /**
     * Cancels [viewModelScope] for unit tests that install a test Main dispatcher; mirrors the
     * sync-center VM teardown so no Main-bound collector straggles past `Dispatchers.resetMain()`.
     * Not used in production.
     */
    @VisibleForTesting
    internal fun cancelScopeForTest() {
        viewModelScope.coroutineContext[Job]?.cancel()
    }

    /**
     * Load the active time entry for the current user
     */
    private suspend fun loadActiveTimeEntry(organizationId: String, onlyIfChanged: Boolean = false) {
        authRepository.getActiveTimeEntry()
            .onSuccess { timeEntry ->
                // The active-entry endpoint is account-wide. Only surface an entry for the
                // organization currently selected in the app.
                val currentTimeEntry = timeEntry?.takeIf {
                    it.organizationId == organizationId
                }
                // A locally started timer whose START/CREATE is still in the outbox does not
                // exist on the server yet. A poll or foreground refresh answering "no active
                // entry" must not clear it, or the user's running timer silently disappears
                // until the next sync (found by TrackingLifecycleE2eTest recreation flow).
                val local = _uiState.value.currentTimeEntry
                if (currentTimeEntry == null && local != null && hasUnsyncedOp(local.id)) {
                    return@onSuccess
                }
                if (onlyIfChanged &&
                    currentTimeEntry?.id == _uiState.value.currentTimeEntry?.id
                ) {
                    if (currentTimeEntry != null) {
                        TimeTrackingNotificationService.startTracking(
                            context = context,
                            startTime = Instant.parse(currentTimeEntry.start),
                            projectName = _uiState.value.projects
                                .find { it.id == currentTimeEntry.projectId }?.name,
                            taskName = _uiState.value.tasks
                                .find { it.id == currentTimeEntry.taskId }?.name,
                            description = currentTimeEntry.description,
                        )
                    } else {
                        updateNotificationState()
                    }
                    return@onSuccess
                }
                val isTracking = currentTimeEntry != null
                _uiState.value = _uiState.value.copy(
                    isTracking = isTracking,
                    currentTimeEntry = currentTimeEntry,
                    editingDescription = currentTimeEntry?.description ?: "",
                    editingProjectId = currentTimeEntry?.projectId,
                    editingTaskId = currentTimeEntry?.taskId,
                    editingTags = currentTimeEntry?.tags?.map { it.id } ?: emptyList(),
                    editingBillable = currentTimeEntry?.billable ?: false,
                )

                // Update notification state based on tracking status and settings
                if (currentTimeEntry != null) {
                    val projectName = _uiState.value.projects
                        .find { it.id == currentTimeEntry.projectId }?.name
                    val taskName = _uiState.value.tasks
                        .find { it.id == currentTimeEntry.taskId }?.name
                    TimeTrackingNotificationService.startTracking(
                        context = context,
                        startTime = Instant.parse(currentTimeEntry.start),
                        projectName = projectName,
                        taskName = taskName,
                        description = currentTimeEntry.description,
                    )
                    settingsDataStore.setWidgetTrackingState(
                        isTracking = true,
                        startTimeEpochMillis = Instant.parse(currentTimeEntry.start).toEpochMilli(),
                        projectName = projectName,
                        taskName = taskName,
                        description = currentTimeEntry.description,
                    )
                } else {
                    // Update notification and widget state for non-tracking cases
                    updateNotificationState()
                    settingsDataStore.setWidgetTrackingState(
                        isTracking = false,
                    )
                }
                // Request widget update
                TimeTrackingWidget.requestUpdate(context)

                // Start timer if tracking
                if (isTracking) {
                    startTimer(currentTimeEntry.start)
                } else {
                    stopTimer()
                }

                // Mark as initialized after first load
                isInitialized = true
            }
            .onFailure { error ->
                Timber.e(error, "Failed to load active time entry")
                _uiState.value = _uiState.value.copy(
                    isTracking = false,
                    error = error.message ?: "Failed to load tracking state",
                )
                stopTimer()

                // Mark as initialized even on failure
                isInitialized = true
            }
    }

    /** Load history progressively while retaining fetched entries for this app session. */
    fun loadMoreTimeEntries() {
        val organizationId = historyOrganizationId ?: return
        val memberId = historyMemberId ?: return
        val state = _uiState.value
        if (state.isLoadingMoreTimeEntries || !state.hasMoreTimeEntries) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMoreTimeEntries = true)
            val (limit, offset) = when (historyLoadStage) {
                0 -> FIRST_SCROLL_TOTAL to 0
                1 -> MAX_PAGE_SIZE to 0
                else -> MAX_PAGE_SIZE to historyOffset
            }
            authRepository.getTimeEntries(organizationId, memberId, limit, offset)
                .onSuccess { response ->
                    val currentEntries = _uiState.value.timeEntries
                    val currentTags = _uiState.value.tags
                    // Tag resolution plus the dedupe/sort of a growing window is O(n log n);
                    // keep it off the main thread so paging in more history cannot jank a
                    // scroll that is still settling.
                    val (incoming, merged) = withContext(Dispatchers.Default) {
                        val tagsById = currentTags.associateBy { it.id }
                        val resolved = response.data.map { entry ->
                            entry.copy(tags = entry.tags.map { tagsById[it.id] ?: it })
                        }
                        resolved to (currentEntries + resolved)
                            .distinctBy { it.id }
                            .sortedByDescending { it.start }
                    }
                    val total = response.meta?.total ?: _uiState.value.totalTimeEntries
                    historyOffset = if (historyLoadStage <= 1) {
                        incoming.size
                    } else {
                        historyOffset + incoming.size
                    }
                    historyLoadStage++
                    // The user has explicitly asked for more than the recent slice; the collector
                    // must now preserve this grown window instead of replacing it on every poll.
                    historyWindowMode = HistoryWindowMode.PAGINATED
                    _uiState.value = _uiState.value.copy(
                        timeEntries = merged,
                        isLoadingMoreTimeEntries = false,
                        hasMoreTimeEntries = incoming.isNotEmpty() &&
                            (total?.let { merged.size < it } ?: true),
                        totalTimeEntries = total,
                    )
                    // Once the user asks for more history, quickly fill the first maximum-sized
                    // buffer so continued scrolling does not catch the network boundary.
                    if (historyLoadStage == 1 && _uiState.value.hasMoreTimeEntries) {
                        loadMoreTimeEntries()
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load more time entries")
                    _uiState.value = _uiState.value.copy(
                        isLoadingMoreTimeEntries = false,
                        error = error.message ?: "Failed to load more entries",
                    )
                }
        }
    }

    @Suppress("LongMethod", "LoopWithTooManyJumpStatements")
    fun jumpToHistoryDate(date: LocalDate) {
        val organizationId = historyOrganizationId ?: return
        val memberId = historyMemberId ?: return
        if (_uiState.value.isLoadingMoreTimeEntries) return
        val loadedDates = _uiState.value.timeEntries.mapNotNull { entry ->
            IsoTimes.localDate(entry.start)
        }
        val newestLoadedDate = loadedDates.maxOrNull()
        val oldestLoadedDate = loadedDates.minOrNull()
        if (newestLoadedDate != null &&
            oldestLoadedDate != null &&
            date in oldestLoadedDate..newestLoadedDate
        ) {
            // The screen resolves an empty selected day to the nearest loaded date header.
            _uiState.value = _uiState.value.copy(historyJumpDate = date)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingMoreTimeEntries = true,
                historyJumpTarget = date,
                historyJumpProgress = 0f,
            )
            val total = _uiState.value.totalTimeEntries ?: getHistoryPageWithRateLimit(
                organizationId,
                memberId,
                limit = 1,
                offset = 0,
            ).getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    isLoadingMoreTimeEntries = false,
                    historyJumpTarget = null,
                    historyJumpProgress = null,
                    historyRateLimitWaitSeconds = null,
                    error = error.message,
                )
                return@launch
            }.meta?.total ?: 0
            var low = 0
            var high = (total - 1).coerceAtLeast(0)
            var matchOffset = 0
            var exactMatch = false
            val expectedProbes = if (total > 1) {
                kotlin.math.ceil(kotlin.math.log2(total.toDouble())).toInt()
            } else {
                1
            }
            var completedProbes = 0
            while (low <= high) {
                val middle = (low + high) ushr 1
                val probe = getHistoryPageWithRateLimit(
                    organizationId,
                    memberId,
                    limit = 1,
                    offset = middle,
                ).getOrElse { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMoreTimeEntries = false,
                        historyJumpTarget = null,
                        historyJumpProgress = null,
                        historyRateLimitWaitSeconds = null,
                        error = error.message ?: "Failed to load date",
                    )
                    return@launch
                }
                val probeDate = probe.data.firstOrNull()?.let { entry ->
                    IsoTimes.localDate(entry.start)
                } ?: break
                completedProbes++
                _uiState.value = _uiState.value.copy(
                    historyJumpProgress = (completedProbes.toFloat() / (expectedProbes + 1))
                        .coerceAtMost(HISTORY_PROGRESS_CAP),
                )
                matchOffset = middle
                when {
                    probeDate > date -> low = middle + 1
                    probeDate < date -> high = middle - 1
                    else -> {
                        exactMatch = true
                        break
                    }
                }
            }
            if (!exactMatch) matchOffset = low.coerceIn(0, (total - 1).coerceAtLeast(0))
            _uiState.value = _uiState.value.copy(historyJumpProgress = 0.92f)
            val windowStart = (matchOffset - MAX_PAGE_SIZE / 2).coerceAtLeast(0)
            val response = getHistoryPageWithRateLimit(
                organizationId,
                memberId,
                limit = MAX_PAGE_SIZE,
                offset = windowStart,
            ).getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    isLoadingMoreTimeEntries = false,
                    historyJumpTarget = null,
                    historyJumpProgress = null,
                    historyRateLimitWaitSeconds = null,
                    error = error.message,
                )
                return@launch
            }
            val tagsById = _uiState.value.tags.associateBy { it.id }
            val window = response.data.map { entry ->
                entry.copy(tags = entry.tags.map { tagsById[it.id] ?: it })
            }
            historyWindowStartOffset = windowStart
            historyOffset = windowStart + window.size
            historyLoadStage = 2
            // The jumped-to window is authoritative; keep the collector from replacing it.
            historyWindowMode = HistoryWindowMode.PAGINATED
            _uiState.value = _uiState.value.copy(
                timeEntries = window,
                totalTimeEntries = response.meta?.total ?: total,
                hasMoreTimeEntries = historyOffset < total,
                canLoadNewerHistory = windowStart > 0,
                isLoadingMoreTimeEntries = false,
                historyJumpTarget = null,
                historyJumpProgress = null,
                historyRateLimitWaitSeconds = null,
                historyJumpDate = date,
            )
        }
    }

    fun loadNewerTimeEntries() {
        val organizationId = historyOrganizationId ?: return
        val memberId = historyMemberId ?: return
        if (_uiState.value.isLoadingMoreTimeEntries || historyWindowStartOffset <= 0) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMoreTimeEntries = true)
            val newStart = (historyWindowStartOffset - MAX_PAGE_SIZE).coerceAtLeast(0)
            authRepository.getTimeEntries(
                organizationId,
                memberId,
                limit = historyWindowStartOffset - newStart,
                offset = newStart,
            ).onSuccess { response ->
                val tagsById = _uiState.value.tags.associateBy { it.id }
                val incoming = response.data.map { entry ->
                    entry.copy(tags = entry.tags.map { tagsById[it.id] ?: it })
                }
                historyWindowStartOffset = newStart
                historyWindowMode = HistoryWindowMode.PAGINATED
                _uiState.value = _uiState.value.copy(
                    timeEntries = (incoming + _uiState.value.timeEntries)
                        .distinctBy { it.id }.sortedByDescending { it.start },
                    isLoadingMoreTimeEntries = false,
                    canLoadNewerHistory = newStart > 0,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isLoadingMoreTimeEntries = false, error = error.message)
            }
        }
    }

    fun consumeHistoryJump() {
        _uiState.value = _uiState.value.copy(historyJumpDate = null)
    }

    private suspend fun getHistoryPageWithRateLimit(
        organizationId: String,
        memberId: String,
        limit: Int,
        offset: Int,
    ): Result<dev.tricked.solidverdant.data.model.TimeEntriesResponse> {
        repeat(RATE_LIMIT_RETRY_ATTEMPTS) {
            val result = authRepository.getTimeEntries(organizationId, memberId, limit, offset)
            val error = result.exceptionOrNull()
            if (error !is retrofit2.HttpException || error.code() != HTTP_TOO_MANY_REQUESTS) return result
            val waitSeconds = error.response()?.headers()?.get("Retry-After")
                ?.toIntOrNull()?.coerceIn(MIN_RETRY_AFTER_SECONDS, MAX_RETRY_AFTER_SECONDS)
                ?: DEFAULT_RETRY_AFTER_SECONDS
            _uiState.value = _uiState.value.copy(historyRateLimitWaitSeconds = waitSeconds)
            delay(waitSeconds * MILLIS_PER_SECOND)
            _uiState.value = _uiState.value.copy(historyRateLimitWaitSeconds = null)
        }
        return Result.failure(IllegalStateException("Rate limit retry exhausted"))
    }

    /**
     * Start the elapsed time timer
     */
    private fun startTimer(startTimeString: String) {
        stopTimer() // Stop any existing timer

        timerJob = viewModelScope.launch {
            try {
                // Parse the start time
                val startTime =
                    ZonedDateTime.parse(startTimeString, DateTimeFormatter.ISO_DATE_TIME)
                val startInstant = startTime.toInstant()

                while (true) {
                    val now = Instant.now()
                    // Clamp: a device clock behind the entry's start would otherwise yield a
                    // negative elapsed value and render as garbage (e.g. "-1:-5:-3").
                    val elapsed = (now.epochSecond - startInstant.epochSecond).coerceAtLeast(0)
                    _elapsedSeconds.value = elapsed
                    delay(TIMER_TICK_INTERVAL_MS) // Update every second
                }
            } catch (e: CancellationException) {
                // Expected when timer is stopped, don't log as error
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse start time or run timer")
            }
        }
    }

    /**
     * Stop the elapsed time timer
     */
    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _elapsedSeconds.value = 0
    }

    /**
     * Update editing description
     */
    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(editingDescription = description)
    }

    /**
     * Update editing project
     */
    fun updateProject(projectId: String?) {
        _uiState.value = _uiState.value.copy(
            editingProjectId = projectId,
            // Clear task if project changed
            editingTaskId = if (projectId != _uiState.value.editingProjectId) null else _uiState.value.editingTaskId,
        )
    }

    /**
     * Update editing task
     */
    fun updateTask(taskId: String?) {
        _uiState.value = _uiState.value.copy(editingTaskId = taskId)
    }

    /**
     * Update editing tags
     */
    fun updateTags(tags: List<String>) {
        _uiState.value = _uiState.value.copy(editingTags = tags)
    }

    /**
     * Update editing billable
     */
    fun updateBillable(billable: Boolean) {
        _uiState.value = _uiState.value.copy(editingBillable = billable)
    }

    /**
     * Start a new time entry with current editing state
     */
    fun startTimeEntry(organizationId: String, memberId: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Optimistic local write + outbox enqueue. The Room collector surfaces the
            // new active entry and starts the timer.
            val timeEntry = timeEntryRepository.startEntry(
                organizationId = organizationId,
                memberId = memberId,
                userId = userId,
                projectId = _uiState.value.editingProjectId,
                taskId = _uiState.value.editingTaskId,
                description = _uiState.value.editingDescription,
                tagIds = _uiState.value.editingTags,
            )
            syncTrigger.requestSync()

            // Active timers always have a foreground notification.
            val projectName = _uiState.value.projects.find { it.id == timeEntry.projectId }?.name
            val taskName = _uiState.value.tasks.find { it.id == timeEntry.taskId }?.name

            TimeTrackingNotificationService.startTracking(
                context = context,
                startTime = Instant.parse(timeEntry.start),
                projectName = projectName,
                taskName = taskName,
                description = timeEntry.description,
            )

            settingsDataStore.setWidgetTrackingState(
                isTracking = true,
                startTimeEpochMillis = Instant.parse(timeEntry.start).toEpochMilli(),
                projectName = projectName,
                taskName = taskName,
                description = timeEntry.description,
            )
            TimeTrackingWidget.requestUpdate(context)

            _uiState.value = _uiState.value.copy(isLoading = false, isTracking = true)
            Timber.d("Time entry started successfully (optimistic)")
        }
    }

    /**
     * Update the current active time entry
     */
    fun updateCurrentTimeEntry(timeEntry: TimeEntry? = null, tags: List<String>? = null) {
        val entryToUpdate = timeEntry ?: _uiState.value.currentTimeEntry
        if (entryToUpdate == null) {
            Timber.w("No active time entry to update")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val editingTags = tags ?: _uiState.value.editingTags
            val updatedEntry = entryToUpdate.copy(
                description = _uiState.value.editingDescription,
                projectId = _uiState.value.editingProjectId,
                taskId = _uiState.value.editingTaskId,
                billable = _uiState.value.editingBillable,
                tags = editingTags.map { Tag(it) },
            )

            timeEntryRepository.updateEntry(updatedEntry, editingTags)
            syncTrigger.requestSync()

            // Reassert the foreground notification with the edited details.
            if (updatedEntry.end == null) {
                val projectName = _uiState.value.projects.find { it.id == updatedEntry.projectId }?.name
                val taskName = _uiState.value.tasks.find { it.id == updatedEntry.taskId }?.name
                TimeTrackingNotificationService.startTracking(
                    context = context,
                    startTime = Instant.parse(updatedEntry.start),
                    projectName = projectName,
                    taskName = taskName,
                    description = updatedEntry.description,
                )
                settingsDataStore.setWidgetTrackingState(
                    isTracking = true,
                    startTimeEpochMillis = Instant.parse(updatedEntry.start).toEpochMilli(),
                    projectName = projectName,
                    taskName = taskName,
                    description = updatedEntry.description,
                )
                TimeTrackingWidget.requestUpdate(context)
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                editingTags = editingTags,
            )
            Timber.d("Time entry updated successfully (optimistic)")
        }
    }

    /**
     * Stop the active time entry
     */
    fun stopTimeEntry(userId: String) {
        val currentEntry = _uiState.value.currentTimeEntry

        // If paused, the entry is already stopped - just clear the paused state
        if (currentEntry == null && _uiState.value.isPaused) {
            _uiState.value = _uiState.value.copy(
                isPaused = false,
                editingDescription = "",
                editingProjectId = null,
                editingTaskId = null,
                editingTags = emptyList(),
                editingBillable = false,
            )
            viewModelScope.launch {
                updateNotificationState()
                settingsDataStore.setWidgetTrackingState(isTracking = false)
                TimeTrackingWidget.requestUpdate(context)
            }
            Timber.d("Cleared paused state")
            return
        }

        if (currentEntry == null) {
            Timber.w("No active time entry to stop")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Optimistic local stop + outbox enqueue. The collector clears the active entry.
            timeEntryRepository.stopEntry(currentEntry, userId)
            syncTrigger.requestSync()

            _uiState.value = _uiState.value.copy(
                isTracking = false,
                isPaused = false,
                currentTimeEntry = null,
                editingDescription = "",
                editingProjectId = null,
                editingTaskId = null,
                editingTags = emptyList(),
                editingBillable = false,
            )
            stopTimer()
            lastCollectedActiveId = null
            Timber.d("Time entry stopped successfully (optimistic)")

            // Update notification state (will switch to idle or hide based on settings)
            updateNotificationState()

            // Update widget state to idle
            settingsDataStore.setWidgetTrackingState(isTracking = false)
            TimeTrackingWidget.requestUpdate(context)

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * Pause the active time entry - stops it via API but keeps notification in paused state
     * preserving the project/task/description for easy resume
     */
    fun pauseTimeEntry(userId: String) {
        val currentEntry = _uiState.value.currentTimeEntry
        if (currentEntry == null) {
            Timber.w("No active time entry to pause")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Optimistic local stop + outbox enqueue; keep editing state for resume.
            timeEntryRepository.stopEntry(currentEntry, userId)
            syncTrigger.requestSync()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isTracking = false,
                isPaused = true,
                currentTimeEntry = null,
            )
            stopTimer()
            lastCollectedActiveId = null
            Timber.d("Time entry paused successfully (optimistic)")

            // Update notification to paused state
            TimeTrackingNotificationService.showPaused(context)

            // Update widget state to idle
            settingsDataStore.setWidgetTrackingState(isTracking = false)
            TimeTrackingWidget.requestUpdate(context)
        }
    }

    /**
     * Resume tracking after pause - starts a new time entry with the same project/task/description
     */
    fun resumeTimeEntry(organizationId: String, memberId: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, isPaused = false)

            val timeEntry = timeEntryRepository.startEntry(
                organizationId = organizationId,
                memberId = memberId,
                userId = userId,
                projectId = _uiState.value.editingProjectId,
                taskId = _uiState.value.editingTaskId,
                description = _uiState.value.editingDescription,
                tagIds = _uiState.value.editingTags,
            )
            syncTrigger.requestSync()

            val projectName = _uiState.value.projects.find { it.id == timeEntry.projectId }?.name
            val taskName = _uiState.value.tasks.find { it.id == timeEntry.taskId }?.name

            // Update notification to tracking state
            TimeTrackingNotificationService.startTracking(
                context = context,
                startTime = Instant.parse(timeEntry.start),
                projectName = projectName,
                taskName = taskName,
                description = timeEntry.description,
            )

            settingsDataStore.setWidgetTrackingState(
                isTracking = true,
                startTimeEpochMillis = Instant.parse(timeEntry.start).toEpochMilli(),
                projectName = projectName,
                taskName = taskName,
                description = timeEntry.description,
            )
            TimeTrackingWidget.requestUpdate(context)

            _uiState.value = _uiState.value.copy(isLoading = false, isTracking = true)
            Timber.d("Time entry resumed successfully with new entry (optimistic)")
        }
    }

    /**
     * Create a manual (already-completed) time entry without affecting the running timer.
     */
    fun createManualTimeEntry(
        organizationId: String,
        memberId: String,
        userId: String,
        description: String?,
        projectId: String?,
        taskId: String?,
        tags: List<String>,
        billable: Boolean,
        start: String,
        end: String,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            authRepository.createTimeEntry(
                organizationId = organizationId,
                memberId = memberId,
                userId = userId,
                start = start,
                end = end,
                description = description ?: "",
                projectId = projectId,
                taskId = taskId,
                tags = tags,
                billable = billable,
            )
                .onSuccess { created ->
                    // Insert into the loaded history, keeping newest-first order.
                    // Parse instead of string-sorting: starts mix "Z" and "+02:00" offsets.
                    val updatedList = (_uiState.value.timeEntries + created)
                        .sortedByDescending { java.time.OffsetDateTime.parse(it.start) }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        timeEntries = updatedList,
                    )
                    timeEntryRepository.refreshAll(organizationId, memberId)
                    Timber.d("Manual time entry created successfully")
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to create manual time entry")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to create entry",
                    )
                }
        }
    }

    /**
     * Update a past time entry
     */
    fun updatePastTimeEntry(
        timeEntry: TimeEntry,
        description: String?,
        projectId: String?,
        taskId: String?,
        tags: List<String>,
        billable: Boolean,
        start: String,
        end: String,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val updatedEntry = timeEntry.copy(
                description = description,
                projectId = projectId,
                taskId = taskId,
                billable = billable,
                start = start,
                end = end,
                tags = tags.map { Tag(it) },
            )

            // Optimistic local update + outbox enqueue; the collector refreshes the list.
            timeEntryRepository.updateEntry(updatedEntry, tags)
            syncTrigger.requestSync()

            _uiState.value = _uiState.value.copy(isLoading = false)
            Timber.d("Time entry updated successfully (optimistic)")
        }
    }

    /**
     * Roadmap #13: duplicate a completed entry, then open the copy for immediate editing. Running
     * and conflicted entries are guarded by the repository; a failure surfaces as an error message.
     */
    fun duplicateTimeEntry(entryId: String) {
        val memberId = historyMemberId ?: return
        viewModelScope.launch {
            timeEntryRepository.duplicateEntry(entryId, memberId)
                .onSuccess { created ->
                    syncTrigger.requestSync()
                    _uiState.value = _uiState.value.copy(entryToEditId = created.id)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to duplicate time entry")
                    _uiState.value = _uiState.value.copy(error = error.message ?: "Failed to duplicate entry")
                }
        }
    }

    /**
     * Roadmap #13: split a completed entry at [atIso]; on success open the new second half for
     * immediate editing. Validation (interior instant, running/conflict guards) lives in the repo.
     */
    fun splitTimeEntry(entryId: String, atIso: String) {
        val memberId = historyMemberId ?: return
        viewModelScope.launch {
            timeEntryRepository.splitEntry(entryId, atIso, memberId)
                .onSuccess { newId ->
                    syncTrigger.requestSync()
                    _uiState.value = _uiState.value.copy(entryToEditId = newId)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to split time entry")
                    _uiState.value = _uiState.value.copy(error = error.message ?: "Failed to split entry")
                }
        }
    }

    /** One-shot consume of [TrackingUiState.entryToEditId] once the UI has opened the editor. */
    fun consumeEntryToEdit() {
        if (_uiState.value.entryToEditId != null) {
            _uiState.value = _uiState.value.copy(entryToEditId = null)
        }
    }

    /**
     * Delete a time entry.
     *
     * SV-019: only the local soft-delete happens here, synchronously with no outbox op created -
     * so nothing exists yet for the sync worker to race against. The server-facing commit (DELETE
     * for a synced entry, or cancelling the START/CREATE for a never-synced one - SV-008) is
     * deferred behind the undo window in [pendingDeleteCommitJobs] and only runs if [undoDelete]
     * doesn't cancel it first.
     */
    fun deleteTimeEntry(timeEntryId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val entry = _uiState.value.timeEntries.firstOrNull { it.id == timeEntryId }
                ?: _uiState.value.currentTimeEntry?.takeIf { it.id == timeEntryId }
            if (entry == null) {
                Timber.w("No time entry found to delete: $timeEntryId")
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            // Optimistic local-only soft-delete; the collector removes it from the list. No
            // outbox op exists yet, so there is nothing here for the sync worker to act on.
            timeEntryRepository.softDeleteLocal(entry)

            pendingDeleteCommitJobs.remove(timeEntryId)?.cancel()
            pendingDeleteCommitJobs[timeEntryId] = viewModelScope.launch {
                // Give the Snackbar undo action a real cancellation window before committing.
                delay(DELETE_UNDO_WINDOW_MS)
                timeEntryRepository.commitDelete(entry)
                pendingDeleteCommitJobs.remove(timeEntryId)
                syncTrigger.requestSync()
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
            Timber.d("Time entry soft-deleted successfully (optimistic)")
        }
    }

    fun undoDelete(entry: TimeEntry) {
        viewModelScope.launch {
            // Cancel the deferred server-facing commit first: if the window hasn't closed yet,
            // this guarantees nothing was ever enqueued to the outbox for the repository undo path
            // to race against.
            pendingDeleteCommitJobs.remove(entry.id)?.cancel()
            if (!timeEntryRepository.undoDelete(entry, historyMemberId)) {
                _uiState.value = _uiState.value.copy(error = context.getString(R.string.undo_delete_too_late))
            } else {
                syncTrigger.requestSync()
            }
        }
    }

    fun retrySync() = syncTrigger.requestSync()

    fun retrySync(entryId: String) {
        viewModelScope.launch {
            if (timeEntryRepository.prepareRetry(entryId)) syncTrigger.requestSync()
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Get grouped time entries by date
     */
    fun getGroupedTimeEntries(): Map<LocalDate, List<TimeEntry>> = _uiState.value.timeEntries
        .groupBy { entry -> IsoTimes.localDate(entry.start) ?: LocalDate.now() }
        .toSortedMap(compareByDescending { it })

    override fun onCleared() {
        super.onCleared()
        loadDataJob?.cancel()
        activeEntryMonitorJob?.cancel()
        dataCollectorJob?.cancel()
        stopTimer()
    }

    private companion object {
        const val ACTIVE_ENTRY_REFRESH_INTERVAL_MS = 10_000L
        const val FOREGROUND_REFRESH_DEBOUNCE_MS = 5_000L
        const val FIRST_SCROLL_TOTAL = 150
        const val MAX_PAGE_SIZE = 500
        const val HISTORY_REFRESH_LIMIT = 250
        const val FIRST_FRAME_ENTRY_LIMIT = 30
        const val FIRST_FRAME_CACHE_DEBOUNCE_MS = 500L
        const val DELETE_UNDO_WINDOW_MS = 5_000L
    }
}

package dev.tricked.solidverdant.ui.tracking

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.AppThemeMode
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.service.TimeTrackingNotificationService
import dev.tricked.solidverdant.widget.TimeTrackingWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * UI state for tracking screen
 */
data class TrackingUiState(
    val isLoading: Boolean = false,
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val currentTimeEntry: TimeEntry? = null,
    val timeEntries: List<TimeEntry> = emptyList(),
    val projects: List<Project> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val elapsedSeconds: Long = 0,
    val error: String? = null,
    // Current entry editing state
    val editingDescription: String = "",
    val editingProjectId: String? = null,
    val editingTaskId: String? = null,
    val editingTags: List<String> = emptyList(),
    val editingBillable: Boolean = false
)

/**
 * ViewModel for time tracking operations
 */
@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    val alwaysShowNotifications = settingsDataStore.alwaysShowNotification
    val appTheme = settingsDataStore.appTheme

    private var timerJob: Job? = null
    private var loadDataJob: Job? = null
    private var loadingOrganizationId: String? = null
    private var activeEntryMonitorJob: Job? = null
    private var monitoredOrganizationId: String? = null
    private var isInitialized = false

    init {
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
    fun loadAllData(organizationId: String, memberId: String) {
        if (loadDataJob?.isActive == true && loadingOrganizationId == organizationId) {
            return
        }
        loadDataJob?.cancel()
        loadingOrganizationId = organizationId
        loadDataJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Load organization-scoped labels before the active entry so its notification
                // always has data from the correct organization.
                coroutineScope {
                    listOf(
                        async { loadTimeEntries(organizationId, memberId) },
                        async { loadProjects(organizationId) },
                        async { loadTasks(organizationId) },
                        async { loadTags(organizationId) }
                    ).awaitAll()
                }
                loadActiveTimeEntry(organizationId)
                startActiveEntryMonitoring(organizationId)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
                if (loadingOrganizationId == organizationId) {
                    loadingOrganizationId = null
                }
            }
        }
    }

    /**
     * Keep notification state in sync with timers started or stopped on another device while
     * this ViewModel is alive. Changing organizations replaces the old monitor immediately.
     */
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

    /** Resume polling, refreshing all visible data after a longer background pause. */
    fun onAppForegrounded(
        organizationId: String,
        memberId: String,
        refreshAll: Boolean
    ) {
        if (refreshAll) {
            loadAllData(organizationId, memberId)
        } else {
            startActiveEntryMonitoring(organizationId)
        }
    }

    /**
     * Load the active time entry for the current user
     */
    private suspend fun loadActiveTimeEntry(
        organizationId: String,
        onlyIfChanged: Boolean = false
    ) {
            authRepository.getActiveTimeEntry()
                .onSuccess { timeEntry ->
                    // The active-entry endpoint is account-wide. Only surface an entry for the
                    // organization currently selected in the app.
                    val currentTimeEntry = timeEntry?.takeIf {
                        it.organizationId == organizationId
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
                                description = currentTimeEntry.description
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
                        editingBillable = currentTimeEntry?.billable ?: false
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
                            description = currentTimeEntry.description
                        )
                        settingsDataStore.setWidgetTrackingState(
                            isTracking = true,
                            startTimeEpochMillis = Instant.parse(currentTimeEntry.start).toEpochMilli(),
                            projectName = projectName,
                            taskName = taskName,
                            description = currentTimeEntry.description
                        )
                    } else {
                        // Update notification and widget state for non-tracking cases
                        updateNotificationState()
                        settingsDataStore.setWidgetTrackingState(
                            isTracking = false
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
                        error = error.message ?: "Failed to load tracking state"
                    )
                    stopTimer()

                    // Mark as initialized even on failure
                    isInitialized = true
                }
    }

    /**
     * Load time entries
     */
    private suspend fun loadTimeEntries(organizationId: String, memberId: String) {
            authRepository.getTimeEntries(organizationId, memberId)
                .onSuccess { entries ->
                    _uiState.value = _uiState.value.copy(timeEntries = entries)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load time entries")
                }
    }

    /**
     * Load projects
     */
    private suspend fun loadProjects(organizationId: String) {
            authRepository.getProjects(organizationId)
                .onSuccess { projects ->
                    _uiState.value =
                        _uiState.value.copy(projects = projects.filter { !it.isArchived })
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load projects")
                }
    }

    /**
     * Load tasks
     */
    private suspend fun loadTasks(organizationId: String) {
            authRepository.getTasks(organizationId)
                .onSuccess { tasks ->
                    _uiState.value = _uiState.value.copy(tasks = tasks.filter { !it.isDone })
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load tasks")
                }
    }

    /**
     * Load tags
     */
    private suspend fun loadTags(organizationId: String) {
            authRepository.getTags(organizationId)
                .onSuccess { tags ->
                    _uiState.value = _uiState.value.copy(tags = tags)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load tags")
                }
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
                    val elapsed = now.epochSecond - startInstant.epochSecond
                    _uiState.value = _uiState.value.copy(elapsedSeconds = elapsed)
                    delay(1000) // Update every second
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
        _uiState.value = _uiState.value.copy(elapsedSeconds = 0)
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
            editingTaskId = if (projectId != _uiState.value.editingProjectId) null else _uiState.value.editingTaskId
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

            authRepository.startTimeEntry(
                organizationId = organizationId,
                memberId = memberId,
                userId = userId,
                projectId = _uiState.value.editingProjectId,
                taskId = _uiState.value.editingTaskId,
                description = _uiState.value.editingDescription
            )
                .onSuccess { timeEntry ->
                    // Active timers always have a foreground notification.
                    val projectName =
                        _uiState.value.projects.find { it.id == timeEntry.projectId }?.name
                    val taskName = _uiState.value.tasks.find { it.id == timeEntry.taskId }?.name

                    TimeTrackingNotificationService.startTracking(
                        context = context,
                        startTime = Instant.parse(timeEntry.start),
                        projectName = projectName,
                        taskName = taskName,
                        description = timeEntry.description
                    )

                    // Update widget state
                    settingsDataStore.setWidgetTrackingState(
                        isTracking = true,
                        startTimeEpochMillis = Instant.parse(timeEntry.start).toEpochMilli(),
                        projectName = projectName,
                        taskName = taskName,
                        description = timeEntry.description
                    )
                    TimeTrackingWidget.requestUpdate(context)

                    // Update the time entry with tags if needed
                    if (_uiState.value.editingTags.isNotEmpty()) {
                        updateCurrentTimeEntry(
                            organizationId,
                            timeEntry,
                            _uiState.value.editingTags
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isTracking = true,
                            currentTimeEntry = timeEntry
                        )
                        startTimer(timeEntry.start)
                        Timber.d("Time entry started successfully")
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to start time entry")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to start tracking"
                    )
                }
        }
    }

    /**
     * Update the current active time entry
     */
    fun updateCurrentTimeEntry(
        organizationId: String,
        timeEntry: TimeEntry? = null,
        tags: List<String>? = null
    ) {
        val entryToUpdate = timeEntry ?: _uiState.value.currentTimeEntry
        if (entryToUpdate == null) {
            Timber.w("No active time entry to update")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val updatedEntry = entryToUpdate.copy(
                description = _uiState.value.editingDescription,
                projectId = _uiState.value.editingProjectId,
                taskId = _uiState.value.editingTaskId,
                billable = _uiState.value.editingBillable
            )

            authRepository.updateTimeEntry(
                organizationId = organizationId,
                timeEntry = updatedEntry,
                tags = tags ?: _uiState.value.editingTags
            )
                .onSuccess { updated ->
                    // Update notification with new info if tracking is active
                    if (updated.end == null) {
                        val projectName =
                            _uiState.value.projects.find { it.id == updated.projectId }?.name
                        val taskName = _uiState.value.tasks.find { it.id == updated.taskId }?.name
                        TimeTrackingNotificationService.updateTrackingInfo(
                            context = context,
                            projectName = projectName,
                            taskName = taskName,
                            description = updated.description
                        )

                        // Update widget state
                        settingsDataStore.setWidgetTrackingState(
                            isTracking = true,
                            startTimeEpochMillis = Instant.parse(updated.start).toEpochMilli(),
                            projectName = projectName,
                            taskName = taskName,
                            description = updated.description
                        )
                        TimeTrackingWidget.requestUpdate(context)
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isTracking = true,
                        currentTimeEntry = updated,
                        editingTags = updated.tags.map { it.id }
                    )
                    if (!_uiState.value.isTracking) {
                        startTimer(updated.start)
                    }
                    Timber.d("Time entry updated successfully")
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to update time entry")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to update tracking"
                    )
                }
        }
    }

    /**
     * Stop the active time entry
     */
    fun stopTimeEntry(organizationId: String, memberId: String, userId: String) {
        val currentEntry = _uiState.value.currentTimeEntry

        // If paused, the entry is already stopped - just clear the paused state
        if (currentEntry == null && _uiState.value.isPaused) {
            _uiState.value = _uiState.value.copy(
                isPaused = false,
                editingDescription = "",
                editingProjectId = null,
                editingTaskId = null,
                editingTags = emptyList(),
                editingBillable = false
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

            authRepository.stopTimeEntry(
                organizationId = organizationId,
                timeEntryId = currentEntry.id,
                userId = userId,
                startTime = currentEntry.start
            )
                .onSuccess { timeEntry ->
                    _uiState.value = _uiState.value.copy(
                        isTracking = false,
                        isPaused = false,
                        currentTimeEntry = null,
                        editingDescription = "",
                        editingProjectId = null,
                        editingTaskId = null,
                        editingTags = emptyList(),
                        editingBillable = false
                    )
                    stopTimer()
                    Timber.d("Time entry stopped successfully")

                    // Update notification state (will switch to idle or hide based on settings)
                    updateNotificationState()

                    // Update widget state to idle
                    settingsDataStore.setWidgetTrackingState(isTracking = false)
                    TimeTrackingWidget.requestUpdate(context)

                    // Refresh only after the stop has been committed by the server.
                    loadTimeEntries(organizationId, memberId)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to stop time entry")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to stop tracking"
                    )
                }
        }
    }

    /**
     * Pause the active time entry - stops it via API but keeps notification in paused state
     * preserving the project/task/description for easy resume
     */
    fun pauseTimeEntry(organizationId: String, userId: String) {
        val currentEntry = _uiState.value.currentTimeEntry
        if (currentEntry == null) {
            Timber.w("No active time entry to pause")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            authRepository.stopTimeEntry(
                organizationId = organizationId,
                timeEntryId = currentEntry.id,
                userId = userId,
                startTime = currentEntry.start
            )
                .onSuccess {
                    // Keep the editing state (project, task, description) for resume
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isTracking = false,
                        isPaused = true,
                        currentTimeEntry = null
                    )
                    stopTimer()
                    Timber.d("Time entry paused successfully")

                    // Update notification to paused state
                    TimeTrackingNotificationService.showPaused(context)

                    // Update widget state to idle
                    settingsDataStore.setWidgetTrackingState(isTracking = false)
                    TimeTrackingWidget.requestUpdate(context)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to pause time entry")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to pause tracking"
                    )
                }
        }
    }

    /**
     * Resume tracking after pause - starts a new time entry with the same project/task/description
     */
    fun resumeTimeEntry(organizationId: String, memberId: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, isPaused = false)

            authRepository.startTimeEntry(
                organizationId = organizationId,
                memberId = memberId,
                userId = userId,
                projectId = _uiState.value.editingProjectId,
                taskId = _uiState.value.editingTaskId,
                description = _uiState.value.editingDescription
            )
                .onSuccess { timeEntry ->
                    val projectName =
                        _uiState.value.projects.find { it.id == timeEntry.projectId }?.name
                    val taskName = _uiState.value.tasks.find { it.id == timeEntry.taskId }?.name

                    // Update notification to tracking state
                    TimeTrackingNotificationService.startTracking(
                        context = context,
                        startTime = Instant.parse(timeEntry.start),
                        projectName = projectName,
                        taskName = taskName,
                        description = timeEntry.description
                    )

                    // Update widget state
                    settingsDataStore.setWidgetTrackingState(
                        isTracking = true,
                        startTimeEpochMillis = Instant.parse(timeEntry.start).toEpochMilli(),
                        projectName = projectName,
                        taskName = taskName,
                        description = timeEntry.description
                    )
                    TimeTrackingWidget.requestUpdate(context)

                    // Update the time entry with tags if needed
                    if (_uiState.value.editingTags.isNotEmpty()) {
                        updateCurrentTimeEntry(
                            organizationId,
                            timeEntry,
                            _uiState.value.editingTags
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isTracking = true,
                            currentTimeEntry = timeEntry
                        )
                        startTimer(timeEntry.start)
                        Timber.d("Time entry resumed successfully with new entry")
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to resume time entry")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isPaused = true, // Stay paused on failure
                        error = error.message ?: "Failed to resume tracking"
                    )
                }
        }
    }

    /**
     * Update a past time entry
     */
    fun updatePastTimeEntry(
        organizationId: String,
        timeEntry: TimeEntry,
        description: String?,
        projectId: String?,
        taskId: String?,
        tags: List<String>,
        billable: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val updatedEntry = timeEntry.copy(
                description = description,
                projectId = projectId,
                taskId = taskId,
                billable = billable
            )

            authRepository.updateTimeEntry(
                organizationId = organizationId,
                timeEntry = updatedEntry,
                tags = tags
            )
                .onSuccess { updated ->
                    // Update the time entry in the list
                    val updatedList = _uiState.value.timeEntries.map {
                        if (it.id == updated.id) updated else it
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        timeEntries = updatedList
                    )
                    Timber.d("Time entry updated successfully")
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to update time entry")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to update entry"
                    )
                }
        }
    }

    /**
     * Delete a time entry
     */
    fun deleteTimeEntry(organizationId: String, timeEntryId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            authRepository.deleteTimeEntry(organizationId, timeEntryId)
                .onSuccess {
                    // Remove the time entry from the list
                    val updatedList = _uiState.value.timeEntries.filter { it.id != timeEntryId }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        timeEntries = updatedList
                    )
                    Timber.d("Time entry deleted successfully")
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to delete time entry")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to delete entry"
                    )
                }
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
    fun getGroupedTimeEntries(): Map<LocalDate, List<TimeEntry>> {
        return _uiState.value.timeEntries
            .groupBy { entry ->
                try {
                    val zonedDateTime =
                        ZonedDateTime.parse(entry.start, DateTimeFormatter.ISO_DATE_TIME)
                    zonedDateTime.toLocalDate()
                } catch (e: Exception) {
                    LocalDate.now()
                }
            }
            .toSortedMap(compareByDescending { it })
    }

    override fun onCleared() {
        super.onCleared()
        loadDataJob?.cancel()
        activeEntryMonitorJob?.cancel()
        stopTimer()
    }

    private companion object {
        const val ACTIVE_ENTRY_REFRESH_INTERVAL_MS = 10_000L
    }
}

package dev.tricked.solidverdant.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    /**
     * Load all data needed for the tracking screen
     */
    fun loadAllData(organizationId: String, memberId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Load active time entry
            loadActiveTimeEntry()

            // Load time entries
            loadTimeEntries(organizationId, memberId)

            // Load projects
            loadProjects(organizationId)

            // Load tasks
            loadTasks(organizationId)

            // Load tags
            loadTags(organizationId)

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * Load the active time entry for the current user
     */
    fun loadActiveTimeEntry() {
        viewModelScope.launch {
            authRepository.getActiveTimeEntry()
                .onSuccess { timeEntry ->
                    val isTracking = timeEntry != null
                    _uiState.value = _uiState.value.copy(
                        isTracking = isTracking,
                        currentTimeEntry = timeEntry,
                        editingDescription = timeEntry?.description ?: "",
                        editingProjectId = timeEntry?.projectId,
                        editingTaskId = timeEntry?.taskId,
                        editingTags = timeEntry?.tags?.map { it.id } ?: emptyList(),
                        editingBillable = timeEntry?.billable ?: false
                    )

                    // Start timer if tracking
                    if (isTracking && timeEntry != null) {
                        startTimer(timeEntry.start)
                    } else {
                        stopTimer()
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load active time entry")
                    _uiState.value = _uiState.value.copy(
                        isTracking = false,
                        error = error.message ?: "Failed to load tracking state"
                    )
                    stopTimer()
                }
        }
    }

    /**
     * Load time entries
     */
    private fun loadTimeEntries(organizationId: String, memberId: String) {
        viewModelScope.launch {
            authRepository.getTimeEntries(organizationId, memberId)
                .onSuccess { entries ->
                    _uiState.value = _uiState.value.copy(timeEntries = entries)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load time entries")
                }
        }
    }

    /**
     * Load projects
     */
    private fun loadProjects(organizationId: String) {
        viewModelScope.launch {
            authRepository.getProjects(organizationId)
                .onSuccess { projects ->
                    _uiState.value =
                        _uiState.value.copy(projects = projects.filter { !it.isArchived })
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load projects")
                }
        }
    }

    /**
     * Load tasks
     */
    private fun loadTasks(organizationId: String) {
        viewModelScope.launch {
            authRepository.getTasks(organizationId)
                .onSuccess { tasks ->
                    _uiState.value = _uiState.value.copy(tasks = tasks.filter { !it.isDone })
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load tasks")
                }
        }
    }

    /**
     * Load tags
     */
    private fun loadTags(organizationId: String) {
        viewModelScope.launch {
            authRepository.getTags(organizationId)
                .onSuccess { tags ->
                    _uiState.value = _uiState.value.copy(tags = tags)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load tags")
                }
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
    fun stopTimeEntry(organizationId: String, userId: String) {
        val currentEntry = _uiState.value.currentTimeEntry
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
                        isLoading = false,
                        isTracking = false,
                        currentTimeEntry = null,
                        editingDescription = "",
                        editingProjectId = null,
                        editingTaskId = null,
                        editingTags = emptyList(),
                        editingBillable = false
                    )
                    stopTimer()
                    Timber.d("Time entry stopped successfully")

                    // Reload time entries to show the stopped entry
                    // Will be called from the UI after stop
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
        stopTimer()
    }
}

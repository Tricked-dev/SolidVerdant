package dev.tricked.solidverdant.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val elapsedSeconds: Long = 0,
    val error: String? = null
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
     * Load the active time entry for the current user
     */
    fun loadActiveTimeEntry() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            authRepository.getActiveTimeEntry()
                .onSuccess { timeEntry ->
                    val isTracking = timeEntry != null
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isTracking = isTracking,
                        currentTimeEntry = timeEntry
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
                        isLoading = false,
                        isTracking = false,
                        error = error.message ?: "Failed to load tracking state"
                    )
                    stopTimer()
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
     * Start a new time entry
     */
    fun startTimeEntry(organizationId: String, memberId: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            authRepository.startTimeEntry(organizationId, memberId, userId)
                .onSuccess { timeEntry ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isTracking = true,
                        currentTimeEntry = timeEntry
                    )
                    startTimer(timeEntry.start)
                    Timber.d("Time entry started successfully")
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
                        currentTimeEntry = null
                    )
                    stopTimer()
                    Timber.d("Time entry stopped successfully")
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
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
}

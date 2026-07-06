package dev.tricked.solidverdant.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.TimeEntryReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class DayBucket(
    val date: LocalDate,
    val entries: List<TimeEntry>,
    val totalSeconds: Long,
)

data class CalendarUiState(
    val visibleMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val bucketsByDate: Map<LocalDate, DayBucket> = emptyMap(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val reader: TimeEntryReader,
) : ViewModel() {

    private var organizationId: String? = null
    private var memberId: String? = null

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    fun setOrganization(organizationId: String, memberId: String = "") {
        this.organizationId = organizationId
        this.memberId = memberId
        viewModelScope.launch {
            reader.observeTimeEntries(organizationId).collect { entries ->
                val now = Instant.now()
                val buckets = entries
                    .groupBy { entryLocalDate(it) }
                    .mapValues { (date, dayEntries) ->
                        DayBucket(
                            date = date,
                            entries = dayEntries.sortedByDescending { it.start },
                            totalSeconds = dayEntries.sumOf { entryDurationSeconds(it, now) },
                        )
                    }
                _uiState.update { it.copy(bucketsByDate = buckets, isLoading = false) }
            }
        }
        loadVisibleMonth()
    }

    fun selectDate(date: LocalDate) = _uiState.update {
        it.copy(selectedDate = date, visibleMonth = YearMonth.from(date))
    }

    fun nextMonth() = moveMonth(1)

    fun previousMonth() = moveMonth(-1)

    private fun moveMonth(delta: Long) = _uiState.update { state ->
        val month = state.visibleMonth.plusMonths(delta)
        val preferredDay = state.selectedDate.dayOfMonth.coerceAtMost(month.lengthOfMonth())
        val preferredDate = month.atDay(preferredDay)
        val dateWithEntries = state.bucketsByDate.keys
            .filter { YearMonth.from(it) == month }
            .minByOrNull { kotlin.math.abs(it.dayOfMonth - preferredDay) }
        state.copy(visibleMonth = month, selectedDate = dateWithEntries ?: preferredDate)
    }.also { loadVisibleMonth() }

    private fun loadVisibleMonth() {
        val org = organizationId ?: return
        val member = memberId ?: return
        val month = _uiState.value.visibleMonth
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            reader.loadMonth(org, member, month)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun entriesForSelectedDay(): List<TimeEntry> =
        _uiState.value.bucketsByDate[_uiState.value.selectedDate]?.entries ?: emptyList()
}

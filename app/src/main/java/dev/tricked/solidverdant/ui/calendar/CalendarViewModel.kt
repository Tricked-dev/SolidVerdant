package dev.tricked.solidverdant.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.calendar.CalendarEventSource
import dev.tricked.solidverdant.data.calendar.CalendarOverlaySettings
import dev.tricked.solidverdant.data.calendar.DeviceCalendar
import dev.tricked.solidverdant.data.calendar.DeviceCalendarEvent
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.TimeEntryReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

/** Which calendar layout the user is currently viewing. */
enum class CalendarViewMode { MONTH, WEEK, DAY }

data class DayBucket(
    val date: LocalDate,
    val entries: List<TimeEntry>,
    val totalSeconds: Long,
)

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.WEEK,
    val visibleMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    /** Anchor day for the week/day page; [visibleDays] is derived from it. */
    val weekAnchor: LocalDate = LocalDate.now(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    /** Columns shown in WEEK mode (7, or 3 on narrow screens). DAY mode always shows 1. */
    val dayCount: Int = 7,
    val visibleDays: List<LocalDate> = emptyList(),
    val bucketsByDate: Map<LocalDate, DayBucket> = emptyMap(),
    val isLoading: Boolean = true,
    // --- Device-calendar overlay ---
    val overlayEnabled: Boolean = false,
    val hasCalendarPermission: Boolean = false,
    /** True once the user has been shown the system permission dialog at least once this session. */
    val permissionRequested: Boolean = false,
    val availableCalendars: List<DeviceCalendar> = emptyList(),
    val selectedCalendarIds: Set<String> = emptySet(),
    val overlayEvents: List<DeviceCalendarEvent> = emptyList(),
    val overlayLoading: Boolean = false,
    val overlayError: Boolean = false,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val reader: TimeEntryReader,
    private val eventSource: CalendarEventSource,
    private val overlaySettings: CalendarOverlaySettings,
) : ViewModel() {

    private var organizationId: String? = null
    private var memberId: String? = null
    private var entriesJob: Job? = null

    /** Locale's first day of week so the grid honours regional week-start conventions. */
    private val weekStart: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    private val _uiState = MutableStateFlow(CalendarUiState(weekStart = weekStart))
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    // Overlay query inputs kept as flows so event queries react without recollecting time entries.
    private val _viewMode = MutableStateFlow(CalendarViewMode.WEEK)
    private val _weekAnchor = MutableStateFlow(LocalDate.now())
    private val _dayCount = MutableStateFlow(7)
    private val _hasPermission = MutableStateFlow(false)
    private val _retry = MutableStateFlow(0)

    private data class OverlayInputs(
        val enabled: Boolean,
        val ids: Set<String>,
        val hasPermission: Boolean,
        val range: Pair<Long, Long>?,
        val retry: Int,
    )

    private data class OverlayResult(
        val events: List<DeviceCalendarEvent>,
        val loading: Boolean,
        val error: Boolean,
    )

    init {
        recomputeVisibleDays()

        // Mirror persisted overlay preferences into the UI state.
        viewModelScope.launch {
            overlaySettings.calendarOverlayEnabled.collect { enabled ->
                _uiState.update { it.copy(overlayEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            overlaySettings.selectedCalendarIds.collect { ids ->
                _uiState.update { it.copy(selectedCalendarIds = ids) }
            }
        }
        // Load the picker's calendar list only while the overlay is on and permission is granted.
        viewModelScope.launch {
            combine(overlaySettings.calendarOverlayEnabled, _hasPermission) { enabled, perm ->
                enabled && perm
            }
                .distinctUntilChanged()
                .collect { ready ->
                    if (ready) refreshAvailableCalendars()
                    else _uiState.update { it.copy(availableCalendars = emptyList()) }
                }
        }
        // Query events reactively whenever the page, selection, or permission changes.
        viewModelScope.launch { observeOverlayEvents() }
    }

    fun setOrganization(organizationId: String, memberId: String = "") {
        if (this.organizationId == organizationId && this.memberId == memberId && entriesJob?.isActive == true) {
            return
        }
        this.organizationId = organizationId
        this.memberId = memberId
        entriesJob?.cancel()
        entriesJob = viewModelScope.launch {
            reader.observeTimeEntries(organizationId).collect { entries ->
                // Aggregate off the main thread: a large month can hold thousands of entries and
                // groupBy/sumOf here would otherwise jank or ANR the UI thread.
                val buckets = withContext(Dispatchers.Default) {
                    val now = Instant.now()
                    entries
                        // Skip entries whose start cannot be parsed instead of bucketing them
                        // onto today, which would corrupt the current day's total.
                        .mapNotNull { entry -> entryLocalDate(entry)?.let { it to entry } }
                        .groupBy({ it.first }, { it.second })
                        .mapValues { (date, dayEntries) ->
                            DayBucket(
                                date = date,
                                entries = dayEntries.sortedByDescending { it.start },
                                totalSeconds = dayEntries.sumOf { entryDurationSeconds(it, now) },
                            )
                        }
                }
                _uiState.update { it.copy(bucketsByDate = buckets, isLoading = false) }
            }
        }
        loadForVisibleDays()
    }

    // --- View-mode / navigation ---------------------------------------------------------------

    fun setViewMode(mode: CalendarViewMode) {
        if (_viewMode.value == mode) return
        _viewMode.value = mode
        _uiState.update { it.copy(viewMode = mode) }
        recomputeVisibleDays()
        loadForVisibleDays()
    }

    /** Called by the UI as the available width changes so WEEK mode can drop to a 3-day layout. */
    fun setVisibleDayCount(count: Int) {
        val safe = count.coerceIn(1, 7)
        if (_dayCount.value == safe) return
        _dayCount.value = safe
        _uiState.update { it.copy(dayCount = safe) }
        recomputeVisibleDays()
        loadForVisibleDays()
    }

    fun pageForward() = page(1)

    fun pageBackward() = page(-1)

    private fun page(direction: Int) {
        val state = _uiState.value
        val newAnchor = when (state.viewMode) {
            CalendarViewMode.MONTH -> return
            CalendarViewMode.WEEK -> pageAnchor(state.weekAnchor, weekStart, state.dayCount, direction)
            CalendarViewMode.DAY -> state.weekAnchor.plusDays(direction.toLong())
        }
        _weekAnchor.value = newAnchor
        val newDays = visibleDaysFor(state.viewMode, newAnchor, state.dayCount)
        val newSelected = if (state.selectedDate in newDays) state.selectedDate
        else newDays.firstOrNull() ?: newAnchor
        _uiState.update {
            it.copy(
                weekAnchor = newAnchor,
                visibleDays = newDays,
                selectedDate = newSelected,
                visibleMonth = YearMonth.from(newAnchor),
            )
        }
        loadForVisibleDays()
    }

    fun jumpToToday() {
        val today = LocalDate.now()
        _weekAnchor.value = today
        _uiState.update {
            it.copy(
                weekAnchor = today,
                selectedDate = today,
                visibleMonth = YearMonth.from(today),
                visibleDays = visibleDaysFor(it.viewMode, today, it.dayCount),
            )
        }
        loadForVisibleDays()
    }

    fun selectDate(date: LocalDate) {
        _weekAnchor.value = date
        _uiState.update {
            it.copy(
                selectedDate = date,
                weekAnchor = date,
                visibleMonth = YearMonth.from(date),
                visibleDays = visibleDaysFor(it.viewMode, date, it.dayCount),
            )
        }
        loadForVisibleDays()
    }

    fun nextMonth() = moveMonth(1)

    fun previousMonth() = moveMonth(-1)

    private fun moveMonth(delta: Long) {
        _uiState.update { state ->
            val month = state.visibleMonth.plusMonths(delta)
            val preferredDay = state.selectedDate.dayOfMonth.coerceAtMost(month.lengthOfMonth())
            val preferredDate = month.atDay(preferredDay)
            val dateWithEntries = state.bucketsByDate.keys
                .filter { YearMonth.from(it) == month }
                .minByOrNull { kotlin.math.abs(it.dayOfMonth - preferredDay) }
            val selected = dateWithEntries ?: preferredDate
            state.copy(visibleMonth = month, selectedDate = selected, weekAnchor = selected)
        }
        _weekAnchor.value = _uiState.value.weekAnchor
        loadForVisibleDays()
    }

    // --- Overlay controls ---------------------------------------------------------------------

    /** Called by the UI after checking/requesting the READ_CALENDAR runtime permission. */
    fun onCalendarPermissionChanged(granted: Boolean) {
        if (_hasPermission.value == granted && _uiState.value.hasCalendarPermission == granted) return
        _hasPermission.value = granted
        _uiState.update { it.copy(hasCalendarPermission = granted) }
    }

    fun onPermissionRequested() {
        _uiState.update { it.copy(permissionRequested = true) }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch { overlaySettings.setCalendarOverlayEnabled(enabled) }
    }

    fun toggleCalendarSelected(id: String) {
        val current = _uiState.value.selectedCalendarIds
        val next = if (id in current) current - id else current + id
        viewModelScope.launch { overlaySettings.setSelectedCalendarIds(next) }
    }

    fun retryOverlay() {
        _retry.update { it + 1 }
    }

    private suspend fun refreshAvailableCalendars() {
        val calendars = runCatching { eventSource.queryCalendars() }.getOrDefault(emptyList())
        _uiState.update { it.copy(availableCalendars = calendars) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeOverlayEvents() {
        val rangeFlow: Flow<Pair<Long, Long>?> =
            combine(_viewMode, _weekAnchor, _dayCount) { mode, anchor, dc -> rangeFor(mode, anchor, dc) }
        combine(
            overlaySettings.calendarOverlayEnabled,
            overlaySettings.selectedCalendarIds,
            _hasPermission,
            rangeFlow,
            _retry,
        ) { enabled, ids, perm, range, retry -> OverlayInputs(enabled, ids, perm, range, retry) }
            .distinctUntilChanged()
            .flatMapLatest { input ->
                if (!input.enabled || !input.hasPermission || input.ids.isEmpty() || input.range == null) {
                    flowOf(OverlayResult(emptyList(), loading = false, error = false))
                } else {
                    flow {
                        emit(OverlayResult(emptyList(), loading = true, error = false))
                        val result = runCatching {
                            eventSource.queryEvents(input.ids, input.range.first, input.range.second)
                        }
                        emit(
                            result.fold(
                                onSuccess = { OverlayResult(it, loading = false, error = false) },
                                onFailure = { OverlayResult(emptyList(), loading = false, error = true) },
                            ),
                        )
                    }
                }
            }
            .collect { result ->
                _uiState.update {
                    it.copy(
                        overlayEvents = result.events,
                        overlayLoading = result.loading,
                        overlayError = result.error,
                    )
                }
            }
    }

    // --- Derivation helpers -------------------------------------------------------------------

    private fun recomputeVisibleDays() {
        val s = _uiState.value
        _uiState.update { it.copy(visibleDays = visibleDaysFor(s.viewMode, s.weekAnchor, s.dayCount)) }
    }

    private fun visibleDaysFor(mode: CalendarViewMode, anchor: LocalDate, dayCount: Int): List<LocalDate> =
        when (mode) {
            CalendarViewMode.MONTH -> emptyList()
            CalendarViewMode.WEEK -> visibleCalendarDays(anchor, weekStart, dayCount)
            CalendarViewMode.DAY -> listOf(anchor)
        }

    /** Epoch-milli half-open range covering the visible page, or null for MONTH mode. */
    private fun rangeFor(mode: CalendarViewMode, anchor: LocalDate, dayCount: Int): Pair<Long, Long>? {
        val days = visibleDaysFor(mode, anchor, dayCount)
        if (days.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val start = days.first().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = days.last().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    private fun loadForVisibleDays() {
        val org = organizationId ?: return
        val member = memberId ?: return
        val state = _uiState.value
        val months = when (state.viewMode) {
            CalendarViewMode.MONTH -> listOf(state.visibleMonth)
            else -> {
                val days = state.visibleDays
                if (days.isEmpty()) listOf(state.visibleMonth)
                // A week can straddle two months; load both so no column is missing entries.
                else listOf(days.first(), days.last()).map { YearMonth.from(it) }.distinct()
            }
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            months.forEach { reader.loadMonth(org, member, it) }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun entriesForSelectedDay(): List<TimeEntry> =
        _uiState.value.bucketsByDate[_uiState.value.selectedDate]?.entries ?: emptyList()
}

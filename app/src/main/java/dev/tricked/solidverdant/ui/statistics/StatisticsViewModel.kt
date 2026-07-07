package dev.tricked.solidverdant.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val range: StatRange = StatRange.ThisWeek,
    val summary: StatisticsSummary = EMPTY_SUMMARY,
    val isEmpty: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
) {
    companion object {
        val EMPTY_SUMMARY = StatisticsSummary(0, 0, 0, 0, 0, emptyList(), emptyList())
    }
}

/**
 * ViewModel for the Statistics screen.
 *
 * Room supplies an immediate offline result while a bounded, server-filtered request refreshes
 * the selected range. A failed refresh never turns cached data into a false empty state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val timeEntryRepository: TimeEntryRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val rangeFlow = MutableStateFlow<StatRange>(StatRange.ThisWeek)
    private val refreshTrigger = MutableStateFlow(0)

    private data class RemoteEntries(
        val entries: List<TimeEntry>? = null,
        val isLoading: Boolean = false,
        val failed: Boolean = false,
    )

    private fun loadRemoteEntries(
        organizationId: String,
        memberId: String,
        range: ClosedRange<LocalDate>,
    ): Flow<RemoteEntries> =
        flow {
            emit(RemoteEntries(isLoading = true))
            val entries = mutableListOf<TimeEntry>()
            val start = range.start.atStartOfDay(zone).toInstant().toString()
            val end = range.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toString()
            var offset = 0
            do {
                val page = authRepository.getTimeEntries(
                    organizationId, memberId, limit = 500, offset = offset,
                    start = start, end = end,
                ).getOrThrow()
                entries += page.data
                offset += page.data.size
                val total = page.meta?.total ?: page.data.size
            } while (page.data.size == 500 && offset < total)
            emit(RemoteEntries(entries = entries))
        }.catch {
            emit(RemoteEntries(failed = true))
        }.flowOn(Dispatchers.IO)

    private val membershipFlow = flow { emit(authRepository.getCurrentMembership()) }.flowOn(Dispatchers.IO)

    val uiState: StateFlow<StatisticsUiState> =
        membershipFlow.flatMapLatest { membership ->
            if (membership == null) {
                flowOf(StatisticsUiState(isLoading = false, isEmpty = true))
            } else {
                combine(rangeFlow, refreshTrigger) { range, _ -> range }.flatMapLatest { range ->
                    val resolved = range.resolve(LocalDate.now(zone))
                    combine(
                        timeEntryRepository.observeTimeEntries(membership.organizationId),
                        timeEntryRepository.observeProjects(membership.organizationId),
                        loadRemoteEntries(membership.organizationId, membership.id, resolved),
                    ) { cachedEntries, projects, remote ->
                    val entries = remote.entries ?: cachedEntries
                    val summary = withContext(Dispatchers.Default) {
                        StatisticsAggregator.compute(
                            entries = entries,
                            projects = projects,
                            rangeStart = resolved.start,
                            rangeEnd = resolved.endInclusive,
                            zone = zone,
                            granularity = granularityFor(resolved),
                        )
                    }
                    StatisticsUiState(
                        isLoading = false,
                        isRefreshing = remote.isLoading,
                        refreshFailed = remote.failed,
                        range = range,
                        summary = summary,
                        isEmpty = summary.entryCount == 0,
                    )
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatisticsUiState(),
        )

    fun setRange(range: StatRange) {
        rangeFlow.value = range
    }

    /** Re-fetches the selected range while keeping cached results visible. */
    fun refresh() {
        refreshTrigger.value += 1
    }
}

package dev.tricked.solidverdant.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
) {
    companion object {
        val EMPTY_SUMMARY = StatisticsSummary(0, 0, 0, 0, 0, emptyList(), emptyList())
    }
}

/**
 * ViewModel for the Statistics screen.
 *
 * Feature #6 (`TimeEntryRepository`) is not present, so the read source of truth uses the
 * documented fallback: the two `observe*` flows are thin adapters backed by one-shot
 * `AuthRepository` calls. When Feature #6 lands, swap these two adapters for the repository's
 * `observeTimeEntries` / `observeProjects` Flows — nothing else in this file changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val rangeFlow = MutableStateFlow<StatRange>(StatRange.ThisWeek)
    private val refreshTrigger = MutableStateFlow(0)

    // --- Feature #6 fallback adapter ---
    private fun observeTimeEntries(organizationId: String, memberId: String): Flow<List<TimeEntry>> =
        flow {
            emit(
                authRepository.getTimeEntries(organizationId, memberId, limit = 500)
                    .getOrNull()?.data ?: emptyList(),
            )
        }.flowOn(Dispatchers.IO)

    private fun observeProjects(organizationId: String): Flow<List<Project>> =
        flow {
            emit(authRepository.getProjects(organizationId).getOrDefault(emptyList()))
        }.flowOn(Dispatchers.IO)
    // --- end fallback adapter ---

    private val membershipFlow = refreshTrigger.flatMapLatest {
        flow { emit(authRepository.getCurrentMembership()) }.flowOn(Dispatchers.IO)
    }

    val uiState: StateFlow<StatisticsUiState> =
        membershipFlow.flatMapLatest { membership ->
            if (membership == null) {
                flowOf(StatisticsUiState(isLoading = false, isEmpty = true))
            } else {
                combine(
                    observeTimeEntries(membership.organizationId, membership.id),
                    observeProjects(membership.organizationId),
                    rangeFlow,
                ) { entries, projects, range ->
                    val today = LocalDate.now(zone)
                    val resolved = range.resolve(today)
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
                        range = range,
                        summary = summary,
                        isEmpty = summary.entryCount == 0,
                    )
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

    /** Re-fetches entries/projects from the API (fallback replacement for repository re-emit). */
    fun refresh() {
        refreshTrigger.value += 1
    }
}

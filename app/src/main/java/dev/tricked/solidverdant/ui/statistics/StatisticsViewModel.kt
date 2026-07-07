package dev.tricked.solidverdant.ui.statistics

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.export.CsvExporter
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val range: StatRange = StatRange.ThisWeek,
    val filters: StatFilters = StatFilters(),
    val catalog: StatCatalog = StatCatalog(),
    val summary: StatisticsSummary = EMPTY_SUMMARY,
    val comparison: PeriodComparison? = null,
    val filteredEntries: List<TimeEntry> = emptyList(),
    val rangeStart: LocalDate? = null,
    val rangeEnd: LocalDate? = null,
    val granularity: TrendGranularity = TrendGranularity.DAY,
    val isEmpty: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
) {
    companion object {
        val EMPTY_SUMMARY = StatisticsSummary(0, 0, 0, 0, 0, emptyList(), emptyList())
    }
}

/** One-shot state for the CSV export/share flow, consumed by the screen once handled. */
sealed interface ExportState {
    data object Idle : ExportState
    data object Running : ExportState
    data class Ready(val uri: Uri, val fileName: String) : ExportState
    data object Empty : ExportState
    data object Error : ExportState
}

/** What the user tapped to open a drill-down list. */
sealed interface DrillDownTarget {
    /** A donut slice / project legend row; [projectId] null is the "no project" bucket. */
    data class ProjectSlice(
        val projectId: String?,
        val projectName: String?,
        val colorHex: String,
    ) : DrillDownTarget

    /** A trend bar covering the inclusive [start]..[end] window it represents. */
    data class TrendSlice(
        val label: String,
        val start: LocalDate,
        val end: LocalDate,
    ) : DrillDownTarget
}

/** Contents of the drill-down bottom sheet for the currently tapped [target]. */
data class DrillDownUiState(
    val target: DrillDownTarget,
    val isLoading: Boolean = true,
    val rows: List<DrillDownRow> = emptyList(),
    val totalSeconds: Long = 0L,
)

/**
 * ViewModel for the Statistics screen.
 *
 * Room supplies an immediate offline result while a bounded, server-filtered request refreshes the
 * selected range. Filters are applied locally to the fetched/cached entries and drive every chart,
 * KPI, the previous-period comparison and the CSV export. A failed refresh never turns cached data
 * into a false empty state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val timeEntryRepository: TimeEntryRepository,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val rangeFlow = MutableStateFlow<StatRange>(StatRange.ThisWeek)
    private val filtersFlow = MutableStateFlow(StatFilters())
    private val refreshTrigger = MutableStateFlow(0)

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _drillDown = MutableStateFlow<DrillDownUiState?>(null)
    val drillDown: StateFlow<DrillDownUiState?> = _drillDown.asStateFlow()

    /** Latest inputs needed to build a CSV of the current filtered range, refreshed by [uiState]. */
    @Volatile
    private var exportInput: ExportInput? = null

    private data class ExportInput(
        val entries: List<TimeEntry>,
        val catalog: StatCatalog,
        val rangeStart: LocalDate,
        val rangeEnd: LocalDate,
        val organizationName: String,
    )

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
            val pageSize = 500
            var offset = 0
            while (true) {
                val page = authRepository.getTimeEntries(
                    organizationId, memberId, limit = pageSize, offset = offset,
                    start = start, end = end,
                ).getOrThrow()
                entries += page.data
                offset += page.data.size
                if (!shouldFetchNextPage(pageSize, page.data.size, offset, page.meta?.total)) break
            }
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
                val orgId = membership.organizationId
                val orgName = membership.organization.name
                val catalogFlow = combine(
                    timeEntryRepository.observeProjects(orgId),
                    timeEntryRepository.observeClients(orgId),
                    timeEntryRepository.observeTasks(orgId),
                    timeEntryRepository.observeTags(orgId),
                ) { projects, clients, tasks, tags -> StatCatalog(projects, clients, tasks, tags) }

                // Only the range and an explicit refresh trigger a server fetch; filters are applied
                // locally to the already-fetched entries, so toggling a filter never re-downloads the
                // range. combine memoizes on exactly these inputs.
                combine(rangeFlow, refreshTrigger) { range, _ -> range }
                    .flatMapLatest { range ->
                        val resolved = range.resolve(LocalDate.now(zone))
                        val previous = previousPeriod(resolved)
                        val fetchRange = previous.start..resolved.endInclusive
                        combine(
                            timeEntryRepository.observeTimeEntries(orgId),
                            catalogFlow,
                            loadRemoteEntries(orgId, membership.id, fetchRange),
                            filtersFlow,
                        ) { cachedEntries, catalog, remote, filters ->
                            val entries = remote.entries ?: cachedEntries
                            val computed = withContext(Dispatchers.Default) {
                                val filtered = StatisticsAggregator.applyFilters(entries, catalog.projects, filters)
                                val current = StatisticsAggregator.compute(
                                    entries = filtered,
                                    projects = catalog.projects,
                                    rangeStart = resolved.start,
                                    rangeEnd = resolved.endInclusive,
                                    zone = zone,
                                    granularity = granularityFor(resolved),
                                )
                                val prior = StatisticsAggregator.compute(
                                    entries = filtered,
                                    projects = catalog.projects,
                                    rangeStart = previous.start,
                                    rangeEnd = previous.endInclusive,
                                    zone = zone,
                                    granularity = granularityFor(previous),
                                )
                                Triple(current, computeComparison(current, prior, previous), filtered)
                            }
                            val (summary, comparison, filtered) = computed
                            val exportEntries = withContext(Dispatchers.Default) {
                                filtered.filter {
                                    StatisticsAggregator.clippedSeconds(
                                        it, zone, resolved.start, resolved.endInclusive,
                                    ) != null
                                }
                            }
                            exportInput = ExportInput(
                                entries = exportEntries,
                                catalog = catalog,
                                rangeStart = resolved.start,
                                rangeEnd = resolved.endInclusive,
                                organizationName = orgName,
                            )
                            StatisticsUiState(
                                isLoading = false,
                                isRefreshing = remote.isLoading,
                                refreshFailed = remote.failed,
                                range = range,
                                filters = filters,
                                catalog = catalog,
                                summary = summary,
                                comparison = comparison,
                                filteredEntries = filtered,
                                rangeStart = resolved.start,
                                rangeEnd = resolved.endInclusive,
                                granularity = granularityFor(resolved),
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

    fun setFilters(filters: StatFilters) {
        filtersFlow.value = filters
    }

    fun clearFilters() {
        filtersFlow.value = StatFilters()
    }

    /** Re-fetches the selected range while keeping cached results visible. */
    fun refresh() {
        refreshTrigger.value += 1
    }

    /** The current filter's [zone], exposed so drill-down clipping matches the aggregated view. */
    fun zone(): ZoneId = zone

    /** Opens the drill-down list for a tapped project donut slice / legend row. */
    fun openProjectDrillDown(projectId: String?, projectName: String?, colorHex: String) {
        openDrillDown(DrillDownTarget.ProjectSlice(projectId, projectName, colorHex))
    }

    /** Opens the drill-down list for a tapped trend bar covering [start]..[end] (inclusive). */
    fun openTrendDrillDown(label: String, start: LocalDate, end: LocalDate) {
        openDrillDown(DrillDownTarget.TrendSlice(label, start, end))
    }

    fun closeDrillDown() {
        _drillDown.value = null
    }

    /**
     * Computes the drill-down rows for [target] off the main thread from the already-filtered,
     * already-fetched entries in the current [uiState]. A late result is dropped if the user has
     * since closed the sheet or opened a different slice, so a slow computation can't overwrite the
     * visible selection. Does nothing when no range has resolved yet.
     */
    private fun openDrillDown(target: DrillDownTarget) {
        val snapshot = uiState.value
        val rangeStart = snapshot.rangeStart ?: return
        val rangeEnd = snapshot.rangeEnd ?: return
        _drillDown.value = DrillDownUiState(target = target, isLoading = true)
        viewModelScope.launch {
            val rows = withContext(Dispatchers.Default) {
                when (target) {
                    is DrillDownTarget.ProjectSlice -> StatisticsAggregator.drillDown(
                        entries = snapshot.filteredEntries,
                        projects = snapshot.catalog.projects,
                        tasks = snapshot.catalog.tasks,
                        zone = zone,
                        selStart = rangeStart,
                        selEnd = rangeEnd,
                        matchProject = true,
                        projectId = target.projectId,
                    )
                    is DrillDownTarget.TrendSlice -> {
                        val selStart = if (target.start.isAfter(rangeStart)) target.start else rangeStart
                        val selEnd = if (target.end.isBefore(rangeEnd)) target.end else rangeEnd
                        StatisticsAggregator.drillDown(
                            entries = snapshot.filteredEntries,
                            projects = snapshot.catalog.projects,
                            tasks = snapshot.catalog.tasks,
                            zone = zone,
                            selStart = selStart,
                            selEnd = selEnd,
                            matchProject = false,
                            projectId = null,
                        )
                    }
                }
            }
            if (_drillDown.value?.target == target) {
                _drillDown.value = DrillDownUiState(
                    target = target,
                    isLoading = false,
                    rows = rows,
                    totalSeconds = rows.sumOf { it.seconds },
                )
            }
        }
    }

    /**
     * Builds a CSV of the currently filtered range off the main thread, writes it to the export
     * cache and surfaces a shareable URI. Repeated taps while running are ignored; an empty result
     * yields [ExportState.Empty] rather than an empty file.
     */
    fun export() {
        if (_exportState.value == ExportState.Running) return
        val input = exportInput
        if (input == null || input.entries.isEmpty()) {
            _exportState.value = ExportState.Empty
            return
        }
        _exportState.value = ExportState.Running
        viewModelScope.launch {
            try {
                val csv = withContext(Dispatchers.Default) {
                    csvExporter.formatCsv(
                        entries = input.entries,
                        projects = input.catalog.projects,
                        clients = input.catalog.clients,
                        tasks = input.catalog.tasks,
                        tags = input.catalog.tags,
                        zone = zone,
                        organizationName = input.organizationName,
                    )
                }
                val baseName = exportBaseName(input.rangeStart, input.rangeEnd)
                val uri = csvExporter.writeToCache(csv, baseName)
                _exportState.value = ExportState.Ready(uri, "$baseName.csv")
            } catch (t: Throwable) {
                // Never log entry contents; the message alone is safe.
                Timber.e(t, "CSV export failed")
                _exportState.value = ExportState.Error
            }
        }
    }

    fun onExportHandled() {
        _exportState.value = ExportState.Idle
    }

    private fun exportBaseName(start: LocalDate, end: LocalDate): String {
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
        return "solidverdant-timeentries-${start.format(fmt)}-${end.format(fmt)}"
    }
}

/**
 * Whether another page must be fetched after receiving one of [lastPageSize] entries.
 *
 * A full page (== [pageSize]) means the server may have more rows: when the total is unknown
 * (meta null) that alone triggers the next request; when the total is known we continue only while
 * [offsetAfterPage] has not reached it. A short or empty page always stops the loop. Extracted as a
 * pure function so the previous under-fetch (defaulting an unknown total to the page size, which
 * made `offset < total` false right after the first full page) is directly testable.
 */
internal fun shouldFetchNextPage(
    pageSize: Int,
    lastPageSize: Int,
    offsetAfterPage: Int,
    total: Int?,
): Boolean = lastPageSize == pageSize && (total == null || offsetAfterPage < total)

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.reminder.currentOrganizationIdOrNull
import dev.tricked.solidverdant.sync.SyncScheduler
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Drives the guided end-of-day review (gap analysis #18). It derives the day's facts and the list
 * of corrections entirely from cached Room data, so it works offline, and exposes actions that
 * prioritise corrections over analytics: stop / adjust / keep a running timer, retry a failed sync,
 * and assign a project to uncategorized time.
 *
 * The organization is seeded from the offline auth cache so the parameterless pane works without
 * threading it in; [setOrganization] is available for hosts that want to react to org switches.
 */
@HiltViewModel
class ReviewDayViewModel @Inject constructor(
    private val repository: TimeEntryRepository,
    private val settings: SettingsDataStore,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val organizationId = MutableStateFlow(settings.currentOrganizationIdOrNull())
    private val handledIds = MutableStateFlow<Set<String>>(emptySet())

    private val _message = MutableStateFlow<Int?>(null)

    /** Transient confirmation / error message (a string resource id), consumed by the UI. */
    val message: StateFlow<Int?> = _message.asStateFlow()

    fun setOrganization(id: String?) {
        if (id != organizationId.value) {
            organizationId.value = id
            handledIds.value = emptySet()
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ReviewDayUiState> = organizationId
        .flatMapLatest { orgId ->
            if (orgId.isNullOrBlank()) {
                flowOf(
                    ReviewDayUiState(
                        loading = false,
                        hasOrganization = false,
                        dateEpochDay = LocalDate.now(zone).toEpochDay(),
                    ),
                )
            } else {
                combine(
                    repository.observeTimeEntries(orgId),
                    repository.observeProjects(orgId),
                    repository.observeSyncOperations(orgId),
                    handledIds,
                ) { entries, projects, syncOps, handled ->
                    buildState(entries, projects, syncOps, handled)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ReviewDayUiState(loading = true))

    private fun buildState(
        entries: List<TimeEntry>,
        projects: List<dev.tricked.solidverdant.data.model.Project>,
        syncOps: List<TimeEntryRepository.SyncOperation>,
        handled: Set<String>,
    ): ReviewDayUiState {
        val nowInstant = Instant.ofEpochMilli(clock.nowMs())
        val today = nowInstant.atZone(zone).toLocalDate()
        val nowSec = nowInstant.epochSecond

        val todays = entries.filter { parseInstant(it.start)?.atZone(zone)?.toLocalDate() == today }

        var total = 0L
        var billable = 0L
        val intervals = ArrayList<LongRange>(todays.size)
        todays.forEach { e ->
            val startSec = parseInstant(e.start)?.epochSecond ?: return@forEach
            val endSec = e.end?.let { parseInstant(it)?.epochSecond } ?: nowSec
            val duration = (endSec - startSec).coerceAtLeast(0L)
            total += duration
            if (e.billable) billable += duration
            if (endSec >= startSec) intervals += startSec..endSec
        }

        val runningEntry = entries.firstOrNull { it.end == null }
        val uncategorized = todays.filter { it.end != null && it.projectId.isNullOrBlank() }
        val failed = syncOps.filter { it.status == TimeEntryRepository.EntrySyncStatus.FAILED }
            .distinctBy { it.entryId }

        val items = buildList {
            runningEntry?.let {
                add(
                    ReviewItem(
                        id = "running:${it.id}",
                        type = ReviewItemType.RUNNING_TIMER,
                        entryId = it.id,
                        description = it.description,
                        startIso = it.start,
                    ),
                )
            }
            failed.forEach { op ->
                add(
                    ReviewItem(
                        id = "sync:${op.entryId}",
                        type = ReviewItemType.FAILED_SYNC,
                        entryId = op.entryId,
                        detail = op.error,
                    ),
                )
            }
            uncategorized.forEach { e ->
                add(
                    ReviewItem(
                        id = "uncat:${e.id}",
                        type = ReviewItemType.UNCATEGORIZED,
                        entryId = e.id,
                        description = e.description,
                        startIso = e.start,
                        endIso = e.end,
                    ),
                )
            }
        }

        return ReviewDayUiState(
            loading = false,
            hasOrganization = true,
            dateEpochDay = today.toEpochDay(),
            totalTrackedSeconds = total,
            billableSeconds = billable,
            entryCount = todays.size,
            largestGapSeconds = largestGap(intervals),
            uncategorizedCount = uncategorized.size,
            failedSyncCount = failed.size,
            items = items,
            handledIds = handled,
            runningEntry = runningEntry,
            uncategorizedById = uncategorized.associateBy { it.id },
            projects = projects
                .filter { !it.isArchived }
                .map { ReviewProject(it.id, it.name, it.color) },
        )
    }

    // ---- Actions ----

    /** Stop the still-running timer now (ends the entry at the current time). */
    fun stopRunningTimer() = viewModelScope.launch {
        val entry = uiState.value.runningEntry ?: return@launch
        val userId = settings.getCachedAuth()?.user?.id
        if (userId == null) {
            _message.value = R.string.review_msg_action_failed
            return@launch
        }
        runCatching { repository.stopEntry(entry, userId) }
            .onSuccess {
                syncScheduler.requestSync()
                markHandled("running:${entry.id}")
                _message.value = R.string.review_msg_timer_stopped
            }
            .onFailure { _message.value = R.string.review_msg_action_failed }
    }

    /** Leave the timer running and move past this step. */
    fun keepRunning() {
        val entry = uiState.value.runningEntry ?: return
        markHandled("running:${entry.id}")
    }

    /**
     * Adjust the running entry's end to today at [hour]:[minute] local time. The end must be after
     * the start and no later than now; otherwise a message is shown and nothing changes.
     */
    fun adjustEndTime(hour: Int, minute: Int) = viewModelScope.launch {
        val entry = uiState.value.runningEntry ?: return@launch
        val startInstant = parseInstant(entry.start)
        if (startInstant == null) {
            _message.value = R.string.review_msg_action_failed
            return@launch
        }
        val now = Instant.ofEpochMilli(clock.nowMs())
        val endInstant = now.atZone(zone).toLocalDate().atTime(hour, minute).atZone(zone).toInstant()
        if (!endInstant.isAfter(startInstant) || endInstant.isAfter(now)) {
            _message.value = R.string.review_msg_invalid_end
            return@launch
        }
        runCatching {
            repository.updateEntry(entry.copy(end = formatIso(endInstant)), entry.tags.map { it.id })
        }.onSuccess {
            syncScheduler.requestSync()
            markHandled("running:${entry.id}")
            _message.value = R.string.review_msg_end_adjusted
        }.onFailure { _message.value = R.string.review_msg_action_failed }
    }

    /** Queue a retry for a change that failed to sync. */
    fun retryFailedSync(item: ReviewItem) = viewModelScope.launch {
        runCatching { repository.prepareRetry(item.entryId) }
            .onSuccess { reset ->
                if (reset) syncScheduler.requestSync()
                markHandled(item.id)
                _message.value = R.string.review_msg_retry_queued
            }
            .onFailure { _message.value = R.string.review_msg_action_failed }
    }

    /** Assign [projectId] to the uncategorized entry behind [item]. */
    fun assignProject(item: ReviewItem, projectId: String) = viewModelScope.launch {
        val entry = uiState.value.uncategorizedById[item.entryId] ?: return@launch
        runCatching {
            repository.updateEntry(entry.copy(projectId = projectId), entry.tags.map { it.id })
        }.onSuccess {
            syncScheduler.requestSync()
            markHandled(item.id)
            _message.value = R.string.review_msg_project_assigned
        }.onFailure { _message.value = R.string.review_msg_action_failed }
    }

    /**
     * Acknowledge the current item as intentional and move on without changing it.
     *
     * SV-029: for a failed-sync item this also discards the underlying dead-lettered outbox
     * operation, so it stops permanently reappearing in Track's Sync center with only a
     * re-failing Retry - "keep as is" here means "stop trying to sync this change", not merely
     * "advance the review wizard".
     */
    fun keepAsIs(item: ReviewItem) {
        if (item.type == ReviewItemType.FAILED_SYNC) {
            viewModelScope.launch {
                runCatching { repository.discardFailedSync(item.entryId) }
                    .onFailure { _message.value = R.string.review_msg_action_failed }
            }
        }
        markHandled(item.id)
    }

    /** Restart the review from the first item. */
    fun reviewAgain() {
        handledIds.value = emptySet()
    }

    private fun markHandled(id: String) {
        handledIds.update { it + id }
    }

    private fun parseInstant(iso: String): Instant? = runCatching {
        OffsetDateTime.parse(iso).toInstant()
    }.recoverCatching {
        Instant.parse(iso)
    }.getOrNull()

    private fun formatIso(instant: Instant): String = ISO.format(instant.atOffset(ZoneOffset.UTC))

    companion object {
        private val ISO: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

        /**
         * Largest untracked gap (seconds) between merged intervals within the worked span — the
         * "missing periods" fact from gap analysis #18. Returns 0 when fewer than two intervals.
         */
        fun largestGap(intervals: List<LongRange>): Long {
            if (intervals.size < 2) return 0L
            val sorted = intervals.sortedBy { it.first }
            var maxGap = 0L
            var cursor = sorted.first().last
            for (i in 1 until sorted.size) {
                val range = sorted[i]
                if (range.first > cursor) {
                    maxGap = maxOf(maxGap, range.first - cursor)
                }
                if (range.last > cursor) cursor = range.last
            }
            return maxGap
        }
    }
}

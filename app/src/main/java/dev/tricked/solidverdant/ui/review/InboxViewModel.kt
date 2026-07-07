/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.db.InboxDismissalDao
import dev.tricked.solidverdant.data.local.db.InboxDismissalEntity
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.domain.inbox.InboxAnalyzer
import dev.tricked.solidverdant.domain.inbox.InboxIssue
import dev.tricked.solidverdant.domain.inbox.InboxSettings
import dev.tricked.solidverdant.domain.inbox.InboxSettingsDataStore
import dev.tricked.solidverdant.sync.SyncTrigger
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Drives the Time Inbox pane (gap analysis #16/#17). It projects the Room-backed time entries plus
 * the local inbox configuration through [InboxAnalyzer] into an ordered list of checks, tracks
 * transient action/refresh state, and applies quick-fixes back through the same repository the rest
 * of the app uses (so writes are optimistic + outbox-backed where the underlying method supports it).
 *
 * The organization is resolved from the reactive current-membership id plus the cached auth snapshot
 * rather than a caller-supplied parameter, because the Review panes are hosted parameterless.
 */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val timeEntryRepository: TimeEntryRepository,
    private val authRepository: AuthRepository,
    private val authDataStore: AuthDataStore,
    private val settingsDataStore: SettingsDataStore,
    private val inboxSettingsDataStore: InboxSettingsDataStore,
    private val dismissalDao: InboxDismissalDao,
    private val syncTrigger: SyncTrigger,
    private val clock: Clock,
) : ViewModel() {

    private data class OrgContext(val organizationId: String, val memberId: String, val userId: String, val preventOverlap: Boolean)

    private data class InboxData(
        val entries: List<TimeEntry>,
        val projects: List<Project>,
        val tasks: List<Task>,
        val tags: List<Tag>,
        val settings: InboxSettings,
        val longTimerHours: Int,
        val dismissals: List<InboxDismissalEntity>,
    )

    private val zone: ZoneId = ZoneId.systemDefault()
    private val retentionMs = TimeUnit.DAYS.toMillis(InboxAnalyzer.DISMISSAL_RETENTION_DAYS)

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    @Volatile
    private var context: OrgContext? = null
    private var didInitialRefresh = false

    private val orgContext = authDataStore.currentMembershipId.map { selectedId ->
        val cached = settingsDataStore.getCachedAuth() ?: return@map null
        val membership = cached.memberships.firstOrNull { it.id == (selectedId ?: cached.currentMembershipId) }
            ?: cached.memberships.firstOrNull()
            ?: return@map null
        OrgContext(
            organizationId = membership.organizationId,
            memberId = membership.id,
            userId = cached.user.id,
            preventOverlap = membership.organization.preventOverlappingTimeEntries,
        )
    }.distinctUntilChanged()

    init {
        viewModelScope.launch { observeInbox() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeInbox() {
        orgContext.flatMapLatest { ctx ->
            context = ctx
            if (ctx == null) {
                flowOf<Pair<OrgContext?, InboxData?>>(null to null)
            } else {
                maybeInitialRefresh(ctx)
                combine(
                    timeEntryRepository.observeTimeEntries(ctx.organizationId),
                    timeEntryRepository.observeProjects(ctx.organizationId),
                    timeEntryRepository.observeTasks(ctx.organizationId),
                    timeEntryRepository.observeTags(ctx.organizationId),
                    combine(
                        inboxSettingsDataStore.settings,
                        settingsDataStore.longTimerHours,
                        dismissalDao.observeDismissals(ctx.organizationId),
                    ) { settings, longTimerHours, dismissals ->
                        Triple(settings, longTimerHours, dismissals)
                    },
                ) { entries, projects, tasks, tags, config ->
                    val (settings, longTimerHours, dismissals) = config
                    ctx to InboxData(
                        entries = entries,
                        projects = projects.filterNot { it.isArchived },
                        tasks = tasks.filterNot { it.isDone },
                        tags = tags,
                        settings = settings,
                        longTimerHours = longTimerHours,
                        dismissals = dismissals,
                    )
                }
            }
        }.collect { (ctx, data) ->
            if (ctx == null || data == null) {
                _uiState.update {
                    it.copy(isLoading = false, organizationId = null, issues = emptyList(), hasEntries = false)
                }
                return@collect
            }
            val (now, config, issues) = withContext(Dispatchers.Default) {
                val now = clock.nowMs()
                val config = data.settings.toConfig(data.longTimerHours)
                val activeDismissed = data.dismissals
                    .filter { now - it.dismissedAtMs <= retentionMs }
                    .map { it.issueKey }
                    .toSet()
                Triple(now, config, InboxAnalyzer.analyze(data.entries, config, activeDismissed, now, zone))
            }
            pruneExpiredDismissals(data.dismissals, now)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    organizationId = ctx.organizationId,
                    issues = issues,
                    hasEntries = data.entries.isNotEmpty(),
                    config = config,
                    preventOverlap = ctx.preventOverlap,
                    projects = data.projects,
                    tasks = data.tasks,
                    tags = data.tags,
                )
            }
        }
    }

    private fun maybeInitialRefresh(ctx: OrgContext) {
        if (didInitialRefresh) return
        didInitialRefresh = true
        refresh(ctx)
    }

    /** Best-effort background refresh; failure keeps cached results and shows the stale banner. */
    fun refresh() {
        refresh(context ?: return)
    }

    private fun refresh(ctx: OrgContext) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, refreshError = false) }
            val result = timeEntryRepository.refreshAll(ctx.organizationId, ctx.memberId)
            _uiState.update { it.copy(isRefreshing = false, refreshError = result.isFailure) }
        }
    }

    /** Persist a dismissal for [issue]; the analyzer immediately drops it from the list. */
    fun dismiss(issue: InboxIssue) {
        val ctx = context ?: return
        viewModelScope.launch {
            dismissalDao.upsert(
                InboxDismissalEntity(
                    issueKey = issue.key,
                    organizationId = ctx.organizationId,
                    dismissedAtMs = clock.nowMs(),
                ),
            )
            _uiState.update { it.copy(pendingUndoKey = issue.key) }
        }
    }

    /** Undo the most recent dismissal (before its retention silently expires). */
    fun undoDismiss(key: String) {
        viewModelScope.launch {
            dismissalDao.deleteByKey(key)
            _uiState.update { if (it.pendingUndoKey == key) it.copy(pendingUndoKey = null) else it }
        }
    }

    fun consumeUndo() {
        _uiState.update { it.copy(pendingUndoKey = null) }
    }

    fun consumeActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    fun consumeRefreshError() {
        _uiState.update { it.copy(refreshError = false) }
    }

    /**
     * Apply an edit to an existing entry (missing metadata / long / overlap quick-fix). Optimistic
     * local write + outbox enqueue via the shared repository, so it works offline.
     */
    fun resolveEntryEdit(
        entry: TimeEntry,
        description: String?,
        projectId: String?,
        taskId: String?,
        tags: List<String>,
        billable: Boolean,
        start: String,
        end: String,
    ) {
        viewModelScope.launch {
            val updated = entry.copy(
                description = description,
                projectId = projectId,
                taskId = taskId,
                billable = billable,
                start = start,
                end = end,
                tags = tags.map { Tag(it) },
            )
            runCatching {
                timeEntryRepository.updateEntry(updated, tags)
                syncTrigger.requestSync()
            }.onFailure {
                Timber.w(it, "Failed to resolve inbox entry edit")
                _uiState.update { s -> s.copy(actionError = InboxActionError.RESOLVE_FAILED) }
            }
        }
    }

    /**
     * Create a completed entry to fill a gap. Mirrors the manual-create path (network-backed), so it
     * requires connectivity; failure is surfaced without losing the inbox list.
     */
    fun fillGap(
        description: String?,
        projectId: String?,
        taskId: String?,
        tags: List<String>,
        billable: Boolean,
        start: String,
        end: String,
    ) {
        val ctx = context ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, actionError = null) }
            authRepository.createTimeEntry(
                organizationId = ctx.organizationId,
                memberId = ctx.memberId,
                userId = ctx.userId,
                start = start,
                end = end,
                description = description ?: "",
                projectId = projectId,
                taskId = taskId,
                tags = tags,
                billable = billable,
            ).onSuccess {
                timeEntryRepository.refreshAll(ctx.organizationId, ctx.memberId)
                _uiState.update { it.copy(isRefreshing = false) }
            }.onFailure {
                Timber.w(it, "Failed to fill gap")
                _uiState.update { s -> s.copy(isRefreshing = false, actionError = InboxActionError.CREATE_FAILED) }
            }
        }
    }

    /** A blank, completed entry seeded with the gap window for the reused edit dialog. */
    fun blankEntryFor(startIso: String, endIso: String): TimeEntry? {
        val ctx = context ?: return null
        return TimeEntry(
            id = "local-inbox-${java.util.UUID.randomUUID()}",
            description = null,
            userId = ctx.userId,
            start = startIso,
            end = endIso,
            taskId = null,
            projectId = null,
            tags = emptyList(),
            billable = false,
            organizationId = ctx.organizationId,
        )
    }

    // ---- Config editing ----

    fun setCheckEnabled(check: InboxSettingsDataStore.InboxCheck, enabled: Boolean) {
        viewModelScope.launch { inboxSettingsDataStore.setCheckEnabled(check, enabled) }
    }

    fun setWorkDays(days: Set<DayOfWeek>) {
        viewModelScope.launch { inboxSettingsDataStore.setWorkDays(days) }
    }

    fun setWorkWindow(startMinute: Int, endMinute: Int) {
        viewModelScope.launch {
            if (startMinute in 0..1440 && endMinute in 0..1440 && endMinute > startMinute) {
                inboxSettingsDataStore.setWorkWindow(startMinute, endMinute)
            }
        }
    }

    fun setMinGapMinutes(minutes: Int) {
        viewModelScope.launch {
            if (minutes in 1..24 * 60) inboxSettingsDataStore.setMinGapMinutes(minutes)
        }
    }

    fun setMaxDurationHours(hours: Int) {
        viewModelScope.launch {
            if (hours in 1..24) settingsDataStore.setLongTimerHours(hours)
        }
    }

    private fun pruneExpiredDismissals(dismissals: List<InboxDismissalEntity>, now: Long) {
        val expired = dismissals.filter { now - it.dismissedAtMs > retentionMs }
        if (expired.isEmpty()) return
        viewModelScope.launch {
            expired.forEach { runCatching { dismissalDao.deleteByKey(it.issueKey) } }
        }
    }
}

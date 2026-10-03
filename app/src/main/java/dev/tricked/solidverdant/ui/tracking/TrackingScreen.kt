/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.data.repository.EntryTemplate
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.domain.time.formatTimeEntryInstant
import dev.tricked.solidverdant.domain.time.isCompletedTimeEntry
import dev.tricked.solidverdant.domain.time.isRunningTimeEntry
import dev.tricked.solidverdant.domain.time.isWorkTimeEntry
import dev.tricked.solidverdant.domain.time.parseTimeEntryInstant
import dev.tricked.solidverdant.domain.time.timeEntryLocalDaySlices
import dev.tricked.solidverdant.service.TimeTrackingNotificationService
import dev.tricked.solidverdant.ui.components.AppSheet
import dev.tricked.solidverdant.ui.components.AppTimePickerDialog
import dev.tricked.solidverdant.ui.components.ConfirmDialog
import dev.tricked.solidverdant.ui.components.ConsumeVerticalOverscroll
import dev.tricked.solidverdant.ui.components.DateRangePickerDialog
import dev.tricked.solidverdant.ui.components.DestructiveActionRow
import dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags
import dev.tricked.solidverdant.ui.components.EmptyState
import dev.tricked.solidverdant.ui.components.EntryDatePickerDialog
import dev.tricked.solidverdant.ui.components.EntryDescriptionField
import dev.tricked.solidverdant.ui.components.EntryDurationRow
import dev.tricked.solidverdant.ui.components.EntrySheetHeader
import dev.tricked.solidverdant.ui.components.EntryTimeRow
import dev.tricked.solidverdant.ui.components.FilterOption
import dev.tricked.solidverdant.ui.components.FilterRow
import dev.tricked.solidverdant.ui.components.GroupedDivider
import dev.tricked.solidverdant.ui.components.GroupedRow
import dev.tricked.solidverdant.ui.components.GroupedSection
import dev.tricked.solidverdant.ui.components.GroupedSwitchRow
import dev.tricked.solidverdant.ui.components.OptionPickerDialog
import dev.tricked.solidverdant.ui.components.SegmentedControl
import dev.tricked.solidverdant.ui.components.SelectorStyle
import dev.tricked.solidverdant.ui.components.SingleSelectFilterPicker
import dev.tricked.solidverdant.ui.components.TagsSelector
import dev.tricked.solidverdant.ui.components.retimedEnd
import dev.tricked.solidverdant.ui.localization.appLocale
import dev.tricked.solidverdant.ui.navigation.MainTopBar
import dev.tricked.solidverdant.ui.templates.FavoriteTemplatesRow
import dev.tricked.solidverdant.ui.templates.ManageTemplatesViewModel
import dev.tricked.solidverdant.ui.templates.TemplateDraft
import dev.tricked.solidverdant.ui.templates.TemplateResolver
import dev.tricked.solidverdant.ui.templates.templateDisplayLabel
import dev.tricked.solidverdant.ui.theme.Dimens
import dev.tricked.solidverdant.ui.theme.syncFailed
import dev.tricked.solidverdant.ui.theme.syncPending
import dev.tricked.solidverdant.util.NotificationPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import dev.tricked.solidverdant.ui.components.ProjectTaskDropdown as SharedProjectTaskDropdown

/**
 * Tracking screen displaying current time tracking state and history
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@SuppressLint("InlinedApi")
@Composable
fun TrackingScreen(
    user: User?,
    currentMembership: Membership?,
    uiState: TrackingUiState,
    elapsedSeconds: StateFlow<Long> = MutableStateFlow(uiState.elapsedSeconds),
    autoClearEntryFieldsAfterStop: Boolean,
    longTimerHours: Int,
    editActiveEntryRequested: Boolean,
    onEditActiveEntryConsumed: () -> Unit,
    onRefresh: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onPauseTracking: () -> Unit,
    onResumeTracking: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onProjectChange: (String?) -> Unit,
    onTaskChange: (String?) -> Unit,
    onResetEntryFields: () -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onBillableChange: (Boolean) -> Unit,
    onUpdatePastEntry: (TimeEntry, String?, String?, String?, List<String>, Boolean, String, String?) -> Unit,
    onCreateEntry: (String?, String?, String?, List<String>, Boolean, String, String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onDuplicateEntry: (String) -> Unit,
    onSplitEntry: (String, String) -> Unit,
    onEntryToEditConsumed: () -> Unit,
    onUndoDelete: (TimeEntry) -> Unit,
    onRetrySync: () -> Unit,
    onRetrySyncEntry: (String) -> Unit,
    onOpenSyncCenter: () -> Unit,
    onLoadMoreEntries: () -> Unit,
    onLoadNewerEntries: () -> Unit,
    onJumpToDate: (LocalDate) -> Unit,
    onHistoryJumpConsumed: () -> Unit,
    onClearError: () -> Unit = {},
    // The start sheet's draft, read only by its form so typing does not recompose the screen.
    entryDraft: () -> EntryDraft = { uiState.entryDraft() },
    // Pause or Stop tapped in the running entry's details, with that sheet's pending fields.
    onStopTrackingWithEdits: (RunningEntryEdits) -> Unit = { onStopTracking() },
    onPauseTrackingWithEdits: (RunningEntryEdits) -> Unit = { onPauseTracking() },
    // A history entry's play button: starts it, first stopping a running or ending a paused timer.
    // Without it the button is offered only while no timer runs.
    onContinueEntry: ((TimeEntry) -> Unit)? = null,
    // Favorites & templates (gap analysis #1, #9). The template ViewModel resolves the current
    // organization itself; its catalogue includes archived/done items so availability can be shown.
    templateViewModel: ManageTemplatesViewModel = hiltViewModel(),
) {
    var showEditDialog by remember { mutableStateOf<TimeEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var hasUserScrolledHistory by remember { mutableStateOf(false) }
    var calendarInitialDate by remember { mutableStateOf<LocalDate?>(null) }
    var historyFilter by remember { mutableStateOf(HistoryFilter()) }
    // Search lives behind the header's search button, not in the history list.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filterOptionsOpen by remember { mutableStateOf(false) }
    var deletedEntries by remember { mutableStateOf<List<TimeEntry>>(emptyList()) }
    // Entries waiting for the delete confirmation: one entry, or a whole stack.
    var pendingDelete by remember { mutableStateOf<List<TimeEntry>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }
    var longTimerSnoozedUntil by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val templatesSavedMessage = stringResource(R.string.templates_saved)
    val entryDeletedMessage = stringResource(R.string.entry_deleted)
    val conflictEditLockedMessage = stringResource(R.string.sync_conflict_edit_locked)
    val noTimerRunningMessage = stringResource(R.string.edit_active_entry_no_timer)
    val undoLabel = stringResource(R.string.undo)
    val scope = rememberCoroutineScope()
    val historyListState = rememberLazyListState()
    // Read only where the clock is drawn, so the per-second tick redraws just that text.
    val elapsed = elapsedSeconds.collectAsState()
    val readElapsed: () -> Long = { elapsed.value }
    val routineSyncInProgress = uiState.isLoading ||
        uiState.isRefreshing ||
        uiState.syncOperations.any { operation ->
            operation.status == TimeEntryRepository.EntrySyncStatus.PENDING ||
                operation.status == TimeEntryRepository.EntrySyncStatus.RETRYING
        }

    val templateState by templateViewModel.uiState.collectAsState()
    val onSaveTemplateFromForm: (TemplateDraft) -> Unit = { draft ->
        templateViewModel.saveNewTemplate(draft)
        scope.launch { snackbarHostState.showSnackbar(templatesSavedMessage) }
    }

    LaunchedEffect(editActiveEntryRequested, uiState.currentTimeEntry, uiState.hasLoadedTimeEntries) {
        if (!editActiveEntryRequested) return@LaunchedEffect
        val entry = uiState.currentTimeEntry
        if (entry == null) {
            // The timer stopped before the request (the notification's "Adjust end time") was
            // handled. Drop it once the timer state is known, or it would open the next timer.
            if (uiState.hasLoadedTimeEntries) {
                onEditActiveEntryConsumed()
                snackbarHostState.showSnackbar(noTimerRunningMessage, withDismissAction = true)
            }
            return@LaunchedEffect
        }
        onEditActiveEntryConsumed()
        if (entry.id in uiState.conflictedEntryIds) {
            snackbarHostState.showSnackbar(conflictEditLockedMessage, withDismissAction = true)
        } else {
            showEditDialog = entry
        }
    }

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, withDismissAction = true)
        onClearError()
    }

    // Roadmap #13: after a duplicate/split the VM emits the new entry's id; open it for editing
    // once it surfaces in the observed list, then consume the one-shot signal. A copy that does not
    // show up soon (its id changed on sync, or it lies outside the loaded window) is given up rather
    // than opened later on its own; leaving Time Tracker gives it up too.
    val currentTimeEntries by rememberUpdatedState(uiState.timeEntries)
    val currentConflictedIds by rememberUpdatedState(uiState.conflictedEntryIds)
    val consumeEntryToEdit by rememberUpdatedState(onEntryToEditConsumed)
    LaunchedEffect(uiState.entryToEditId) {
        val id = uiState.entryToEditId ?: return@LaunchedEffect
        val entry = withTimeoutOrNull(ENTRY_TO_EDIT_WAIT_MS) {
            snapshotFlow { currentTimeEntries.firstOrNull { it.id == id } }.filterNotNull().first()
        }
        consumeEntryToEdit()
        when {
            entry == null -> Unit
            entry.id in currentConflictedIds -> snackbarHostState.showSnackbar(conflictEditLockedMessage, withDismissAction = true)
            else -> showEditDialog = entry
        }
    }
    DisposableEffect(Unit) {
        onDispose { consumeEntryToEdit() }
    }

    LaunchedEffect(historyFilter.startDate) {
        historyFilter.startDate?.let(onJumpToDate)
    }

    LaunchedEffect(deletedEntries) {
        val entries = deletedEntries.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = entryDeletedMessage,
            actionLabel = undoLabel,
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) entries.forEach(onUndoDelete)
        deletedEntries = emptyList()
    }
    val historyScrollConnection = remember(onLoadMoreEntries) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    hasUserScrolledHistory = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(historyListState, uiState.timeEntries.size, uiState.hasMoreTimeEntries) {
        snapshotFlow {
            val info = historyListState.layoutInfo
            hasUserScrolledHistory &&
                info.totalItemsCount > 0 &&
                (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >=
                info.totalItemsCount - HISTORY_PREFETCH_ITEMS
        }.filter { it }.collect { onLoadMoreEntries() }
    }
    LaunchedEffect(
        currentMembership?.organizationId,
        uiState.hasLoadedTimeEntries,
        uiState.hasMoreTimeEntries,
        uiState.timeEntries.size,
    ) {
        if (uiState.hasLoadedTimeEntries &&
            uiState.hasMoreTimeEntries &&
            uiState.timeEntries.size <= HISTORY_INITIAL_PREFETCH_MAX_ENTRIES
        ) {
            onLoadMoreEntries()
        }
    }
    LaunchedEffect(
        historyListState,
        uiState.timeEntries.size,
        uiState.canLoadNewerHistory,
        uiState.historyJumpDate,
    ) {
        // A jump publishes its window before the list scrolls to the day; until then the list
        // still sits at the top and would ask for the newer page straight away.
        if (uiState.historyJumpDate != null) return@LaunchedEffect
        snapshotFlow {
            hasUserScrolledHistory &&
                uiState.canLoadNewerHistory &&
                (
                    historyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
                        ?: Int.MAX_VALUE
                    ) <= HISTORY_PREFETCH_ITEMS
        }.filter { it }.collect { onLoadNewerEntries() }
    }

    // Permission launcher for notification permission (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        // Permission result is handled, no action needed
    }

    // SV-014: Do not auto-request POST_NOTIFICATIONS when tracking starts — that force-prompts a
    // system dialog over the UI (breaks E2E robots, poor UX). The permission is instead requested
    // from explicit user actions: the "Enable notifications" app-bar action below, and the
    // "Always show notifications" toggle opt-in (see onCheckedChange on that Switch). The
    // persistent notification still shows normally if permission is already granted.

    val timerActive = uiState.isTracking || uiState.isPaused
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var showStartTimerSheet by rememberSaveable { mutableStateOf(false) }
    // Starting from any surface (sheet, favourite, history play button, notification) closes the
    // start controls; the running timer then docks at the bottom.
    LaunchedEffect(timerActive) {
        if (timerActive) {
            fabExpanded = false
            showStartTimerSheet = false
        }
    }
    BackHandler(enabled = fabExpanded) { fabExpanded = false }
    // History is newest first, so the last described entry is near the top: stop at the first.
    val serverLastEntry = remember(uiState.timeEntries) {
        uiState.timeEntries.firstOrNull { isCompletedTimeEntry(it) && !it.description.isNullOrBlank() }
    }
    val lastEntry = remember(serverLastEntry, uiState.cachedContinueEntry, currentMembership) {
        serverLastEntry ?: uiState.cachedContinueEntry?.takeIf {
            it.organizationId == currentMembership?.organizationId
        }
    }
    val continueEntry: (TimeEntry) -> Unit = { entry ->
        onDescriptionChange(entry.description ?: "")
        onProjectChange(entry.projectId)
        onTaskChange(entry.taskId)
        onTagsChange(entry.tags.map { it.id })
        onBillableChange(entry.billable)
        onStartTracking()
    }
    val showConflictLocked: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar(conflictEditLockedMessage, withDismissAction = true) }
    }
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TimeTrackerTopBar(
                    searchOpen = searchOpen,
                    onSearch = { searchOpen = true },
                    syncing = routineSyncInProgress,
                    onRefresh = onRefresh,
                    onRequestNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !NotificationPermissionHelper.hasNotificationPermission(context)
                    ) {
                        { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    } else {
                        null
                    },
                )
                if (searchOpen) {
                    HistorySearchBar(
                        filter = historyFilter,
                        onChange = { historyFilter = it },
                        onOpenOptions = { filterOptionsOpen = true },
                        onClose = {
                            searchOpen = false
                            historyFilter = HistoryFilter()
                        },
                    )
                }
            }
        },
        bottomBar = {
            if (timerActive) {
                ActiveTimerBar(
                    uiState = uiState,
                    elapsedSeconds = readElapsed,
                    onStop = onStopTracking,
                    onPause = onPauseTracking,
                    onResume = onResumeTracking,
                    onEditActiveEntry = {
                        uiState.currentTimeEntry?.let { entry ->
                            if (entry.id in uiState.conflictedEntryIds) showConflictLocked() else showEditDialog = entry
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            TimerFab(
                timerActive = timerActive,
                expanded = fabExpanded,
                onExpandedChange = { fabExpanded = it },
                onStartTimer = { showStartTimerSheet = true },
                onAddManual = { showAddDialog = true },
                // Without the docked timer the button sits directly above the system navigation bar.
                modifier = if (timerActive) Modifier else Modifier.navigationBarsPadding(),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(paddingValues),
        ) {
            // The sync-status search option is the only part of the list that depends on the outbox.
            val filterSyncOperations = uiState.syncOperations.takeIf { historyFilter.syncStatus != null }
            val historySnapshot by produceState(
                initialValue = HistorySnapshot.Empty,
                uiState.timeEntries,
                uiState.projects,
                uiState.tasks,
                uiState.clients,
                historyFilter,
                filterSyncOperations,
                uiState.zone,
                uiState.firstDayOfWeek,
            ) {
                val entries = uiState.timeEntries
                val filter = historyFilter
                value = withContext(Dispatchers.Default) {
                    val filtered = EntryTrustRules.filter(
                        entries = entries,
                        filter = filter,
                        projects = uiState.projects,
                        tasks = uiState.tasks,
                        clients = uiState.clients,
                        syncOperations = filterSyncOperations.orEmpty(),
                        zone = uiState.zone,
                    )
                    val now = Instant.now()
                    // Running entries are left to the docked timer, except when searching for them.
                    val items = buildHistoryListItems(
                        days = groupEntriesByLocalDay(filtered, uiState.zone, now, includeRunning = filter.runningOnly),
                        firstDayOfWeek = uiState.firstDayOfWeek,
                        today = LocalDate.now(uiState.zone),
                        zone = uiState.zone,
                        now = now,
                        includeRunning = filter.runningOnly,
                    )
                    HistorySnapshot(items = items, entries = entries, filter = filter)
                }
            }
            val historyListItems = historySnapshot.items
            // Built off the main thread, the list can briefly lag the entries it is shown with.
            val historyIsCurrent = historySnapshot.entries == uiState.timeEntries && historySnapshot.filter == historyFilter
            // The Review checks, shown on each entry's card.
            val reviewIssues by produceState(emptyMap<String, Set<EntryReviewIssue>>(), uiState.timeEntries, longTimerHours) {
                value = withContext(Dispatchers.Default) {
                    EntryTrustRules.reviewIssues(uiState.timeEntries, Duration.ofHours(longTimerHours.toLong()))
                }
            }
            val historyProjectsById = remember(uiState.projects) { uiState.projects.associateBy { it.id } }
            val historyTasksById = remember(uiState.tasks) { uiState.tasks.associateBy { it.id } }
            val historyClientsById = remember(uiState.clients) { uiState.clients.associateBy { it.id } }
            val visibleSyncOperations = remember(uiState.syncOperations, uiState.syncStatusVisible) {
                if (uiState.syncStatusVisible) uiState.syncOperations else emptyList()
            }
            val syncStatusByEntryId = remember(visibleSyncOperations) {
                worstSyncStatusByEntryId(visibleSyncOperations)
            }
            val showSyncCenter = uiState.syncStatusVisible && uiState.syncOperations.isNotEmpty()
            val onHistoryEdit = remember(uiState.conflictedEntryIds) {
                { entry: TimeEntry ->
                    if (entry.id in uiState.conflictedEntryIds) showConflictLocked() else showEditDialog = entry
                }
            }
            val requestDelete = remember(uiState.conflictedEntryIds) {
                { entries: List<TimeEntry> ->
                    if (entries.any { it.id in uiState.conflictedEntryIds }) showConflictLocked() else pendingDelete = entries
                }
            }
            val onHistoryDelete = remember(requestDelete) { { entry: TimeEntry -> requestDelete(listOf(entry)) } }
            val onHistoryDateClick = remember<(LocalDate) -> Unit> { { date -> calendarInitialDate = date } }

            LaunchedEffect(uiState.historyJumpDate, historySnapshot) {
                val target = uiState.historyJumpDate ?: return@LaunchedEffect
                val historyIndex = historyJumpHeaderIndex(target, historySnapshot.items, historySnapshot.entries, uiState.timeEntries)
                    ?: return@LaunchedEffect
                if (historyIndex >= 0) {
                    // The long-timer warning row, then the sync card when shown.
                    val leadingItemCount = 1 + (if (showSyncCenter) 1 else 0)
                    historyListState.scrollToItem(leadingItemCount + historyIndex)
                }
                onHistoryJumpConsumed()
            }

            // Grouped sections carry their own horizontal inset.
            val sectionInset = Modifier.fillMaxWidth().padding(vertical = Dimens.Space8)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                CompositionLocalProvider(LocalLongEntryHours provides longTimerHours) {
                    LazyColumn(
                        state = historyListState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = Dimens.ContentMaxWidth)
                            .fillMaxWidth()
                            .testTag(TrackingTestTags.HISTORY_LIST)
                            .nestedScroll(historyScrollConnection),
                        contentPadding = PaddingValues(
                            bottom = Dimens.FabClearance + if (timerActive) 0.dp else navigationBarInset,
                        ),
                    ) {
                        item(key = "long_timer_warning") {
                            // Recomposes when the warning appears or goes, not on every tick.
                            val showWarning by remember(uiState.isTracking, longTimerHours) {
                                derivedStateOf {
                                    uiState.isTracking &&
                                        elapsed.value >= longTimerHours * SECONDS_PER_HOUR_LONG &&
                                        elapsed.value >= longTimerSnoozedUntil
                                }
                            }
                            if (showWarning) {
                                LongTimerWarning(
                                    modifier = sectionInset,
                                    hours = longTimerHours,
                                    onStop = onStopTracking,
                                    onKeepRunning = {
                                        longTimerSnoozedUntil = elapsed.value + SECONDS_PER_HOUR_LONG
                                        TimeTrackingNotificationService.snoozeLongTimerWarning(context)
                                    },
                                    onAdjust = { uiState.currentTimeEntry?.let { showEditDialog = it } },
                                )
                            }
                        }
                        if (showSyncCenter) {
                            item(key = "sync_center") {
                                SyncCenter(
                                    operations = uiState.syncOperations,
                                    onRetry = onRetrySync,
                                    onRetryEntry = onRetrySyncEntry,
                                    onOpenSyncCenter = onOpenSyncCenter,
                                    modifier = sectionInset,
                                )
                            }
                        }
                        trackingHistoryItems(
                            uiState = uiState,
                            historyItems = historyListItems,
                            projectsById = historyProjectsById,
                            tasksById = historyTasksById,
                            clientsById = historyClientsById,
                            syncStatusByEntryId = syncStatusByEntryId,
                            onEdit = onHistoryEdit,
                            onDelete = onHistoryDelete,
                            onDateClick = onHistoryDateClick,
                            onRetrySync = { onRetrySyncEntry(it.id) },
                            onContinue = onContinueEntry ?: continueEntry.takeIf { !timerActive },
                            onDuplicate = { onDuplicateEntry(it.id) },
                            onDeleteStack = requestDelete,
                            reviewIssues = reviewIssues,
                            showNoMatches = historyIsCurrent &&
                                historyListItems.isEmpty() &&
                                uiState.timeEntries.isNotEmpty() &&
                                (historyFilter.query.isNotBlank() || historyFilter.activeOptionsCount() > 0),
                        )
                    }
                }
                if (fabExpanded) {
                    val closeLabel = stringResource(R.string.timer_fab_close)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = FAB_SCRIM_ALPHA))
                            .clickable(onClickLabel = closeLabel) { fabExpanded = false },
                    )
                }
            }
        }
    }

    if (filterOptionsOpen) {
        HistoryFiltersSheet(
            filter = historyFilter,
            uiState = uiState,
            onChange = { historyFilter = it },
            onDismiss = { filterOptionsOpen = false },
        )
    }

    if (showStartTimerSheet && !timerActive) {
        val startAndClose: (() -> Unit) -> Unit = { start ->
            showStartTimerSheet = false
            start()
        }
        StartTimerSheet(onDismiss = { showStartTimerSheet = false }) {
            StartTimerForm(
                uiState = uiState,
                draft = entryDraft(),
                onDescriptionChange = onDescriptionChange,
                onProjectChange = onProjectChange,
                onTaskChange = onTaskChange,
                onResetEntryFields = onResetEntryFields,
                autoClearEntryFieldsAfterStop = autoClearEntryFieldsAfterStop,
                onTagsChange = onTagsChange,
                onBillableChange = onBillableChange,
                onStart = { startAndClose(onStartTracking) },
            )
            if (lastEntry != null || templateState.quickStart.isNotEmpty()) {
                // One-tap starts: the last entry, then the favourite and recent templates.
                GroupedSection(header = stringResource(R.string.start_timer_shortcuts)) {
                    lastEntry?.let { entry ->
                        ContinueLastEntryRow(
                            entry = entry,
                            projects = uiState.projects,
                            tasks = uiState.tasks,
                            onContinue = { startAndClose { continueEntry(entry) } },
                        )
                    }
                    FavoriteTemplatesRow(
                        templates = templateState.quickStart,
                        projects = templateState.projects,
                        tasks = templateState.tasks,
                        tags = templateState.tags,
                        leadingDivider = lastEntry != null,
                        onStart = { start ->
                            startAndClose {
                                onDescriptionChange(start.description ?: "")
                                onProjectChange(start.projectId)
                                onTaskChange(start.taskId)
                                onTagsChange(start.tagIds)
                                onBillableChange(start.billable)
                                onStartTracking()
                            }
                        },
                    )
                }
            }
        }
    }

    if (pendingDelete.isNotEmpty()) {
        val entries = pendingDelete
        DeleteEntriesDialog(
            count = entries.size,
            onConfirm = {
                pendingDelete = emptyList()
                deletedEntries = entries
                entries.forEach { onDeleteEntry(it.id) }
            },
            onDismiss = { pendingDelete = emptyList() },
        )
    }

    // Edit dialog
    showEditDialog?.let { entry ->
        TimeEntryFormSheet(
            entry = entry,
            zone = uiState.zone,
            suggestedStart = null,
            projects = uiState.projects,
            tasks = uiState.tasks,
            tags = uiState.tags,
            onDismiss = { showEditDialog = null },
            onSave = { description, projectId, taskId, tagIds, billable, start, end ->
                onUpdatePastEntry(entry, description, projectId, taskId, tagIds, billable, start, end)
                showEditDialog = null
            },
            existingEntries = uiState.timeEntries,
            preventOverlap = currentMembership?.organization?.preventOverlappingTimeEntries == true,
            templates = templateState.templates,
            onSaveAsTemplate = onSaveTemplateFromForm,
            saveEnabled = entry.id !in uiState.conflictedEntryIds,
            onDuplicate = { onDuplicateEntry(entry.id) },
            onSplit = { atIso -> onSplitEntry(entry.id, atIso) },
            onDelete = {
                deletedEntries = listOf(entry)
                onDeleteEntry(entry.id)
            },
            // The running timer's details keep its clock and controls on top. Pause
            // and Stop commit the sheet's pending fields and close it: the entry has ended, and a
            // later Save of the running copy must not bring the timer back.
            runningControls = if (uiState.currentTimeEntry?.let { isSameRunningEntry(it, entry) } == true) {
                { pendingEdits ->
                    RunningTimerControls(
                        elapsedSeconds = readElapsed,
                        isPaused = uiState.isPaused,
                        enabled = !uiState.isMutating,
                        onPause = {
                            showEditDialog = null
                            onPauseTrackingWithEdits(pendingEdits())
                        },
                        onResume = onResumeTracking,
                        onStop = {
                            showEditDialog = null
                            onStopTrackingWithEdits(pendingEdits())
                        },
                    )
                }
            } else {
                null
            },
        )
    }

    // Add-entry dialog
    if (showAddDialog) {
        val suggestedStart = remember(uiState.timeEntries, uiState.zone) {
            suggestedManualEntryStart(uiState.timeEntries, ZonedDateTime.now(uiState.zone))
        }
        TimeEntryFormSheet(
            entry = null,
            zone = uiState.zone,
            suggestedStart = suggestedStart,
            projects = uiState.projects,
            tasks = uiState.tasks,
            tags = uiState.tags,
            existingEntries = uiState.timeEntries,
            preventOverlap = currentMembership?.organization?.preventOverlappingTimeEntries == true,
            onDismiss = { showAddDialog = false },
            onSave = { description, projectId, taskId, tagIds, billable, start, end ->
                end?.let {
                    onCreateEntry(description, projectId, taskId, tagIds, billable, start, it)
                    showAddDialog = false
                }
            },
            templates = templateState.templates,
            onSaveAsTemplate = onSaveTemplateFromForm,
        )
    }

    calendarInitialDate?.let { initialDate ->
        androidx.compose.runtime.key(initialDate) {
            EntryDatePickerDialog(
                initialDate = initialDate,
                onDismiss = { calendarInitialDate = null },
                onConfirm = { date ->
                    calendarInitialDate = null
                    onJumpToDate(date)
                },
                confirmLabel = stringResource(R.string.jump_to_date),
            )
        }
    }

    uiState.historyJumpTarget?.let { targetDate ->
        // A progress dialog in the app's dialog style; it closes itself when the jump finishes.
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            confirmButton = { },
            title = {
                Text(
                    stringResource(
                        R.string.finding_date_entries,
                        formatDate(targetDate, LocalContext.current, uiState.zone, appLocale()),
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space16)) {
                    LinearProgressIndicator(
                        progress = { uiState.historyJumpProgress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = uiState.historyRateLimitWaitSeconds?.let { seconds ->
                            pluralStringResource(R.plurals.rate_limit_wait, seconds, seconds)
                        } ?: stringResource(R.string.finding_date_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

/**
 * The day header a history jump to [target] scrolls to (-1 when the list has no day), or null to
 * wait: the jump replaces the window, and until the list is rebuilt from those entries, [builtFrom]
 * is the previous window, where the header would point somewhere else. A jump to a day already
 * loaded fetches the same entries again; that equal list is not rebuilt, so it compares by content.
 */
internal fun historyJumpHeaderIndex(
    target: LocalDate,
    items: List<HistoryListItem>,
    builtFrom: List<TimeEntry>?,
    currentEntries: List<TimeEntry>,
): Int? = if (builtFrom != currentEntries) null else historyHeaderIndex(target, items)

/** The history list and the entries and filter it was built from, to tell a lagging build apart. */
@Immutable
private class HistorySnapshot(val items: List<HistoryListItem>, val entries: List<TimeEntry>?, val filter: HistoryFilter?) {
    companion object {
        val Empty = HistorySnapshot(items = emptyList(), entries = null, filter = null)
    }
}

/**
 * Where a new manual entry starts: the latest end today that is already past, else an hour ago.
 * History is newest first, so the scan stops at entries that started before yesterday; parsing
 * every entry's end whenever the sheet opens was measurable with a long history.
 */
internal fun suggestedManualEntryStart(entries: List<TimeEntry>, now: ZonedDateTime): ZonedDateTime {
    val scanFrom = now.toLocalDate().minusDays(1).atStartOfDay(now.zone).toInstant()
    var latestEnd: Instant? = null
    for (entry in entries) {
        val start = parseTimeEntryInstant(entry.start) ?: continue
        if (start < scanFrom) break
        val end = entry.end?.let(::parseTimeEntryInstant) ?: continue
        if (latestEnd == null || end > latestEnd) latestEnd = end
    }
    return latestEnd?.atZone(now.zone)
        ?.takeIf { it.toLocalDate() == now.toLocalDate() && it.isBefore(now) }
        ?: now.minusHours(1)
}

/** How many search options beyond the text query are active; shown on the filter button. */
private fun HistoryFilter.activeOptionsCount(): Int = listOfNotNull(
    billable, runningOnly.takeIf { it },
    syncStatus, startDate, endDate, clientId, projectId, taskId, tagId,
    missingProjectOnly.takeIf { it }, missingDescriptionOnly.takeIf { it },
    needsCategorization.takeIf { it },
).size

/**
 * The search bar under the Time Tracker header, opened from its search button: the query, a filter
 * button (with the active option count) that opens the options sheet, and close, which clears both.
 */
@Composable
private fun HistorySearchBar(filter: HistoryFilter, onChange: (HistoryFilter) -> Unit, onOpenOptions: () -> Unit, onClose: () -> Unit) {
    val activeOptions = filter.activeOptionsCount()
    val transparent = Color.Transparent
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Dimens.Space4, end = Dimens.Space4, bottom = Dimens.Space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = filter.query,
                onValueChange = { onChange(filter.copy(query = it)) },
                placeholder = { Text(stringResource(R.string.search_history)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = transparent,
                    unfocusedContainerColor = transparent,
                    focusedIndicatorColor = transparent,
                    unfocusedIndicatorColor = transparent,
                ),
                modifier = Modifier.weight(1f).testTag(TrackingTestTags.FILTER_SEARCH_FIELD),
            )
            IconButton(onClick = onOpenOptions, modifier = Modifier.testTag(TrackingTestTags.FILTER_OPEN_BUTTON)) {
                BadgedBox(badge = { if (activeOptions > 0) Badge { Text(activeOptions.toString()) } }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = if (activeOptions > 0) {
                            pluralStringResource(R.plurals.active_filters_count, activeOptions, activeOptions)
                        } else {
                            stringResource(R.string.search_options)
                        },
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.testTag(TrackingTestTags.SEARCH_CLOSE_BUTTON)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
            }
        }
    }
}

/** The date choices of the search options; Custom opens the range picker. */
private enum class HistoryDateChoice { ANY, TODAY, WEEK, CUSTOM }

private fun HistoryFilter.dateChoice(today: LocalDate): HistoryDateChoice = when {
    startDate == null && endDate == null -> HistoryDateChoice.ANY
    startDate == today && endDate == today -> HistoryDateChoice.TODAY
    startDate == today.minusDays(LAST_7_DAYS_OFFSET) && endDate == today -> HistoryDateChoice.WEEK
    else -> HistoryDateChoice.CUSTOM
}

/**
 * The search options in a sheet, laid out like the entry form: a Date section, the catalogue
 * filters as full-width rows that open the same searchable pickers, then billable and the status
 * checks as switches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
internal fun HistoryFiltersSheet(
    filter: HistoryFilter,
    uiState: TrackingUiState,
    onChange: (HistoryFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var showDateRangePicker by remember { mutableStateOf(false) }
    var openPicker by remember { mutableStateOf<HistoryFilterPicker?>(null) }
    val today = LocalDate.now(uiState.zone)
    val clients = remember(uiState.clients) { uiState.clients.map { FilterOption(it.id, it.name) } }
    val projects = remember(uiState.projects, filter.clientId) {
        uiState.projects
            .filter { filter.clientId == null || it.clientId == filter.clientId }
            .map { FilterOption(it.id, it.name) }
    }
    val tasks = remember(uiState.tasks, filter.projectId) {
        uiState.tasks.filter { it.projectId == filter.projectId }.map { FilterOption(it.id, it.name) }
    }
    val tags = remember(uiState.tags) { uiState.tags.map { FilterOption(it.id, it.name) } }
    val all = stringResource(R.string.filter_all)
    fun nameOf(options: List<FilterOption>, id: String?) = options.firstOrNull { it.id == id }?.name ?: all

    AppSheet(
        title = stringResource(R.string.search_options),
        onDismiss = onDismiss,
        titleAction = {
            if (filter.activeOptionsCount() > 0) {
                TextButton(onClick = { onChange(HistoryFilter(query = filter.query)) }) {
                    Text(stringResource(R.string.clear_filters))
                }
            }
        },
        onDone = onDismiss,
        doneTestTag = TrackingTestTags.FILTER_CLOSE_BUTTON,
    ) {
        val dateChoice = filter.dateChoice(today)
        GroupedSection(header = stringResource(R.string.filter_section_date)) {
            SegmentedControl(
                options = HistoryDateChoice.entries,
                selected = dateChoice,
                onSelect = { choice ->
                    when (choice) {
                        HistoryDateChoice.ANY -> onChange(filter.copy(startDate = null, endDate = null))
                        HistoryDateChoice.TODAY -> onChange(filter.copy(startDate = today, endDate = today))
                        HistoryDateChoice.WEEK -> onChange(
                            filter.copy(startDate = today.minusDays(LAST_7_DAYS_OFFSET), endDate = today),
                        )
                        HistoryDateChoice.CUSTOM -> showDateRangePicker = true
                    }
                },
                label = { choice ->
                    stringResource(
                        when (choice) {
                            HistoryDateChoice.ANY -> R.string.filter_any_time
                            HistoryDateChoice.TODAY -> R.string.today
                            HistoryDateChoice.WEEK -> R.string.filter_week_short
                            HistoryDateChoice.CUSTOM -> R.string.stats_custom
                        },
                    )
                },
                modifier = Modifier.padding(horizontal = Dimens.Space8, vertical = Dimens.Space4),
            )
            val start = filter.startDate
            val end = filter.endDate
            if (dateChoice == HistoryDateChoice.CUSTOM && start != null && end != null) {
                val locale = appLocale()
                val formatter = remember(locale) {
                    DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)
                }
                GroupedDivider()
                GroupedRow(
                    title = "${start.format(formatter)} – ${end.format(formatter)}",
                    leadingIcon = Icons.Outlined.DateRange,
                    onClick = { showDateRangePicker = true },
                )
            }
        }

        GroupedSection(header = stringResource(R.string.filter_section_details)) {
            FilterRow(
                label = stringResource(R.string.client),
                icon = Icons.Outlined.Business,
                value = nameOf(clients, filter.clientId),
                onClick = { openPicker = HistoryFilterPicker.CLIENT },
            )
            GroupedDivider(inset = Dimens.SettingsIconInset)
            FilterRow(
                label = stringResource(R.string.project),
                icon = Icons.Outlined.Folder,
                value = nameOf(projects, filter.projectId),
                onClick = { openPicker = HistoryFilterPicker.PROJECT },
            )
            if (filter.projectId != null) {
                GroupedDivider(inset = Dimens.SettingsIconInset)
                FilterRow(
                    label = stringResource(R.string.task),
                    icon = Icons.AutoMirrored.Outlined.List,
                    value = nameOf(tasks, filter.taskId),
                    onClick = { openPicker = HistoryFilterPicker.TASK },
                )
            }
            GroupedDivider(inset = Dimens.SettingsIconInset)
            FilterRow(
                label = stringResource(R.string.tags),
                icon = Icons.AutoMirrored.Outlined.Label,
                value = nameOf(tags, filter.tagId),
                onClick = { openPicker = HistoryFilterPicker.TAG },
            )
        }

        GroupedSection(header = stringResource(R.string.filter_section_status)) {
            val billableOptions = listOf(null, true, false)
            SegmentedControl(
                options = billableOptions,
                selected = filter.billable,
                onSelect = { onChange(filter.copy(billable = it)) },
                label = { billable ->
                    stringResource(
                        when (billable) {
                            null -> R.string.filter_all
                            true -> R.string.billable
                            false -> R.string.non_billable
                        },
                    )
                },
                modifier = Modifier.padding(horizontal = Dimens.Space8, vertical = Dimens.Space4),
            )
            GroupedDivider()
            GroupedSwitchRow(
                title = stringResource(R.string.running_entries),
                leadingIcon = Icons.Outlined.Timer,
                checked = filter.runningOnly,
                onCheckedChange = { onChange(filter.copy(runningOnly = it)) },
            )
            GroupedDivider(inset = Dimens.SettingsIconInset)
            GroupedSwitchRow(
                title = stringResource(R.string.filter_failed_sync),
                leadingIcon = Icons.Outlined.SyncProblem,
                checked = filter.syncStatus == TimeEntryRepository.EntrySyncStatus.FAILED,
                onCheckedChange = { failed ->
                    onChange(filter.copy(syncStatus = TimeEntryRepository.EntrySyncStatus.FAILED.takeIf { failed }))
                },
            )
            GroupedDivider(inset = Dimens.SettingsIconInset)
            GroupedSwitchRow(
                title = stringResource(R.string.needs_categorization),
                leadingIcon = Icons.Outlined.Category,
                checked = filter.needsCategorization,
                onCheckedChange = { onChange(filter.copy(needsCategorization = it)) },
            )
            GroupedDivider(inset = Dimens.SettingsIconInset)
            GroupedSwitchRow(
                title = stringResource(R.string.without_project),
                leadingIcon = Icons.Outlined.FolderOff,
                checked = filter.missingProjectOnly,
                onCheckedChange = { onChange(filter.copy(missingProjectOnly = it)) },
            )
            GroupedDivider(inset = Dimens.SettingsIconInset)
            GroupedSwitchRow(
                title = stringResource(R.string.without_description),
                leadingIcon = Icons.AutoMirrored.Outlined.Notes,
                checked = filter.missingDescriptionOnly,
                onCheckedChange = { onChange(filter.copy(missingDescriptionOnly = it)) },
            )
        }
    }

    when (openPicker) {
        HistoryFilterPicker.CLIENT -> SingleSelectFilterPicker(
            title = stringResource(R.string.client),
            options = clients,
            selectedId = filter.clientId,
            onSelect = { onChange(filter.copy(clientId = it, projectId = null, taskId = null)) },
            onDismiss = { openPicker = null },
        )
        HistoryFilterPicker.PROJECT -> SingleSelectFilterPicker(
            title = stringResource(R.string.project),
            options = projects,
            selectedId = filter.projectId,
            onSelect = { onChange(filter.copy(projectId = it, taskId = null)) },
            onDismiss = { openPicker = null },
        )
        HistoryFilterPicker.TASK -> SingleSelectFilterPicker(
            title = stringResource(R.string.task),
            options = tasks,
            selectedId = filter.taskId,
            onSelect = { onChange(filter.copy(taskId = it)) },
            onDismiss = { openPicker = null },
        )
        HistoryFilterPicker.TAG -> SingleSelectFilterPicker(
            title = stringResource(R.string.tags),
            options = tags,
            selectedId = filter.tagId,
            onSelect = { onChange(filter.copy(tagId = it)) },
            onDismiss = { openPicker = null },
        )
        null -> Unit
    }

    if (showDateRangePicker) {
        DateRangePickerDialog(
            initialStart = filter.startDate,
            initialEnd = filter.endDate,
            onDismiss = { showDateRangePicker = false },
            onConfirm = { start, end ->
                onChange(filter.copy(startDate = start, endDate = end))
                showDateRangePicker = false
            },
        )
    }
}

private enum class HistoryFilterPicker { CLIENT, PROJECT, TASK, TAG }

/**
 * The Time Tracker's sync status, as a grouped section: the summary with Retry when changes can be
 * retried, one row per change that failed, and a row into the sync details.
 */
@Composable
private fun SyncCenter(
    operations: List<TimeEntryRepository.SyncOperation>,
    onRetry: () -> Unit,
    onRetryEntry: (String) -> Unit,
    onOpenSyncCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failedOperations = operations.filter { it.status == TimeEntryRepository.EntrySyncStatus.FAILED }
    val failed = failedOperations.size
    val retrying = operations.count { it.status == TimeEntryRepository.EntrySyncStatus.RETRYING }
    val conflicts = operations.count { it.status == TimeEntryRepository.EntrySyncStatus.CONFLICT }
    val (statusIcon, statusTint) = when {
        failed > 0 -> Icons.Outlined.SyncProblem to MaterialTheme.colorScheme.syncFailed
        conflicts > 0 -> Icons.Outlined.SyncProblem to MaterialTheme.colorScheme.error
        retrying > 0 -> Icons.Outlined.Sync to MaterialTheme.colorScheme.syncPending
        else -> Icons.Outlined.Schedule to MaterialTheme.colorScheme.syncPending
    }
    val retryLabel = stringResource(R.string.retry)
    GroupedSection(
        modifier = modifier.testTag(TrackingTestTags.SYNC_STATUS_CARD),
        header = stringResource(R.string.sync_status_card_title),
    ) {
        GroupedRow(
            title = when {
                failed > 0 -> pluralStringResource(R.plurals.sync_failed_count, failed, failed)
                conflicts > 0 -> pluralStringResource(R.plurals.sync_conflict_count, conflicts, conflicts)
                retrying > 0 -> pluralStringResource(R.plurals.sync_retrying_count, retrying, retrying)
                else -> pluralStringResource(R.plurals.sync_pending_count, operations.size, operations.size)
            },
            leadingIcon = statusIcon,
            leadingIconTint = statusTint,
            trailing = if (failed > 0 || retrying > 0) {
                { TextButton(onClick = onRetry) { Text(retryLabel) } }
            } else {
                null
            },
        )
        failedOperations.forEach { operation ->
            GroupedDivider(inset = Dimens.SettingsIconInset)
            GroupedRow(
                title = stringResource(R.string.sync_entry_failed),
                leadingIcon = Icons.Outlined.ErrorOutline,
                leadingIconTint = MaterialTheme.colorScheme.syncFailed,
                trailing = { TextButton(onClick = { onRetryEntry(operation.entryId) }) { Text(retryLabel) } },
            )
        }
        GroupedDivider(inset = Dimens.SettingsIconInset)
        GroupedRow(
            title = stringResource(R.string.sync_center_open),
            leadingIcon = Icons.Outlined.Info,
            onClick = onOpenSyncCenter,
            modifier = Modifier.testTag(TrackingTestTags.SYNC_DETAILS_BUTTON),
        )
    }
}

/**
 * The long-running timer check, as a grouped section: the warning, then adjust the end, keep
 * running for another hour, and stop, which is the destructive row.
 */
@Composable
private fun LongTimerWarning(
    hours: Int,
    onStop: () -> Unit,
    onKeepRunning: () -> Unit,
    onAdjust: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupedSection(modifier = modifier) {
        GroupedRow(
            title = pluralStringResource(R.plurals.timer_running_long, hours, hours),
            leadingIcon = Icons.Outlined.Timer,
            leadingIconTint = MaterialTheme.colorScheme.error,
        )
        GroupedDivider(inset = Dimens.SettingsIconInset)
        GroupedRow(
            title = stringResource(R.string.adjust_end_time),
            leadingIcon = Icons.Outlined.Edit,
            onClick = onAdjust,
        )
        GroupedDivider(inset = Dimens.SettingsIconInset)
        GroupedRow(
            title = stringResource(R.string.keep_running),
            leadingIcon = Icons.Outlined.Snooze,
            showChevron = false,
            onClick = onKeepRunning,
        )
        GroupedDivider(inset = Dimens.SettingsIconInset)
        GroupedRow(
            title = stringResource(R.string.stop_now),
            leadingIcon = Icons.Default.Stop,
            destructive = true,
            onClick = onStop,
        )
    }
}

private const val HISTORY_PREFETCH_ITEMS = 75
private const val HISTORY_INITIAL_PREFETCH_MAX_ENTRIES = 250

@Suppress("LongParameterList")
internal fun LazyListScope.trackingHistoryItems(
    uiState: TrackingUiState,
    historyItems: List<HistoryListItem>,
    projectsById: Map<String, Project>,
    tasksById: Map<String, Task>,
    clientsById: Map<String, Client>,
    syncStatusByEntryId: Map<String, TimeEntryRepository.EntrySyncStatus>,
    onEdit: (TimeEntry) -> Unit,
    onDelete: (TimeEntry) -> Unit,
    onDateClick: (LocalDate) -> Unit,
    onRetrySync: (TimeEntry) -> Unit = {},
    onContinue: ((TimeEntry) -> Unit)? = null,
    onDuplicate: ((TimeEntry) -> Unit)? = null,
    onDeleteStack: ((List<TimeEntry>) -> Unit)? = null,
    reviewIssues: Map<String, Set<EntryReviewIssue>> = emptyMap(),
    showNoMatches: Boolean = false,
) {
    if (!uiState.hasLoadedTimeEntries && uiState.timeEntries.isEmpty()) {
        item(key = "history_loading_header") { HistoryLoadingHeader() }
        repeat(HISTORY_PLACEHOLDER_COUNT) { index ->
            item(key = "history_loading_$index") { HistoryLoadingEntry(index) }
        }
        return
    }

    val zone = uiState.zone
    items(
        items = historyItems,
        key = { it.key },
        contentType = {
            when (it) {
                is HistoryListItem.Week -> "history_week"
                is HistoryListItem.Header -> "history_header"
                is HistoryListItem.Group -> "history_group"
            }
        },
    ) { historyItem ->
        when (historyItem) {
            is HistoryListItem.Week -> HistoryWeekHeader(week = historyItem)
            is HistoryListItem.Header -> HistoryDayHeader(
                day = historyItem.day,
                zone = zone,
                onClick = { onDateClick(historyItem.day.date) },
            )
            is HistoryListItem.Group -> {
                val project = projectsById[historyItem.lead.projectId]
                HistoryEntryCard(
                    group = historyItem,
                    zone = zone,
                    project = project,
                    task = tasksById[historyItem.lead.taskId],
                    client = project?.clientId?.let(clientsById::get),
                    // Only this card's statuses: a sync or review update elsewhere leaves it equal,
                    // so it is skipped instead of every visible card redrawing.
                    status = historyCardStatus(historyItem, syncStatusByEntryId, reviewIssues),
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onDeleteStack = onDeleteStack,
                    onDuplicate = onDuplicate,
                    onRetrySync = onRetrySync,
                    onContinue = onContinue,
                )
            }
        }
    }

    if (showNoMatches) {
        item(key = "history_no_matches") { EmptyState(text = stringResource(R.string.no_results_found)) }
    }

    if (uiState.hasMoreTimeEntries || uiState.isLoadingMoreTimeEntries) {
        item(key = "history_pagination") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                uiState.totalTimeEntries?.let { total ->
                    Text(
                        text = "  ${uiState.timeEntries.size} / $total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (uiState.timeEntries.isEmpty() && uiState.hasLoadedTimeEntries && !uiState.isLoading) {
        item(key = "history_empty") { EmptyState(text = stringResource(R.string.no_time_entries)) }
    }
}

/** Compatibility entry point for previews and screenshot tests that provide grouped history. */
internal fun LazyListScope.trackingHistoryItems(
    uiState: TrackingUiState,
    groupedEntries: Map<LocalDate, List<TimeEntry>>,
    onEdit: (TimeEntry) -> Unit,
    onDelete: (TimeEntry) -> Unit,
    onDateClick: (LocalDate) -> Unit,
    onRetrySync: (TimeEntry) -> Unit = {},
    onContinue: ((TimeEntry) -> Unit)? = null,
    onDuplicate: ((TimeEntry) -> Unit)? = null,
    onDeleteStack: ((List<TimeEntry>) -> Unit)? = null,
    reviewIssues: Map<String, Set<EntryReviewIssue>> = emptyMap(),
) {
    val now = Instant.now()
    trackingHistoryItems(
        uiState = uiState,
        historyItems = buildHistoryListItems(
            days = groupedEntries,
            firstDayOfWeek = uiState.firstDayOfWeek,
            today = LocalDate.now(uiState.zone),
            zone = uiState.zone,
            now = now,
        ),
        projectsById = uiState.projects.associateBy { it.id },
        tasksById = uiState.tasks.associateBy { it.id },
        clientsById = uiState.clients.associateBy { it.id },
        syncStatusByEntryId = worstSyncStatusByEntryId(uiState.syncOperations),
        onEdit = onEdit,
        onDelete = onDelete,
        onDateClick = onDateClick,
        onRetrySync = onRetrySync,
        onContinue = onContinue,
        onDuplicate = onDuplicate,
        onDeleteStack = onDeleteStack,
        reviewIssues = reviewIssues,
    )
}

@Composable
private fun rememberGhostAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "history loading")
    val alpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ghost alpha",
    )
    return alpha
}

@Composable
private fun GhostBlock(modifier: Modifier, alpha: Float) {
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                RoundedCornerShape(6.dp),
            ),
    )
}

@Composable
private fun HistoryLoadingHeader() {
    val alpha = rememberGhostAlpha()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostBlock(Modifier.width(72.dp).height(12.dp), alpha)
        GhostBlock(Modifier.width(150.dp).height(10.dp), alpha)
    }
}

@Composable
private fun HistoryLoadingEntry(index: Int) {
    val alpha = rememberGhostAlpha()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GhostBlock(Modifier.size(10.dp), alpha)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                GhostBlock(
                    Modifier
                        .fillMaxWidth(if (index % ALTERNATING_ROW_COUNT == 0) GHOST_PRIMARY_WIDTH else GHOST_SECONDARY_WIDTH)
                        .height(13.dp),
                    alpha,
                )
                GhostBlock(Modifier.width(if (index % 2 == 0) 96.dp else 128.dp).height(10.dp), alpha)
            }
            GhostBlock(Modifier.width(58.dp).height(14.dp), alpha)
        }
    }
}

/**
 * The last entry as a start-timer shortcut: its description, then "Project · Task"; tapping starts
 * a timer with its fields, like the favourites below it.
 */
@Composable
private fun ContinueLastEntryRow(entry: TimeEntry, projects: List<Project>, tasks: List<Task>, onContinue: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val projectName = remember(entry.projectId, projects) { projects.firstOrNull { it.id == entry.projectId }?.name }
    val taskName = remember(entry.taskId, tasks) { tasks.firstOrNull { it.id == entry.taskId }?.name }
    GroupedRow(
        title = entry.description.orEmpty(),
        subtitle = shortcutProjectTaskLabel(projectName, taskName),
        leadingIcon = Icons.Default.PlayArrow,
        showChevron = false,
        singleLine = true,
        onClickLabel = stringResource(R.string.continue_last_entry),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onContinue()
        },
        modifier = Modifier.testTag(TrackingTestTags.CONTINUE_BUTTON),
    )
}

/** "Project · Task" for a shortcut row; just the project without a task, nothing without a project. */
@Composable
internal fun shortcutProjectTaskLabel(projectName: String?, taskName: String?): String? = when {
    projectName == null -> null
    taskName == null -> projectName
    else -> stringResource(R.string.start_timer_shortcut_project_task, projectName, taskName)
}

/**
 * Description field with dropdown suggestions from recent time entries.
 * Shows the last 5 unique entries (deduplicated by description+project+task+tags).
 * Filters out entries with no description.
 * Tapping a suggestion copies all fields (description, project, task, tags, billable).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DescriptionFieldWithSuggestions(
    description: String,
    onDescriptionChange: (String) -> Unit,
    timeEntries: List<TimeEntry>,
    projects: List<Project>,
    tags: List<Tag>,
    enabled: Boolean,
    onEntryCopied: (TimeEntry) -> Unit,
    borderless: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val descriptionLabel = stringResource(R.string.description)

    // The last 5 unique recent entries with a description. History is newest first, so this stops
    // as soon as it has them instead of sorting the whole history.
    val recentEntries = remember(timeEntries) {
        timeEntries
            .asSequence()
            .filter { isCompletedTimeEntry(it) && !it.description.isNullOrBlank() }
            .distinctBy { entry ->
                "${entry.description}|${entry.projectId}|${entry.taskId}|${entry.tags.map { it.id }.sorted()}"
            }
            .take(RECENT_ENTRIES_LIMIT)
            .toList()
    }

    ExposedDropdownMenuBox(
        expanded = expanded && recentEntries.isNotEmpty(),
        onExpandedChange = {
            if (enabled) expanded = it
        },
    ) {
        val fieldModifier = Modifier
            .fillMaxWidth()
            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = enabled)
            .onFocusChanged {
                if (it.isFocused && enabled) expanded = true
            }
        val onValueChange: (String) -> Unit = {
            onDescriptionChange(it)
            expanded = false
        }
        if (borderless) {
            val transparent = androidx.compose.ui.graphics.Color.Transparent
            TextField(
                value = description,
                onValueChange = onValueChange,
                placeholder = { Text(stringResource(R.string.what_are_you_working_on)) },
                modifier = fieldModifier.semantics { contentDescription = descriptionLabel },
                singleLine = true,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = transparent,
                    unfocusedContainerColor = transparent,
                    disabledContainerColor = transparent,
                    focusedIndicatorColor = transparent,
                    unfocusedIndicatorColor = transparent,
                    disabledIndicatorColor = transparent,
                ),
            )
        } else {
            OutlinedTextField(
                value = description,
                onValueChange = onValueChange,
                label = { Text(descriptionLabel) },
                placeholder = { Text(stringResource(R.string.what_are_you_working_on)) },
                modifier = fieldModifier,
                singleLine = true,
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
            )
        }

        ExposedDropdownMenu(
            expanded = expanded && recentEntries.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            Text(
                stringResource(R.string.recent_entries),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            recentEntries.forEach { entry ->
                val project = projects.find { it.id == entry.projectId }
                val entryTagNames = entry.tags.map { it.name }

                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Description
                            Text(
                                text = entry.description!!,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // Project row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (project != null) {
                                    ProjectTaskLine(
                                        project = project,
                                        task = null,
                                        background = MaterialTheme.colorScheme.surfaceContainer,
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.no_project_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            // Tag chips
                            if (entryTagNames.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    entryTagNames.forEach { tagName ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            tonalElevation = 0.dp,
                                        ) {
                                            Text(
                                                text = tagName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(
                                                    horizontal = 6.dp,
                                                    vertical = 2.dp,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onClick = {
                        onEntryCopied(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Separately searchable project and task selectors shared by Track entry forms. */
@Composable
internal fun ProjectTaskDropdown(
    selectedProjectId: String?,
    selectedTaskId: String?,
    projects: List<Project>,
    tasks: List<Task>,
    onSelectionChanged: (projectId: String?, taskId: String?) -> Unit,
    enabled: Boolean,
    onCreateProject: ((String) -> Unit)? = null,
    onCreateTask: ((String, String) -> Unit)? = null,
    style: SelectorStyle = SelectorStyle.Field,
) {
    SharedProjectTaskDropdown(
        projects = projects,
        tasks = tasks,
        selectedProjectId = selectedProjectId,
        selectedTaskId = selectedTaskId,
        onSelectionChanged = onSelectionChanged,
        enabled = enabled,
        showProjectColors = true,
        rounded = true,
        onCreateProject = onCreateProject,
        onCreateTask = onCreateTask,
        style = style,
    )
}

/** Confirms deleting one history entry, or every entry of a stack ([count] > 1). */
@Composable
private fun DeleteEntriesDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = if (count > 1) {
            pluralStringResource(R.plurals.history_delete_stack_title, count, count)
        } else {
            stringResource(R.string.calendar_delete_entry_title)
        },
        message = stringResource(if (count > 1) R.string.history_delete_stack_message else R.string.calendar_delete_entry_message),
        confirmLabel = stringResource(R.string.delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
        confirmTestTag = TrackingTestTags.DELETE_CONFIRM,
        dismissTestTag = TrackingTestTags.DELETE_CANCEL,
    )
}

/**
 * The Time Tracker header: the side-menu button and title, then search, enable-notifications
 * (when not granted) and refresh, which spins while a sync runs.
 */
@Composable
internal fun TimeTrackerTopBar(
    syncing: Boolean,
    onRefresh: () -> Unit,
    onRequestNotifications: (() -> Unit)?,
    searchOpen: Boolean = false,
    onSearch: (() -> Unit)? = null,
) {
    MainTopBar(
        title = stringResource(R.string.nav_time_tracker),
        actions = {
            if (onSearch != null && !searchOpen) {
                IconButton(onClick = onSearch, modifier = Modifier.testTag(TrackingTestTags.SEARCH_BUTTON)) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_history))
                }
            }
            if (onRequestNotifications != null) {
                IconButton(onClick = onRequestNotifications) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsOff,
                        contentDescription = stringResource(R.string.enable_notifications),
                    )
                }
            }
            // Only compose the infinite spin while syncing; an idle infinite transition keeps the
            // frame clock busy forever.
            val syncRotation = if (syncing) {
                val syncTransition = rememberInfiniteTransition(label = "sync")
                val rotation by syncTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = FULL_ROTATION_DEGREES,
                    animationSpec = infiniteRepeatable(tween(SYNC_ROTATION_DURATION_MS), RepeatMode.Restart),
                    label = "sync rotation",
                )
                rotation
            } else {
                0f
            }
            IconButton(onClick = onRefresh, modifier = Modifier.testTag(TrackingTestTags.REFRESH_BUTTON)) {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = stringResource(if (syncing) R.string.syncing else R.string.refresh),
                    modifier = Modifier.rotate(syncRotation),
                )
            }
        },
    )
}

/**
 * Create/edit time entry bottom sheet. [entry] null = create mode.
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TimeEntryFormSheet(
    entry: TimeEntry?, // null = create mode
    zone: ZoneId, // account temporal-policy zone for the new-entry fallback start
    suggestedStart: ZonedDateTime?, // create mode: pre-filled start (end of last entry / now-1h)
    projects: List<Project>,
    tasks: List<Task>,
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onSave: (String?, String?, String?, List<String>, Boolean, String, String?) -> Unit,
    existingEntries: List<TimeEntry> = emptyList(),
    preventOverlap: Boolean = false,
    templates: List<EntryTemplate> = emptyList(),
    onSaveAsTemplate: ((TemplateDraft) -> Unit)? = null,
    saveEnabled: Boolean = true,
    onDuplicate: (() -> Unit)? = null,
    onSplit: ((String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    // The running timer's controls, given the sheet's pending fields for a Pause or Stop there.
    runningControls: (@Composable (pendingEdits: () -> RunningEntryEdits) -> Unit)? = null,
) {
    // Keyed on the entry: when another entry replaces the one shown (e.g. "edit the running entry"
    // arriving while a history entry is open), its fields must not carry over.
    val entryKey = entry?.id
    var description by remember(entryKey) { mutableStateOf(entry?.description ?: "") }
    var projectId by remember(entryKey) { mutableStateOf(entry?.projectId) }
    var taskId by remember(entryKey) { mutableStateOf(entry?.taskId) }
    var selectedTags by remember(entryKey) { mutableStateOf(entry?.tags?.map { it.id } ?: emptyList<String>()) }
    var billable by remember(entryKey) { mutableStateOf(entry?.billable ?: false) }
    val isRunningEntry = remember(entry?.id, entry?.end, entry?.duration) {
        entry?.let(::isRunningTimeEntry) == true
    }
    var templatePickerOpen by remember(entryKey) { mutableStateOf(false) }
    val originalStart = remember(entry?.id, zone) {
        entry?.let { ZonedDateTime.parse(it.start, DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(zone) }
            ?: (suggestedStart ?: ZonedDateTime.now(zone).minusHours(1))
                .withSecond(0).withNano(0)
    }
    val originalEnd = remember(entry?.id, zone) {
        when {
            entry?.end != null -> ZonedDateTime.parse(entry.end, DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(zone)
            entry != null -> originalStart.plusSeconds((entry.duration ?: 0).toLong())
            else -> ZonedDateTime.now(originalStart.zone).withSecond(0).withNano(0)
                .let { if (it.isAfter(originalStart)) it else originalStart.plusMinutes(1) }
        }
    }
    var startTime by remember(entry?.id, zone) { mutableStateOf(originalStart) }
    var endTime by remember(entry?.id, zone) { mutableStateOf(originalEnd) }
    var durationMinutes by remember(entry?.id, zone) {
        mutableStateOf(java.time.Duration.between(originalStart, originalEnd).toMinutes().coerceAtLeast(1).toString())
    }
    var editingTime by remember(entryKey) { mutableStateOf<TimeField?>(null) }
    var editingDate by remember(entryKey) { mutableStateOf<TimeField?>(null) }
    var showSplitPicker by remember(entryKey) { mutableStateOf(false) }
    var splitOutsideEntry by remember(entryKey) { mutableStateOf(false) }
    val durationIsValid = isRunningEntry || durationMinutes.toLongOrNull()?.let { it > 0 } == true
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // The other entries' intervals, parsed once off the main thread; each time change then only
    // compares instants of the entries near the new interval.
    val overlapIndex by produceState<EntryOverlapIndex?>(null, existingEntries) {
        value = withContext(Dispatchers.Default) { EntryOverlapIndex.of(existingEntries) }
    }
    val overlapOrganizationId = entry?.organizationId ?: existingEntries.firstOrNull()?.organizationId
    val overlaps = remember(startTime, endTime, overlapIndex, entry?.id, overlapOrganizationId, isRunningEntry) {
        if (isRunningEntry || overlapOrganizationId == null) {
            false
        } else {
            overlapIndex?.overlaps(
                excludeId = entry?.id.orEmpty(),
                organizationId = overlapOrganizationId,
                start = startTime.toInstant(),
                end = endTime.toInstant(),
            ) == true
        }
    }
    val validation = remember(startTime, endTime, overlaps, preventOverlap, isRunningEntry) {
        if (isRunningEntry) {
            EntryTimeValidator.Result(error = null, warnings = emptyList())
        } else {
            EntryTimeValidator.evaluate(startTime, endTime, overlaps, preventOverlap)
        }
    }
    val durationHours = remember(startTime, endTime) {
        java.time.Duration.between(startTime, endTime).toHours().coerceAtLeast(0)
    }

    fun setDuration(minutes: Long) {
        val safeMinutes = minutes.coerceAtLeast(1)
        durationMinutes = safeMinutes.toString()
        endTime = startTime.plusMinutes(safeMinutes)
    }

    val saveEntry = {
        onSave(
            description.ifEmpty { null },
            projectId,
            taskId,
            selectedTags,
            billable,
            formatTimeEntryInstant(startTime),
            endTime.takeUnless { isRunningEntry }?.let(::formatTimeEntryInstant),
        )
    }
    ModalBottomSheet(
        // Child date/time/split pickers use separate dialog windows. Do not let the parent sheet
        // interpret their focus change as a request to close the whole editor.
        onDismissRequest = {
            if (canDismissTimeEntryFormSheet(editingTime != null, editingDate != null, showSplitPicker, templatePickerOpen)) onDismiss()
        },
        modifier = Modifier.testTag(TrackingTestTags.SHEET),
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Dimens.RadiusXl, topEnd = Dimens.RadiusXl),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Only the drag handle may dismiss the sheet; swipes in the form just scroll it.
                .nestedScroll(ConsumeVerticalOverscroll)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = Dimens.Space24),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space16),
        ) {
            Box(Modifier.padding(horizontal = Dimens.Space8)) {
                EntrySheetHeader(
                    title = stringResource(if (entry == null) R.string.add_time_entry else R.string.edit_time_entry),
                    onCancel = onDismiss,
                    onSave = saveEntry,
                    saveEnabled = saveEnabled && durationIsValid && validation.canSave,
                    cancelTag = EditTimeEntryTestTags.CANCEL_BUTTON,
                    saveTag = TrackingTestTags.SHEET_SAVE_BUTTON,
                )
            }

            if (isRunningEntry) {
                runningControls?.invoke {
                    RunningEntryEdits(
                        description = description.ifEmpty { null },
                        projectId = projectId,
                        taskId = taskId,
                        tagIds = selectedTags,
                        billable = billable,
                        start = formatTimeEntryInstant(startTime),
                    )
                }
            }

            EntryDescriptionField(
                value = description,
                onValueChange = { description = it },
                testTag = TrackingTestTags.SHEET_DESCRIPTION_FIELD,
            )

            GroupedSection {
                ProjectTaskDropdown(
                    selectedProjectId = projectId,
                    selectedTaskId = taskId,
                    projects = projects,
                    tasks = tasks,
                    onSelectionChanged = { newProjectId, newTaskId ->
                        projectId = newProjectId
                        taskId = newTaskId
                    },
                    enabled = true,
                    style = SelectorStyle.Grouped,
                )
                GroupedDivider(inset = Dimens.SettingsIconInset)
                TagsSelector(
                    selectedTagIds = selectedTags,
                    availableTags = tags,
                    onTagsChanged = { selectedTags = it },
                    enabled = true,
                    style = SelectorStyle.Grouped,
                )
                GroupedDivider(inset = Dimens.SettingsIconInset)
                GroupedSwitchRow(
                    title = stringResource(R.string.billable),
                    leadingIcon = Icons.Outlined.AttachMoney,
                    checked = billable,
                    onCheckedChange = { billable = it },
                    modifier = Modifier.testTag(TrackingTestTags.SHEET_BILLABLE),
                )
            }

            GroupedSection(
                header = stringResource(R.string.time_section),
                footer = stringResource(R.string.running_entry_start_edit_hint).takeIf { isRunningEntry },
            ) {
                EntryTimeRow(
                    label = stringResource(R.string.start),
                    value = startTime,
                    onDateClick = { editingDate = TimeField.Start },
                    onTimeClick = { editingTime = TimeField.Start },
                    dateTag = TrackingTestTags.SHEET_START_DATE,
                    timeTag = TrackingTestTags.SHEET_START_TIME,
                    dateLabel = stringResource(R.string.start_date),
                    timeLabel = stringResource(R.string.start_time),
                )
                if (!isRunningEntry) {
                    GroupedDivider()
                    EntryTimeRow(
                        label = stringResource(R.string.end),
                        value = endTime,
                        onDateClick = { editingDate = TimeField.End },
                        onTimeClick = { editingTime = TimeField.End },
                        dateTag = TrackingTestTags.SHEET_END_DATE,
                        timeTag = TrackingTestTags.SHEET_END_TIME,
                        dateLabel = stringResource(R.string.end_date),
                        timeLabel = stringResource(R.string.end_time),
                    )
                    GroupedDivider()
                    EntryDurationRow(
                        minutesText = durationMinutes,
                        totalLabel = formatElapsedTime((durationMinutes.toLongOrNull() ?: 0) * SECONDS_PER_MINUTE_LONG),
                        isValid = durationIsValid,
                        onMinutesTextChange = { value ->
                            durationMinutes = value
                            value.toLongOrNull()
                                ?.takeIf { it >= MINIMUM_DURATION_MINUTES }
                                ?.let { endTime = startTime.plusMinutes(it) }
                        },
                        onDecrease = {
                            setDuration((durationMinutes.toLongOrNull() ?: MINIMUM_DURATION_MINUTES) - DURATION_STEP_MINUTES)
                        },
                        onIncrease = { setDuration((durationMinutes.toLongOrNull() ?: 0) + DURATION_STEP_MINUTES) },
                        fieldTag = TrackingTestTags.SHEET_DURATION_FIELD,
                    )
                }
            }

            if (!isRunningEntry) {
                Box(Modifier.padding(horizontal = Dimens.Space16)) {
                    EntryValidationBanner(result = validation, durationHours = durationHours)
                }
            }

            if (!saveEnabled) {
                Text(
                    text = stringResource(R.string.sync_conflict_edit_locked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Dimens.Space32),
                )
            }

            // Save-as-template / start-from-template affordance (gap analysis #1, #9).
            if (templates.isNotEmpty() || onSaveAsTemplate != null) {
                GroupedSection {
                    if (templates.isNotEmpty()) {
                        GroupedRow(
                            title = stringResource(R.string.templates_use_template),
                            leadingIcon = Icons.Outlined.StarOutline,
                            onClick = { templatePickerOpen = true },
                        )
                    }
                    if (templates.isNotEmpty() && onSaveAsTemplate != null) {
                        GroupedDivider(inset = Dimens.SettingsIconInset)
                    }
                    if (onSaveAsTemplate != null) {
                        val canSaveTemplate = projectId != null || description.isNotBlank() || selectedTags.isNotEmpty()
                        GroupedRow(
                            title = stringResource(R.string.templates_save_as_template),
                            leadingIcon = Icons.Outlined.BookmarkAdd,
                            showChevron = false,
                            onClick = if (canSaveTemplate) {
                                {
                                    onSaveAsTemplate(
                                        TemplateDraft(
                                            name = null,
                                            projectId = projectId,
                                            taskId = taskId,
                                            description = description.trim().takeIf { it.isNotEmpty() },
                                            tagIds = selectedTags,
                                            billable = billable,
                                            isFavorite = false,
                                        ),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            // Roadmap #13: Duplicate/Split act on the stored entry. Only for completed
            // (has end) and editable (not conflicted) entries; hidden otherwise.
            val canDuplicateOrSplit = entry?.let(::isCompletedTimeEntry) == true && saveEnabled
            if (canDuplicateOrSplit && (onDuplicate != null || onSplit != null)) {
                GroupedSection {
                    if (onDuplicate != null) {
                        GroupedRow(
                            title = stringResource(R.string.duplicate_entry),
                            leadingIcon = Icons.Outlined.ContentCopy,
                            showChevron = false,
                            onClick = {
                                onDuplicate()
                                onDismiss()
                            },
                            modifier = Modifier.testTag(EditTimeEntryTestTags.DUPLICATE_BUTTON),
                        )
                    }
                    if (onDuplicate != null && onSplit != null) GroupedDivider(inset = Dimens.SettingsIconInset)
                    if (onSplit != null) {
                        GroupedRow(
                            title = stringResource(R.string.split_entry),
                            leadingIcon = Icons.AutoMirrored.Outlined.CallSplit,
                            showChevron = false,
                            onClick = {
                                splitOutsideEntry = false
                                showSplitPicker = true
                            },
                            modifier = Modifier.testTag(EditTimeEntryTestTags.SPLIT_BUTTON),
                        )
                    }
                }
                if (splitOutsideEntry) {
                    // A picked time the entry does not contain; say why nothing was split.
                    Text(
                        text = stringResource(
                            R.string.split_time_outside_entry,
                            originalStart.format(hourMinuteFormatter),
                            originalEnd.format(hourMinuteFormatter),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = Dimens.Space32),
                    )
                }
            }

            if (entry != null && onDelete != null && saveEnabled) {
                DestructiveActionRow(
                    label = stringResource(R.string.delete_entry),
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    testTag = EditTimeEntryTestTags.DELETE_BUTTON,
                )
            }
        }
    }

    editingTime?.let { field ->
        val current = if (field == TimeField.Start) startTime else endTime
        EntryTimePickerDialog(
            title = stringResource(if (field == TimeField.Start) R.string.start_time else R.string.end_time),
            initial = current,
            onDismiss = { editingTime = null },
            onConfirm = { hour, minute ->
                if (field == TimeField.Start) {
                    val newStart = startTime.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                    if (!isRunningEntry) endTime = retimedEnd(startTime, newStart, endTime)
                    startTime = newStart
                } else {
                    val sameDayEnd = endTime.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                    // Do not silently roll an earlier clock-time into a ~24h entry: only a plausible
                    // overnight span becomes cross-midnight, otherwise keep it same-day so the
                    // validation banner surfaces the end-before-start error for the user to fix.
                    endTime = EntryTimeValidator.resolveEnd(startTime, sameDayEnd) ?: sameDayEnd
                    durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes().toString()
                }
                editingTime = null
            },
        )
    }

    editingDate?.let { field ->
        val current = if (field == TimeField.Start) startTime else endTime
        EntryDatePickerDialog(
            initialDate = current.toLocalDate(),
            onDismiss = { editingDate = null },
            onConfirm = { date ->
                if (field == TimeField.Start) {
                    val newStart = startTime.with(date)
                    if (!isRunningEntry) endTime = retimedEnd(startTime, newStart, endTime)
                    startTime = newStart
                } else {
                    endTime = endTime.with(date)
                    durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes().toString()
                }
                editingDate = null
            },
        )
    }

    // Roadmap #13/#64: split-time picker constrained to the strictly-interior (start, end) window.
    if (showSplitPicker && entry?.let(::isCompletedTimeEntry) == true && onSplit != null) {
        // Midpoint is a sensible default and always a valid interior instant.
        val midpoint = originalStart.plus(java.time.Duration.between(originalStart, originalEnd).dividedBy(2))
        EntryTimePickerDialog(
            title = stringResource(R.string.split_entry_title),
            initial = midpoint,
            testTag = EditTimeEntryTestTags.SPLIT_TIME_PICKER,
            onDismiss = { showSplitPicker = false },
            onConfirm = { hour, minute ->
                showSplitPicker = false
                // The clock time on whichever day the entry contains it, so an entry running past
                // midnight can be split after midnight too. Boundary and outside picks split
                // nothing and say so; the repository re-validates.
                val splitAt = resolveSplitInstant(originalStart, originalEnd, hour, minute)
                if (splitAt != null) {
                    onSplit(formatTimeEntryInstant(splitAt))
                    onDismiss()
                } else {
                    splitOutsideEntry = true
                }
            },
        )
    }

    if (templatePickerOpen) {
        OptionPickerDialog(
            title = stringResource(R.string.templates_use_template),
            options = templates,
            selected = null,
            label = { templateDisplayLabel(it, projects) },
            onSelect = { template ->
                val resolution = TemplateResolver.resolve(template, projects, tasks, tags)
                description = template.description ?: ""
                projectId = resolution.projectId
                taskId = resolution.taskId
                selectedTags = resolution.tagIds
                billable = resolution.billable
            },
            onDismiss = { templatePickerOpen = false },
        )
    }
}

/**
 * The instant to split an entry from [start] to [end] at the picked clock time: that time on the
 * first day within the entry where it falls strictly inside, or null when it never does.
 */
internal fun resolveSplitInstant(start: ZonedDateTime, end: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime? {
    var day = start.toLocalDate()
    val lastDay = end.toLocalDate()
    while (!day.isAfter(lastDay)) {
        val candidate = ZonedDateTime.of(day, java.time.LocalTime.of(hour, minute), start.zone)
        if (candidate.isAfter(start) && candidate.isBefore(end)) return candidate
        day = day.plusDays(1)
    }
    return null
}

private enum class TimeField { Start, End }

@Composable
private fun EntryTimePickerDialog(
    title: String,
    initial: ZonedDateTime,
    testTag: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    AppTimePickerDialog(
        title = title,
        initialHour = initial.hour,
        initialMinute = initial.minute,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        pickerModifier = testTag?.let(Modifier::testTag) ?: Modifier,
    )
}

internal fun canDismissTimeEntryFormSheet(
    hasTimePicker: Boolean,
    hasDatePicker: Boolean,
    hasSplitPicker: Boolean,
    hasTemplatePicker: Boolean = false,
): Boolean = !hasTimePicker && !hasDatePicker && !hasSplitPicker && !hasTemplatePicker

private const val FULL_ROTATION_DEGREES = 360f
private const val SECONDS_PER_HOUR_LONG = 3600L
private const val SECONDS_PER_MINUTE_LONG = 60L
private const val FILTER_ENTER_DURATION_MS = 180
private const val FILTER_EXPAND_DURATION_MS = 220
private const val FILTER_EXIT_DURATION_MS = 120
private const val FILTER_COLLAPSE_DURATION_MS = 180
private const val SYNC_ROTATION_DURATION_MS = 900
private const val ALTERNATING_ROW_COUNT = 2
private const val GHOST_PRIMARY_WIDTH = 0.62f
private const val GHOST_SECONDARY_WIDTH = 0.45f
private const val FAB_SCRIM_ALPHA = 0.32f
private const val HISTORY_PLACEHOLDER_COUNT = 4
private const val RECENT_ENTRIES_LIMIT = 5
private const val DURATION_STEP_MINUTES = 15L
private const val MINIMUM_DURATION_MINUTES = 1L
private const val MAX_CROSS_MIDNIGHT_HOURS = 18L
private const val LONG_DURATION_WARNING_HOURS = 12L
private const val LAST_7_DAYS_OFFSET = 6L

/** How long a duplicate/split waits to appear in the list before its editor is given up. */
private const val ENTRY_TO_EDIT_WAIT_MS = 5_000L

/** Clock-style duration like the iOS timer: "37:03" under an hour, "1:05:00" from an hour. */
internal fun formatElapsedTime(seconds: Long): String {
    // Defensive floor: a device clock behind the entry's start must never render as "-1:-5:-3".
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / SECONDS_PER_HOUR_LONG
    val minutes = (safeSeconds % SECONDS_PER_HOUR_LONG) / SECONDS_PER_MINUTE_LONG
    val secs = safeSeconds % SECONDS_PER_MINUTE_LONG
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, secs)
    }
}

/**
 * Deterministic client-side validation for a manually edited or created time entry. Server policy
 * stays authoritative (see [EntryTrustRules]); these are local guards so the user never silently
 * creates an invalid interval, and is warned before saving an unusually long or overlapping entry.
 * Warnings do not block: an explicit Save is the user's confirmation. Multi-day entries are valid;
 * the server remains authoritative about any organization-specific duration limit.
 */
internal object EntryTimeValidator {
    /** An end clock-time earlier than start rolls to the next day only within this span; beyond it
     *  the inversion is treated as a mistake rather than an intended overnight shift. */
    val MAX_CROSS_MIDNIGHT: java.time.Duration = java.time.Duration.ofHours(MAX_CROSS_MIDNIGHT_HOURS)

    /** Durations at or above this are plausible but worth confirming before saving. */
    val LONG_DURATION_WARNING: java.time.Duration = java.time.Duration.ofHours(LONG_DURATION_WARNING_HOURS)
    enum class Error { END_NOT_AFTER_START }
    enum class Warning { LONG_DURATION, OVERLAP, OVERLAP_POLICY }

    data class Result(val error: Error?, val warnings: List<Warning>) {
        val canSave: Boolean get() = error == null
    }

    /**
     * Resolve an end clock-time [sameDayEnd] that shares [start]'s date. Returns the same-day value
     * when it is after start, the next-day value for a plausible overnight entry, or null when the
     * only rollover interpretation would be implausibly long — signalling the caller to surface an
     * end-before-start error instead of silently rolling over into a ~24h entry.
     */
    fun resolveEnd(start: ZonedDateTime, sameDayEnd: ZonedDateTime): ZonedDateTime? {
        if (sameDayEnd.isAfter(start)) return sameDayEnd
        val rolled = sameDayEnd.plusDays(1)
        return rolled.takeIf { java.time.Duration.between(start, it) <= MAX_CROSS_MIDNIGHT }
    }

    fun evaluate(start: ZonedDateTime, end: ZonedDateTime, overlaps: Boolean = false, overlapProhibited: Boolean = false): Result {
        val duration = java.time.Duration.between(start, end)
        val error = when {
            !end.isAfter(start) -> Error.END_NOT_AFTER_START
            else -> null
        }
        val warnings = buildList {
            if (error == null && duration >= LONG_DURATION_WARNING) add(Warning.LONG_DURATION)
            if (overlaps) add(if (overlapProhibited) Warning.OVERLAP_POLICY else Warning.OVERLAP)
        }
        return Result(error, warnings)
    }
}

/** Inline error/warning banner for the create/edit time-entry sheets. */
@Composable
internal fun EntryValidationBanner(result: EntryTimeValidator.Result, durationHours: Long) {
    if (result.canSave && result.warnings.isEmpty()) return
    val isError = !result.canSave
    Surface(
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag(dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags.VALIDATION_BANNER),
    ) {
        val contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            result.error?.let { error ->
                Text(
                    text = stringResource(
                        when (error) {
                            EntryTimeValidator.Error.END_NOT_AFTER_START -> R.string.entry_error_end_before_start
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
            result.warnings.forEach { warning ->
                Text(
                    text = when (warning) {
                        EntryTimeValidator.Warning.LONG_DURATION ->
                            pluralStringResource(R.plurals.entry_warning_long_duration, durationHours.toInt(), durationHours)
                        EntryTimeValidator.Warning.OVERLAP ->
                            stringResource(R.string.entry_warning_overlap)
                        EntryTimeValidator.Warning.OVERLAP_POLICY ->
                            stringResource(R.string.entry_warning_overlap_policy)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
        }
    }
}

/**
 * Format date for display
 */
internal fun formatDate(date: LocalDate, context: android.content.Context, zone: ZoneId, locale: Locale): String {
    val today = LocalDate.now(zone)
    return when {
        date == today -> context.getString(R.string.today)
        date == today.minusDays(1) -> context.getString(R.string.yesterday)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
}

// Shared formatter instances: DateTimeFormatter.ofPattern() builds a new parser every call,
// which is measurable when every visible history row formats its time range during a scroll.
private val hourMinuteFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Format time range
 */
internal fun formatTimeRange(
    start: String,
    end: String?,
    zone: ZoneId,
    locale: Locale = Locale.getDefault(),
    nowLabel: String = "now",
    invalidLabel: String = "Invalid time",
): String {
    val startValue = runCatching { ZonedDateTime.parse(start).withZoneSameInstant(zone) }.getOrNull()
        ?: return invalidLabel
    val startFormatted = startValue.format(hourMinuteFormatter)
    return if (end != null) {
        val endValue = runCatching { ZonedDateTime.parse(end).withZoneSameInstant(zone) }.getOrNull()
            ?: return invalidLabel
        val endFormatted = endValue.format(hourMinuteFormatter)
        val startDate = startValue.toLocalDate()
        val endDate = endValue.toLocalDate()
        if (startDate == endDate) {
            "$startFormatted - $endFormatted"
        } else {
            "${startDate.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale))} $startFormatted - " +
                "${endDate.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale))} $endFormatted"
        }
    } else {
        "$startFormatted - $nowLabel"
    }
}

internal fun groupCompletedEntriesByLocalDay(entries: List<TimeEntry>, zone: ZoneId, now: Instant): Map<LocalDate, List<TimeEntry>> =
    groupEntriesByLocalDay(entries, zone, now, includeRunning = false)

/**
 * Work entries by each local day they overlap, newest day first. Running entries are left out,
 * as the docked timer shows them, unless [includeRunning] (the "Running" search option).
 */
internal fun groupEntriesByLocalDay(
    entries: List<TimeEntry>,
    zone: ZoneId,
    now: Instant,
    includeRunning: Boolean,
): Map<LocalDate, List<TimeEntry>> = entries
    .asSequence()
    .filter { includeRunning || isCompletedTimeEntry(it) }
    .filter(::isWorkTimeEntry)
    .flatMap { entry -> timeEntryLocalDaySlices(entry, zone, now).asSequence().map { it.date to entry } }
    .groupBy({ it.first }, { it.second })
    .toSortedMap(compareByDescending { it })

/** Date-picker millis are UTC-midnight instants; resolve them back to the picked date. */

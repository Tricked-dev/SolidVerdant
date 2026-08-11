/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.TimeEntryType
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.domain.time.isCompletedTimeEntry
import dev.tricked.solidverdant.domain.time.isRunningTimeEntry
import dev.tricked.solidverdant.ui.components.EditTimeEntryDialog
import dev.tricked.solidverdant.ui.components.ErrorState
import dev.tricked.solidverdant.ui.components.SyncChip
import dev.tricked.solidverdant.ui.theme.Dimens
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    organizationId: String,
    memberId: String,
    initialDate: LocalDate? = null,
    onInitialDateConsumed: () -> Unit = {},
    runningEntry: TimeEntry? = null,
    elapsedSeconds: StateFlow<Long>? = null,
    projects: List<Project>,
    clients: List<Client> = emptyList(),
    tasks: List<Task>,
    tags: List<Tag>,
    onSaveEntry: (TimeEntry, String?, String?, String?, List<String>, Boolean, String, String?) -> Unit,
    onMoveEntry: (TimeEntry, String, String) -> Unit = { _, _, _ -> },
    onCreateEntry: (String?, String?, String?, List<String>, Boolean, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onCreateProject: ((String, String?, (Result<Project>) -> Unit) -> Unit)? = null,
    onCreateClient: ((String, (Result<Client>) -> Unit) -> Unit)? = null,
    onCreateTask: ((String, String, (Result<Task>) -> Unit) -> Unit)? = null,
    onCreateTag: ((String, (Result<Tag>) -> Unit) -> Unit)? = null,
    breaksEnabled: Boolean = false,
    onCreateBreakEntry: (String?, String, String) -> Unit = { _, _, _ -> },
    onDeleteEntry: (TimeEntry) -> Unit = {},
    onDuplicateEntry: (String) -> Unit = {},
    onSplitEntry: (String, String) -> Unit = { _, _ -> },
    onStopEntry: (TimeEntry) -> Unit = {},
    onUndoDelete: (TimeEntry) -> Unit = {},
    onRetrySyncEntry: (String) -> Unit = {},
    onDiscardFailedSync: (String) -> Unit = {},
    onOpenSyncCenter: () -> Unit = {},
    preventOverlap: Boolean = false,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    LaunchedEffect(organizationId, memberId) { viewModel.setOrganization(organizationId, memberId) }
    LaunchedEffect(initialDate) {
        initialDate?.let {
            viewModel.selectDate(it)
            onInitialDateConsumed()
        }
    }
    val state by viewModel.uiState.collectAsState()
    val syncOperationByEntryId = remember(state.syncOperations) { worstSyncOperationsByEntryId(state.syncOperations) }
    var editing by remember { mutableStateOf<TimeEntry?>(null) }
    var creatingRange by remember { mutableStateOf<CalendarTimeRange?>(null) }
    var creatingBreakRange by remember { mutableStateOf<CalendarTimeRange?>(null) }
    var contextEntry by remember { mutableStateOf<TimeEntry?>(null) }
    var splitTarget by remember { mutableStateOf<TimeEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<TimeEntry?>(null) }
    var deletedEntry by remember { mutableStateOf<TimeEntry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val moveOverlapMessage = stringResource(R.string.entry_warning_overlap)
    val calendarEntries = remember(state.bucketsByDate) {
        state.bucketsByDate.values.asSequence()
            .flatMap { it.entries.asSequence() }
            .distinctBy { it.id }
            .toList()
    }
    val visibleRunningEntry = runningEntry?.takeIf(::isRunningTimeEntry)
    val moveEntryWithWarning: (TimeEntry, String, String) -> Unit = { entry, start, end ->
        if (calendarMoveOverlapsExisting(entry, start, end, calendarEntries)) {
            coroutineScope.launch { snackbarHostState.showSnackbar(moveOverlapMessage) }
        }
        onMoveEntry(entry, start, end)
    }
    // Progressive disclosure: the overlay controls live behind an app-bar toggle instead of a
    // persistent bar, so the default calendar keeps its full height for the grid.
    var showOverlaySheet by remember { mutableStateOf(false) }
    val overlaySheetState = rememberModalBottomSheetState()
    var showSettingsSheet by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState()
    val entryActionsSheetState = rememberModalBottomSheetState()

    val context = LocalContext.current
    val activity = context as? Activity

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Keep the overlay preference on regardless; the controls explain the current permission
        // state, so a denial surfaces a recovery path instead of silently disabling the toggle.
        viewModel.onCalendarPermissionChanged(granted)
    }

    // Re-check the grant whenever the screen resumes (covers first composition and returning from
    // system settings), so a permission revoked or granted outside the app is reflected immediately.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onCalendarPermissionChanged(hasCalendarPermission(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showRationale = activity != null &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR)

    val requestPermission: () -> Unit = {
        viewModel.onPermissionRequested()
        permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
    }
    val openAppSettings: () -> Unit = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
    val toggleOverlay: (Boolean) -> Unit = { want ->
        viewModel.setOverlayEnabled(want)
        if (want && !hasCalendarPermission(context)) requestPermission()
    }

    val entryDeletedMessage = stringResource(R.string.entry_deleted)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(deletedEntry) {
        val entry = deletedEntry ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = entryDeletedMessage,
            actionLabel = undoLabel,
            withDismissAction = true,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onUndoDelete(entry)
        deletedEntry = null
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CalendarToolbar(
                state = state,
                onModeSelected = viewModel::setViewMode,
                onAddEntry = {
                    creatingRange = defaultCalendarTimeRange(
                        day = state.selectedDate,
                        zone = state.zone,
                        settings = state.calendarSettings,
                    )
                },
                breaksEnabled = breaksEnabled,
                onAddBreak = {
                    creatingBreakRange = defaultCalendarTimeRange(
                        day = state.selectedDate,
                        zone = state.zone,
                        settings = state.calendarSettings,
                    )
                },
                onOpenOverlay = { showOverlaySheet = true },
                onOpenSettings = { showSettingsSheet = true },
            )
            visibleRunningEntry?.let { entry ->
                CalendarRunningTimerCard(
                    entry = entry,
                    elapsedSeconds = elapsedSeconds,
                    onEdit = { editing = entry },
                )
            }
            if (state.loadError && !state.isStale) {
                ErrorState(
                    text = stringResource(R.string.calendar_load_error),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.weight(1f).testTag(CalendarTestTags.LOAD_ERROR),
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    if (state.loadError) {
                        ErrorState(
                            text = stringResource(R.string.calendar_load_error_cached),
                            onRetry = viewModel::retryLoad,
                            modifier = Modifier.testTag(CalendarTestTags.LOAD_ERROR),
                        )
                    }
                    CalendarBody(
                        state = state,
                        viewModel = viewModel,
                        projects = projects,
                        clients = clients,
                        tasks = tasks,
                        onEntryClick = { editing = it },
                        onEntryLongPress = { contextEntry = it },
                        syncStatusByEntryId = syncOperationByEntryId.mapValues { (_, operation) -> operation.status },
                        onMoveEntry = moveEntryWithWarning,
                        onCreateRange = { creatingRange = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(Dimens.Space16),
        )
    }

    contextEntry?.let { entry ->
        CalendarEntryActionsSheet(
            entry = entry,
            project = projects.firstOrNull { it.id == entry.projectId },
            task = tasks.firstOrNull { it.id == entry.taskId },
            client = projects.firstOrNull { it.id == entry.projectId }?.clientId?.let { clientId ->
                clients.firstOrNull { it.id == clientId }
            },
            syncOperation = syncOperationByEntryId[entry.id],
            sheetState = entryActionsSheetState,
            onDismiss = { contextEntry = null },
            onEdit = {
                contextEntry = null
                editing = entry
            },
            onDuplicate = {
                contextEntry = null
                onDuplicateEntry(entry.id)
            },
            onSplit = {
                contextEntry = null
                splitTarget = entry
            },
            onStop = {
                contextEntry = null
                onStopEntry(entry)
            },
            onDelete = {
                contextEntry = null
                deleteTarget = entry
            },
            onRetrySync = {
                contextEntry = null
                onRetrySyncEntry(entry.id)
            },
            onDiscardFailedSync = {
                contextEntry = null
                onDiscardFailedSync(entry.id)
            },
            onOpenSyncCenter = {
                contextEntry = null
                onOpenSyncCenter()
            },
        )
    }

    splitTarget?.let { entry ->
        CalendarSplitDialog(
            entry = entry,
            zone = state.zone,
            onDismiss = { splitTarget = null },
            onConfirm = { at ->
                splitTarget = null
                onSplitEntry(entry.id, at)
            },
        )
    }

    editing?.let { entry ->
        EditTimeEntryDialog(
            entry = entry,
            zone = state.zone,
            projects = projects,
            clients = clients,
            tasks = tasks,
            tags = tags,
            onDismiss = { editing = null },
            onSave = { desc, projectId, taskId, tagIds, billable, start, end ->
                onSaveEntry(entry, desc, projectId, taskId, tagIds, billable, start, end)
                editing = null
            },
            existingEntries = state.bucketsByDate.values.flatMap { it.entries }.distinctBy { it.id },
            preventOverlap = preventOverlap,
            isBreak = entry.type == TimeEntryType.BREAK,
            onCreateProject = onCreateProject,
            onCreateClient = onCreateClient,
            onCreateTask = onCreateTask,
            onCreateTag = onCreateTag,
            onDelete = {
                editing = null
                deleteTarget = entry
            },
        )
    }

    creatingRange?.let { range ->
        EditTimeEntryDialog(
            entry = null,
            zone = state.zone,
            projects = projects,
            clients = clients,
            tasks = tasks,
            tags = tags,
            suggestedStart = range.start,
            suggestedEnd = range.end,
            existingEntries = state.bucketsByDate.values.flatMap { it.entries }.distinctBy { it.id },
            preventOverlap = preventOverlap,
            onCreateProject = onCreateProject,
            onCreateClient = onCreateClient,
            onCreateTask = onCreateTask,
            onCreateTag = onCreateTag,
            onDismiss = { creatingRange = null },
            onSave = { desc, projectId, taskId, tagIds, billable, start, end ->
                end?.let {
                    onCreateEntry(desc, projectId, taskId, tagIds, billable, start, it)
                    creatingRange = null
                }
            },
        )
    }

    creatingBreakRange?.let { range ->
        EditTimeEntryDialog(
            entry = null,
            zone = state.zone,
            projects = emptyList(),
            clients = emptyList(),
            tasks = emptyList(),
            tags = emptyList(),
            suggestedStart = range.start,
            suggestedEnd = range.end,
            existingEntries = state.bucketsByDate.values.flatMap { it.entries }.distinctBy { it.id },
            preventOverlap = false,
            isBreak = true,
            onDismiss = { creatingBreakRange = null },
            onSave = { desc, _, _, _, _, start, end ->
                end?.let {
                    onCreateBreakEntry(desc, start, it)
                    creatingBreakRange = null
                }
            },
        )
    }

    if (showOverlaySheet) {
        CalendarOverlaySheet(
            state = state,
            sheetState = overlaySheetState,
            showRationale = showRationale,
            onDismiss = { showOverlaySheet = false },
            onToggleOverlay = toggleOverlay,
            onRequestPermission = requestPermission,
            onOpenAppSettings = openAppSettings,
            onToggleCalendar = viewModel::toggleCalendarSelected,
            onRetry = viewModel::retryOverlay,
        )
    }

    if (showSettingsSheet) {
        CalendarSettingsSheet(
            settings = state.calendarSettings,
            sheetState = settingsSheetState,
            onSettingsChanged = viewModel::updateCalendarSettings,
            onDismiss = { showSettingsSheet = false },
        )
    }

    deleteTarget?.let { entry ->
        val discard = entry.id.startsWith("local-")
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = {
                Text(
                    stringResource(
                        if (discard) {
                            R.string.calendar_discard_entry_title
                        } else {
                            R.string.calendar_delete_entry_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (discard) {
                            R.string.calendar_discard_entry_message
                        } else {
                            R.string.calendar_delete_entry_message
                        },
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteEntry(entry)
                        deletedEntry = entry
                        deleteTarget = null
                    },
                    modifier = Modifier.testTag(CalendarTestTags.DELETE_CONFIRM),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(if (discard) R.string.calendar_action_discard else R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTarget = null },
                    modifier = Modifier.testTag(CalendarTestTags.DELETE_CANCEL),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarEntryActionsSheet(
    entry: TimeEntry,
    project: Project?,
    task: Task?,
    client: Client?,
    syncOperation: TimeEntryRepository.SyncOperation?,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onSplit: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onRetrySync: () -> Unit,
    onDiscardFailedSync: () -> Unit,
    onOpenSyncCenter: () -> Unit,
) {
    val running = isRunningTimeEntry(entry)
    val unsynced = entry.id.startsWith("local-")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CalendarTestTags.ENTRY_ACTIONS)
                .padding(horizontal = Dimens.Space16)
                .padding(bottom = Dimens.Space24),
        ) {
            Text(stringResource(R.string.calendar_entry_actions), style = MaterialTheme.typography.titleLarge)
            Text(
                text = entry.description?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.calendar_entry_untitled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Dimens.Space4),
            )
            calendarEntryMetadata(entry, project?.name, task?.name, client?.name).let { metadata ->
                metadata.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Dimens.Space4),
                    )
                }
                metadata.durationSeconds?.let { duration ->
                    Text(
                        text = "${stringResource(R.string.total_time)}: ${formatDuration(duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.heightIn(min = Dimens.Space8))
            syncOperation?.let { operation ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CalendarTestTags.SYNC_STATUS),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SyncChip(status = operation.status)
                    }
                    Text(
                        text = stringResource(
                            when (operation.status) {
                                TimeEntryRepository.EntrySyncStatus.PENDING,
                                TimeEntryRepository.EntrySyncStatus.RETRYING,
                                -> R.string.calendar_sync_pending_detail
                                TimeEntryRepository.EntrySyncStatus.FAILED -> R.string.calendar_sync_failed_detail
                                TimeEntryRepository.EntrySyncStatus.CONFLICT -> R.string.calendar_sync_conflict_detail
                                TimeEntryRepository.EntrySyncStatus.SYNCED -> R.string.calendar_sync_synced_detail
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.Space4),
                    )
                }
                when (operation.status) {
                    TimeEntryRepository.EntrySyncStatus.FAILED -> {
                        CalendarEntryActionButton(
                            label = stringResource(R.string.sync_retry),
                            onClick = onRetrySync,
                            actionTestTag = CalendarTestTags.SYNC_RETRY,
                        )
                        CalendarEntryActionButton(
                            label = stringResource(R.string.sync_discard),
                            onClick = onDiscardFailedSync,
                            actionTestTag = CalendarTestTags.SYNC_DISCARD,
                        )
                    }
                    TimeEntryRepository.EntrySyncStatus.CONFLICT -> {
                        CalendarEntryActionButton(stringResource(R.string.sync_center_title), onOpenSyncCenter)
                    }
                    else -> Unit
                }
            }
            CalendarEntryActionButton(
                label = stringResource(if (running) R.string.edit_start_time else R.string.edit),
                onClick = onEdit,
                actionTestTag = if (running) CalendarTestTags.EDIT_START_TIME else null,
            )
            if (running) {
                CalendarEntryActionButton(
                    label = stringResource(R.string.stop_tracking),
                    onClick = onStop,
                    actionTestTag = CalendarTestTags.STOP_ENTRY,
                )
            }
            if (!running && isCompletedTimeEntry(entry)) {
                CalendarEntryActionButton(
                    label = stringResource(R.string.duplicate_entry),
                    onClick = onDuplicate,
                    actionTestTag = CalendarTestTags.DUPLICATE_ENTRY,
                )
                CalendarEntryActionButton(
                    label = stringResource(R.string.split_entry),
                    onClick = onSplit,
                    actionTestTag = CalendarTestTags.SPLIT_ENTRY,
                )
            }
            CalendarEntryActionButton(
                label = stringResource(if (unsynced) R.string.calendar_action_discard else R.string.delete),
                onClick = onDelete,
                actionTestTag = CalendarTestTags.DELETE_ENTRY,
            )
        }
    }
}

@Composable
private fun CalendarEntryActionButton(label: String, onClick: () -> Unit, actionTestTag: String? = null) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTouchTarget)
            .then(actionTestTag?.let { Modifier.testTag(it) } ?: Modifier),
    ) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CalendarRunningTimerCard(entry: TimeEntry, elapsedSeconds: StateFlow<Long>?, onEdit: () -> Unit) {
    val elapsedState = elapsedSeconds?.collectAsState()
    val liveElapsedSeconds = elapsedState?.value ?: run {
        val now = rememberCalendarNow(secondPrecision = true)
        entryDurationSeconds(entry, now)
    }
    val title = entry.description?.trim()?.takeIf(String::isNotEmpty)
        ?: stringResource(R.string.calendar_entry_untitled)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Space12)
            .padding(bottom = Dimens.Space8)
            .testTag(CalendarTestTags.RUNNING_TIMER),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.CardContentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(Dimens.Space12))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.calendar_timer_running),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatRunningDuration(liveElapsedSeconds),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            IconButton(
                onClick = onEdit,
                modifier = Modifier.testTag(CalendarTestTags.RUNNING_TIMER_EDIT),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_start_time),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarSplitDialog(entry: TimeEntry, zone: java.time.ZoneId, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val originalStart = remember(entry.id, entry.start, zone) {
        runCatching {
            ZonedDateTime.parse(entry.start, DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(zone)
        }.getOrNull()
    }
    val originalEnd = remember(entry.id, entry.end, zone) {
        entry.end?.let {
            runCatching {
                ZonedDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(zone)
            }.getOrNull()
        }
    }
    if (originalStart == null || originalEnd == null || !originalEnd.isAfter(originalStart)) {
        LaunchedEffect(entry.id) { onDismiss() }
        return
    }

    val midpoint = remember(originalStart, originalEnd) {
        originalStart.plusSeconds(java.time.Duration.between(originalStart, originalEnd).seconds / 2)
    }
    var selectedDate by remember(entry.id, zone) { mutableStateOf(midpoint.toLocalDate()) }
    val timeState = rememberTimePickerState(
        initialHour = midpoint.hour,
        initialMinute = midpoint.minute,
        is24Hour = true,
    )
    var invalid by remember(entry.id) { mutableStateOf(false) }
    var showDatePicker by remember(entry.id) { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_entry_title)) },
        text = {
            Column {
                Text(stringResource(R.string.calendar_split_help))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouchTarget),
                ) {
                    Text(selectedDate.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)))
                }
                TimePicker(state = timeState, modifier = Modifier.testTag(CalendarTestTags.SPLIT_PICKER))
                if (invalid) {
                    Text(
                        text = stringResource(R.string.calendar_split_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val candidate = selectedDate.atTime(timeState.hour, timeState.minute).atZone(zone)
                    if (candidate.isAfter(originalStart) && candidate.isBefore(originalEnd)) {
                        onConfirm(candidate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    } else {
                        invalid = true
                    }
                },
                modifier = Modifier.testTag(CalendarTestTags.SPLIT_CONFIRM),
            ) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(CalendarTestTags.SPLIT_CANCEL),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun CalendarToolbar(
    state: CalendarUiState,
    onModeSelected: (CalendarViewMode) -> Unit,
    onAddEntry: () -> Unit,
    breaksEnabled: Boolean,
    onAddBreak: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val modes = remember {
        listOf(
            CalendarViewMode.MONTH to R.string.calendar_view_month,
            CalendarViewMode.WEEK to R.string.calendar_view_week,
            CalendarViewMode.DAY to R.string.calendar_view_day,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Space12, vertical = Dimens.Space8),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            modes.forEachIndexed { index, (mode, labelRes) ->
                SegmentedButton(
                    selected = state.viewMode == mode,
                    onClick = { onModeSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    modifier = Modifier.testTag(
                        when (mode) {
                            CalendarViewMode.MONTH -> CalendarTestTags.MODE_MONTH
                            CalendarViewMode.WEEK -> CalendarTestTags.MODE_WEEK
                            CalendarViewMode.DAY -> CalendarTestTags.MODE_DAY
                        },
                    ),
                ) {
                    Text(stringResource(labelRes))
                }
            }
        }
        IconButton(
            onClick = onAddEntry,
            modifier = Modifier.testTag(CalendarTestTags.ADD_ENTRY),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.add_time_entry),
            )
        }
        if (breaksEnabled) {
            var addMenuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { addMenuExpanded = true },
                    modifier = Modifier.testTag(CalendarTestTags.ADD_BREAK),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.calendar_more_actions),
                    )
                }
                DropdownMenu(
                    expanded = addMenuExpanded,
                    onDismissRequest = { addMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.calendar_add_break)) },
                        modifier = Modifier.testTag(CalendarTestTags.ADD_BREAK_MENU),
                        onClick = {
                            addMenuExpanded = false
                            onAddBreak()
                        },
                    )
                }
            }
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.testTag(CalendarTestTags.SETTINGS),
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.calendar_settings),
            )
        }
        if (state.viewMode != CalendarViewMode.MONTH) {
            IconButton(onClick = onOpenOverlay) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = stringResource(R.string.calendar_overlay_settings),
                    tint = if (state.overlayEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarSettingsSheet(
    settings: CalendarGridSettings,
    sheetState: androidx.compose.material3.SheetState,
    onSettingsChanged: (CalendarGridSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val snapOptions = CalendarGridSettings.SNAP_MINUTES.map { minutes ->
        minutes to stringResource(R.string.calendar_settings_minutes, minutes)
    }
    val densityOptions = listOf(
        CalendarGridDensity.COMPACT to R.string.calendar_settings_density_compact,
        CalendarGridDensity.COMFORTABLE to R.string.calendar_settings_density_comfortable,
        CalendarGridDensity.SPACIOUS to R.string.calendar_settings_density_spacious,
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CalendarTestTags.SETTINGS_SHEET)
                .padding(horizontal = Dimens.Space16)
                .padding(bottom = Dimens.Space24),
        ) {
            Text(stringResource(R.string.calendar_settings), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.heightIn(min = Dimens.Space8))
            CalendarSettingDropdown(
                label = stringResource(R.string.calendar_settings_snap),
                value = stringResource(R.string.calendar_settings_minutes, settings.snapMinutes),
                options = snapOptions,
                onSelected = { minutes -> onSettingsChanged(settings.copy(snapMinutes = minutes)) },
                controlTestTag = CalendarTestTags.SETTINGS_SNAP,
            )
            Spacer(Modifier.heightIn(min = Dimens.Space12))
            Text(
                text = stringResource(R.string.calendar_settings_visible_hours),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CalendarSettingDropdown(
                    label = stringResource(R.string.calendar_settings_start),
                    value = stringResource(R.string.calendar_settings_hour, settings.startHour),
                    options = (CalendarGridSettings.MIN_START_HOUR until settings.endHour).map { hour ->
                        hour to stringResource(R.string.calendar_settings_hour, hour)
                    },
                    onSelected = { hour ->
                        onSettingsChanged(settings.copy(startHour = hour.coerceAtMost(settings.endHour - 1)))
                    },
                    controlTestTag = CalendarTestTags.SETTINGS_START,
                    modifier = Modifier.weight(1f),
                )
                CalendarSettingDropdown(
                    label = stringResource(R.string.calendar_settings_end),
                    value = stringResource(R.string.calendar_settings_hour, settings.endHour),
                    options = ((settings.startHour + 1)..CalendarGridSettings.MAX_END_HOUR).map { hour ->
                        hour to stringResource(R.string.calendar_settings_hour, hour)
                    },
                    onSelected = { hour -> onSettingsChanged(settings.copy(endHour = hour)) },
                    controlTestTag = CalendarTestTags.SETTINGS_END,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.heightIn(min = Dimens.Space12))
            Text(
                text = stringResource(R.string.calendar_settings_density),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                densityOptions.forEachIndexed { index, (density, label) ->
                    SegmentedButton(
                        selected = settings.density == density,
                        onClick = { onSettingsChanged(settings.copy(density = density)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = densityOptions.size),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(
                                when (density) {
                                    CalendarGridDensity.COMPACT -> CalendarTestTags.SETTINGS_DENSITY_COMPACT
                                    CalendarGridDensity.COMFORTABLE -> CalendarTestTags.SETTINGS_DENSITY_COMFORTABLE
                                    CalendarGridDensity.SPACIOUS -> CalendarTestTags.SETTINGS_DENSITY_SPACIOUS
                                },
                            ),
                    ) {
                        Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSettingDropdown(
    label: String,
    value: String,
    options: List<Pair<Int, String>>,
    onSelected: (Int) -> Unit,
    controlTestTag: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(label) { mutableStateOf(false) }
    Column(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.MinTouchTarget)
                .testTag(controlTestTag),
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text(
                    value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(CalendarTestTags.settingsValue(controlTestTag)),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (option, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    modifier = Modifier.testTag(CalendarTestTags.settingsOption(controlTestTag, option.toString())),
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CalendarBody(
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    projects: List<Project>,
    clients: List<Client>,
    tasks: List<Task>,
    onEntryClick: (TimeEntry) -> Unit,
    onEntryLongPress: (TimeEntry) -> Unit,
    syncStatusByEntryId: Map<String, TimeEntryRepository.EntrySyncStatus>,
    onMoveEntry: (TimeEntry, String, String) -> Unit,
    onCreateRange: (CalendarTimeRange) -> Unit,
    modifier: Modifier,
) {
    val bodyModifier = modifier
        .fillMaxWidth()
        .then(if (!state.isLoading) Modifier.testTag(CalendarTestTags.CONTENT_READY) else Modifier)
    when (state.viewMode) {
        CalendarViewMode.MONTH -> MonthCalendarView(
            state = state,
            onSelectDate = viewModel::selectDate,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onEntryClick = onEntryClick,
            onEntryLongPress = onEntryLongPress,
            syncStatusByEntryId = syncStatusByEntryId,
            onMoveEntry = onMoveEntry,
            onCreateRange = onCreateRange,
            projects = projects,
            tasks = tasks,
            clients = clients,
            modifier = bodyModifier,
        )

        else -> BoxWithConstraints(modifier = bodyModifier) {
            val availableWidth = maxWidth
            LaunchedEffect(availableWidth, state.viewMode) {
                if (state.viewMode == CalendarViewMode.WEEK) {
                    viewModel.setVisibleDayCount(
                        if (availableWidth < Dimens.NarrowCalendarWidth) NARROW_CALENDAR_DAYS else FULL_WEEK_DAYS,
                    )
                }
            }
            WeekCalendarView(
                state = state,
                onSelectDate = viewModel::selectDate,
                onEntryClick = onEntryClick,
                onEntryLongPress = onEntryLongPress,
                syncStatusByEntryId = syncStatusByEntryId,
                onMoveEntry = onMoveEntry,
                onCreateRange = onCreateRange,
                onPrevious = viewModel::pageBackward,
                onNext = viewModel::pageForward,
                onToday = viewModel::jumpToToday,
                projects = projects,
                tasks = tasks,
                clients = clients,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarOverlaySheet(
    state: CalendarUiState,
    sheetState: androidx.compose.material3.SheetState,
    showRationale: Boolean,
    onDismiss: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onToggleCalendar: (String) -> Unit,
    onRetry: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CalendarOverlayControls(
            state = state,
            showRationale = showRationale,
            onToggleOverlay = onToggleOverlay,
            onRequestPermission = onRequestPermission,
            onOpenAppSettings = onOpenAppSettings,
            onToggleCalendar = onToggleCalendar,
            onRetry = onRetry,
            modifier = Modifier
                .padding(horizontal = Dimens.Space16)
                .padding(bottom = Dimens.Space24),
        )
    }
}

private const val NARROW_CALENDAR_DAYS = 3
private const val FULL_WEEK_DAYS = 7

private fun hasCalendarPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

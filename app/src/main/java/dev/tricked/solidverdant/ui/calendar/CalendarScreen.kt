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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.ui.components.EditTimeEntryDialog
import dev.tricked.solidverdant.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    organizationId: String,
    memberId: String,
    projects: List<Project>,
    tasks: List<Task>,
    tags: List<Tag>,
    onSaveEntry: (TimeEntry, String?, String?, String?, List<String>, Boolean, String, String?) -> Unit,
    onMoveEntry: (TimeEntry, String, String) -> Unit = { _, _, _ -> },
    onCreateEntry: (String?, String?, String?, List<String>, Boolean, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteEntry: (TimeEntry) -> Unit = {},
    onUndoDelete: (TimeEntry) -> Unit = {},
    preventOverlap: Boolean = false,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    LaunchedEffect(organizationId, memberId) { viewModel.setOrganization(organizationId, memberId) }
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<TimeEntry?>(null) }
    var creatingRange by remember { mutableStateOf<CalendarTimeRange?>(null) }
    var deleteTarget by remember { mutableStateOf<TimeEntry?>(null) }
    var deletedEntry by remember { mutableStateOf<TimeEntry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Progressive disclosure: the overlay controls live behind an app-bar toggle instead of a
    // persistent bar, so the default calendar keeps its full height for the grid.
    var showOverlaySheet by remember { mutableStateOf(false) }
    val overlaySheetState = rememberModalBottomSheetState()

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
                onAddEntry = { creatingRange = defaultCalendarTimeRange(state.selectedDate, state.zone) },
                onOpenOverlay = { showOverlaySheet = true },
            )
            CalendarBody(
                state = state,
                viewModel = viewModel,
                projects = projects,
                tasks = tasks,
                onEntryClick = { editing = it },
                onMoveEntry = onMoveEntry,
                onCreateRange = { creatingRange = it },
                modifier = Modifier.weight(1f),
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(Dimens.Space16),
        )
    }

    editing?.let { entry ->
        EditTimeEntryDialog(
            entry = entry,
            zone = state.zone,
            projects = projects,
            tasks = tasks,
            tags = tags,
            onDismiss = { editing = null },
            onSave = { desc, projectId, taskId, tagIds, billable, start, end ->
                onSaveEntry(entry, desc, projectId, taskId, tagIds, billable, start, end)
                editing = null
            },
            existingEntries = state.bucketsByDate.values.flatMap { it.entries }.distinctBy { it.id },
            preventOverlap = preventOverlap,
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
            tasks = tasks,
            tags = tags,
            suggestedStart = range.start,
            suggestedEnd = range.end,
            existingEntries = state.bucketsByDate.values.flatMap { it.entries }.distinctBy { it.id },
            preventOverlap = preventOverlap,
            onDismiss = { creatingRange = null },
            onSave = { desc, projectId, taskId, tagIds, billable, start, end ->
                end?.let {
                    onCreateEntry(desc, projectId, taskId, tagIds, billable, start, it)
                    creatingRange = null
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

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.calendar_delete_entry_title)) },
            text = { Text(stringResource(R.string.calendar_delete_entry_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteEntry(entry)
                        deletedEntry = entry
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CalendarToolbar(
    state: CalendarUiState,
    onModeSelected: (CalendarViewMode) -> Unit,
    onAddEntry: () -> Unit,
    onOpenOverlay: () -> Unit,
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
                    modifier = if (mode == CalendarViewMode.MONTH) {
                        Modifier.testTag(CalendarTestTags.MODE_MONTH)
                    } else {
                        Modifier
                    },
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

@Composable
private fun CalendarBody(
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    projects: List<Project>,
    tasks: List<Task>,
    onEntryClick: (TimeEntry) -> Unit,
    onMoveEntry: (TimeEntry, String, String) -> Unit,
    onCreateRange: (CalendarTimeRange) -> Unit,
    modifier: Modifier,
) {
    when (state.viewMode) {
        CalendarViewMode.MONTH -> MonthCalendarView(
            state = state,
            onSelectDate = viewModel::selectDate,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onEntryClick = onEntryClick,
            onMoveEntry = onMoveEntry,
            onCreateRange = onCreateRange,
            projects = projects,
            tasks = tasks,
            modifier = modifier.fillMaxWidth(),
        )

        else -> BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
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
                onMoveEntry = onMoveEntry,
                onCreateRange = onCreateRange,
                onPrevious = viewModel::pageBackward,
                onNext = viewModel::pageForward,
                onToday = viewModel::jumpToToday,
                projects = projects,
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

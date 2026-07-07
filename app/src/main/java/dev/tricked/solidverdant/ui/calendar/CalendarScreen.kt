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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.ui.components.EditTimeEntryDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    organizationId: String,
    memberId: String,
    projects: List<Project>,
    tasks: List<Task>,
    tags: List<Tag>,
    onSaveEntry: (TimeEntry, String?, String?, String?, List<String>, Boolean, String, String) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    LaunchedEffect(organizationId, memberId) { viewModel.setOrganization(organizationId, memberId) }
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<TimeEntry?>(null) }

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
        Unit
    }
    val toggleOverlay: (Boolean) -> Unit = { want ->
        viewModel.setOverlayEnabled(want)
        if (want && !hasCalendarPermission(context)) requestPermission()
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        val modes = remember {
            listOf(
                CalendarViewMode.MONTH to R.string.calendar_view_month,
                CalendarViewMode.WEEK to R.string.calendar_view_week,
                CalendarViewMode.DAY to R.string.calendar_view_day,
            )
        }
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            modes.forEachIndexed { index, (mode, labelRes) ->
                SegmentedButton(
                    selected = state.viewMode == mode,
                    onClick = { viewModel.setViewMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                ) {
                    Text(stringResource(labelRes))
                }
            }
        }

        when (state.viewMode) {
            CalendarViewMode.MONTH -> MonthCalendarView(
                state = state,
                onSelectDate = viewModel::selectDate,
                onPreviousMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                onEntryClick = { editing = it },
                projects = projects,
                tasks = tasks,
                tags = tags,
                modifier = Modifier.weight(1f),
            )

            else -> {
                CalendarOverlayControls(
                    state = state,
                    showRationale = showRationale,
                    onToggleOverlay = toggleOverlay,
                    onRequestPermission = requestPermission,
                    onOpenAppSettings = openAppSettings,
                    onToggleCalendar = viewModel::toggleCalendarSelected,
                    onRetry = viewModel::retryOverlay,
                )
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val availableWidth = maxWidth
                    // Fall back to a 3-day layout on narrow phones (gap analysis #20).
                    LaunchedEffect(availableWidth, state.viewMode) {
                        if (state.viewMode == CalendarViewMode.WEEK) {
                            viewModel.setVisibleDayCount(if (availableWidth < 600.dp) 3 else 7)
                        }
                    }
                    WeekCalendarView(
                        state = state,
                        onSelectDate = viewModel::selectDate,
                        onEntryClick = { editing = it },
                        onPrevious = viewModel::pageBackward,
                        onNext = viewModel::pageForward,
                        onToday = viewModel::jumpToToday,
                        projects = projects,
                    )
                }
            }
        }
    }

    editing?.let { entry ->
        EditTimeEntryDialog(
            entry = entry,
            projects = projects,
            tasks = tasks,
            tags = tags,
            onDismiss = { editing = null },
            onSave = { desc, projectId, taskId, tagIds, billable, start, end ->
                onSaveEntry(entry, desc, projectId, taskId, tagIds, billable, start, end)
                editing = null
            },
        )
    }
}

private fun hasCalendarPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.tricked.solidverdant.BuildConfig
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.data.repository.EntryTemplate
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.ui.templates.FavoriteTemplatesRow
import dev.tricked.solidverdant.ui.templates.ManageTemplatesViewModel
import dev.tricked.solidverdant.ui.templates.TemplateDraft
import dev.tricked.solidverdant.ui.templates.TemplateResolver
import dev.tricked.solidverdant.ui.templates.templateDisplayLabel
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import dev.tricked.solidverdant.data.local.AppThemeMode
import dev.tricked.solidverdant.ui.components.ProjectTaskDropdown as SharedProjectTaskDropdown
import dev.tricked.solidverdant.ui.components.SectionCard
import dev.tricked.solidverdant.ui.components.SyncChip
import dev.tricked.solidverdant.ui.theme.Dimens
import dev.tricked.solidverdant.util.IsoTimes
import dev.tricked.solidverdant.util.NotificationPermissionHelper
import dev.tricked.solidverdant.service.TimeTrackingNotificationService
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.Instant
import java.time.ZoneOffset
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Represents a selection of project and/or task
 */
sealed class ProjectTaskSelection {
    object NoProject : ProjectTaskSelection()
    data class ProjectOnly(val project: Project) : ProjectTaskSelection()
    data class ProjectWithTask(val project: Project, val task: Task) : ProjectTaskSelection()
}

/**
 * Tracking screen displaying current time tracking state and history
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrackingScreen(
    user: User?,
    memberships: List<Membership>,
    currentMembership: Membership?,
    serverEndpoint: String,
    clientId: String,
    uiState: TrackingUiState,
    elapsedSeconds: StateFlow<Long> = MutableStateFlow(uiState.elapsedSeconds),
    alwaysShowNotifications: Boolean,
    appTheme: AppThemeMode,
    optimisticRefresh: Boolean,
    longTimerHours: Int,
    editActiveEntryRequested: Boolean,
    onEditActiveEntryConsumed: () -> Unit,
    onAlwaysShowNotificationsChange: (Boolean) -> Unit,
    onAppThemeChange: (AppThemeMode) -> Unit,
    onOptimisticRefreshChange: (Boolean) -> Unit,
    onLongTimerHoursChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onMembershipChange: (Membership) -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onPauseTracking: () -> Unit,
    onResumeTracking: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onProjectChange: (String?) -> Unit,
    onTaskChange: (String?) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onBillableChange: (Boolean) -> Unit,
    onUpdateCurrentEntry: () -> Unit,
    onUpdatePastEntry: (TimeEntry, String?, String?, String?, List<String>, Boolean, String, String) -> Unit,
    onCreateEntry: (String?, String?, String?, List<String>, Boolean, String, String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onUndoDelete: (TimeEntry) -> Unit,
    onRetrySync: () -> Unit,
    onRetrySyncEntry: (String) -> Unit,
    onLoadMoreEntries: () -> Unit,
    onLoadNewerEntries: () -> Unit,
    onJumpToDate: (LocalDate) -> Unit,
    onHistoryJumpConsumed: () -> Unit,
    getGroupedEntries: () -> Map<LocalDate, List<TimeEntry>>
) {
    var showEditDialog by remember { mutableStateOf<TimeEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var organizationMenuExpanded by remember { mutableStateOf(false) }
    var hasUserScrolledHistory by remember { mutableStateOf(false) }
    var calendarInitialDate by remember { mutableStateOf<LocalDate?>(null) }
    var historyFilter by remember { mutableStateOf(HistoryFilter()) }
    var deletedEntry by remember { mutableStateOf<TimeEntry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var longTimerSnoozedUntil by remember { mutableStateOf(0L) }
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val wideHistoryListState = rememberLazyListState()
    val compactListState = rememberLazyListState()

    // Favorites & templates (gap analysis #1, #9). The template ViewModel resolves the current
    // organization itself; its catalogue includes archived/done items so availability can be shown.
    val templateViewModel: ManageTemplatesViewModel = hiltViewModel()
    val templateState by templateViewModel.uiState.collectAsState()
    val onSaveTemplateFromForm: (TemplateDraft) -> Unit = { draft ->
        templateViewModel.saveNewTemplate(draft)
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.templates_saved)) }
    }

    LaunchedEffect(editActiveEntryRequested, uiState.currentTimeEntry) {
        if (editActiveEntryRequested) {
            uiState.currentTimeEntry?.let { showEditDialog = it }
            if (uiState.currentTimeEntry != null) onEditActiveEntryConsumed()
        }
    }

    LaunchedEffect(historyFilter.startDate) {
        historyFilter.startDate?.let(onJumpToDate)
    }

    LaunchedEffect(deletedEntry) {
        val entry = deletedEntry ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.entry_deleted),
            actionLabel = context.getString(R.string.undo),
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoDelete(entry)
        deletedEntry = null
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

    LaunchedEffect(wideHistoryListState, uiState.timeEntries.size, uiState.hasMoreTimeEntries) {
        snapshotFlow {
            val info = wideHistoryListState.layoutInfo
            hasUserScrolledHistory && info.totalItemsCount > 0 &&
                (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >=
                info.totalItemsCount - HISTORY_PREFETCH_ITEMS
        }.filter { it }.collect { onLoadMoreEntries() }
    }
    LaunchedEffect(compactListState, uiState.timeEntries.size, uiState.hasMoreTimeEntries) {
        snapshotFlow {
            val info = compactListState.layoutInfo
            hasUserScrolledHistory && info.totalItemsCount > 0 &&
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
        wideHistoryListState,
        uiState.timeEntries.size,
        uiState.canLoadNewerHistory
    ) {
        snapshotFlow {
            hasUserScrolledHistory && uiState.canLoadNewerHistory &&
                (wideHistoryListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
                    ?: Int.MAX_VALUE) <= HISTORY_PREFETCH_ITEMS
        }.filter { it }.collect { onLoadNewerEntries() }
    }
    LaunchedEffect(
        compactListState,
        uiState.timeEntries.size,
        uiState.canLoadNewerHistory
    ) {
        snapshotFlow {
            hasUserScrolledHistory && uiState.canLoadNewerHistory &&
                (compactListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
                    ?: Int.MAX_VALUE) <= HISTORY_PREFETCH_ITEMS
        }.filter { it }.collect { onLoadNewerEntries() }
    }

    // Permission launcher for notification permission (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result is handled, no action needed
    }

    // Request notification permission on first start tracking
    DisposableEffect(uiState.isTracking) {
        if (uiState.isTracking && !NotificationPermissionHelper.hasNotificationPermission(context)) {
            if (context is android.app.Activity) {
                NotificationPermissionHelper.requestNotificationPermission(context)
            }
        }
        onDispose { }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        user?.let { account ->
                            var profileImageFailed by remember(account.profilePhotoUrl) { mutableStateOf(false) }
                            val profileDescription = stringResource(R.string.profile_picture)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (account.profilePhotoUrl.isNotBlank() && !profileImageFailed) {
                                    AsyncImage(
                                        model = account.profilePhotoUrl,
                                        contentDescription = profileDescription,
                                        onError = { profileImageFailed = true },
                                        modifier = Modifier.size(48.dp).clip(CircleShape)
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.size(48.dp).semantics { contentDescription = profileDescription },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                account.name.split(Regex("\\s+")).mapNotNull { it.firstOrNull() }
                                                    .take(2).joinToString("").uppercase(),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }
                                }
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(account.name, style = MaterialTheme.typography.titleMedium)
                                    Text(account.email, style = MaterialTheme.typography.bodySmall)
                                    val weekStart = remember(account.weekStart) {
                                        runCatching { DayOfWeek.valueOf(account.weekStart.uppercase()) }
                                            .getOrNull()?.getDisplayName(TextStyle.FULL, Locale.getDefault())
                                    }
                                    val profileDetails = listOfNotNull(
                                        account.timezone.takeIf { it.isNotBlank() }, weekStart,
                                    ).joinToString(" · ")
                                    if (profileDetails.isNotBlank()) {
                                        Text(
                                            profileDetails,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.settings_menu),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        OutlinedButton(
                            onClick = {
                                val appUri = Uri.parse("package:${context.packageName}")
                                val languageIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS, appUri)
                                } else {
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri)
                                }
                                runCatching { context.startActivity(languageIntent) }
                                    .onFailure {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri)
                                        )
                                    }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.choose_language))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        HorizontalDivider()
                        Text(
                            stringResource(R.string.long_timer_warning),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            stringResource(R.string.long_timer_warning_description, longTimerHours),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 4, 6, 8, 12).forEach { hours ->
                                FilterChip(
                                    selected = longTimerHours == hours,
                                    onClick = { onLongTimerHoursChange(hours) },
                                    label = { Text(stringResource(R.string.hours_short, hours)) },
                                )
                            }
                        }
                        HorizontalDivider()

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.server_information),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.server_endpoint),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Text(
                            text = serverEndpoint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Server endpoint", serverEndpoint))
                                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.client_id),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Text(
                            text = clientId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Client ID", clientId))
                                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.always_show_notifications),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.always_show_notifications_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = alwaysShowNotifications,
                                onCheckedChange = onAlwaysShowNotificationsChange
                            )
                        }

                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.optimistic_refresh), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.optimistic_refresh_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = optimisticRefresh, onCheckedChange = onOptimisticRefreshChange)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.theme),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.theme_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppThemeMode.entries.forEach { theme ->
                                val label = when (theme) {
                                    AppThemeMode.SYSTEM -> R.string.theme_system
                                    AppThemeMode.LIGHT -> R.string.theme_light
                                    AppThemeMode.NEO -> R.string.theme_neo
                                }
                                FilterChip(
                                    selected = appTheme == theme,
                                    onClick = { onAppThemeChange(theme) },
                                    label = { Text(stringResource(label)) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // About / Verification section
                        AboutSection(context = context)
                    }

                    // Keep logout visible without turning the drawer footer into a large block.
                    TextButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            onLogout()
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag(TrackingTestTags.LOGOUT_BUTTON)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.logout),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            ),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                stringResource(R.string.time_tracking),
                                fontWeight = FontWeight.SemiBold
                            )
                            user?.name?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (currentMembership != null) {
                                Box {
                                    Text(
                                        text = currentMembership.organization.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable(
                                            enabled = memberships.size > 1 && !uiState.isTracking && !uiState.isPaused
                                        ) { organizationMenuExpanded = true }
                                    )
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = organizationMenuExpanded,
                                        onDismissRequest = { organizationMenuExpanded = false }
                                    ) {
                                        memberships.forEach { membership ->
                                            DropdownMenuItem(
                                                text = { Text(membership.organization.name) },
                                                onClick = {
                                                    organizationMenuExpanded = false
                                                    onMembershipChange(membership)
                                                },
                                                trailingIcon = if (membership.id == currentMembership.id) {
                                                    {
                                                        Icon(
                                                            imageVector = Icons.Default.Done,
                                                            contentDescription = null
                                                        )
                                                    }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag(TrackingTestTags.SETTINGS_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.settings_menu)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.add_time_entry)
                            )
                        }
                        // Notification permission button (Android 13+) - only show if not granted
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !NotificationPermissionHelper.hasNotificationPermission(context)
                        ) {
                            IconButton(
                                onClick = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = stringResource(R.string.enable_notifications)
                                )
                            }
                        }
                        val syncTransition = rememberInfiniteTransition(label = "sync")
                        val syncRotation by syncTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = if (uiState.isSyncing) 360f else 0f,
                            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
                            label = "sync rotation"
                        )
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                modifier = Modifier.rotate(syncRotation)
                            )
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.padding(paddingValues)
            ) {
                val serverLastEntry = remember(uiState.timeEntries) {
                    uiState.timeEntries
                        .filter { it.end != null && !it.description.isNullOrBlank() }
                        .maxByOrNull { it.start }
                }
                val lastEntry = remember(serverLastEntry, uiState.cachedContinueEntry, currentMembership) {
                    serverLastEntry ?: uiState.cachedContinueEntry?.takeIf {
                        it.organizationId == currentMembership?.organizationId
                    }
                }
                val preparedHistory by produceState(
                    initialValue = PreparedHistory.Empty,
                    uiState.timeEntries, uiState.projects, uiState.tasks, uiState.clients,
                    historyFilter,
                ) {
                    value = withContext(Dispatchers.Default) {
                        val grouped = EntryTrustRules.filter(
                            entries = uiState.timeEntries,
                            filter = historyFilter,
                            projects = uiState.projects,
                            tasks = uiState.tasks,
                            clients = uiState.clients,
                            syncOperations = uiState.syncOperations,
                        ).groupBy { entry ->
                            IsoTimes.localDate(entry.start) ?: LocalDate.MIN
                        }
                        val days = grouped.mapNotNull { (date, entries) ->
                            val completed = entries.filter { (it.duration ?: 0) > 0 }
                            if (completed.isEmpty()) null else HistoryDay(
                                date = date,
                                entries = completed,
                                groups = completed.groupBy {
                                    "${it.projectId}_${it.taskId}_${it.description.orEmpty()}"
                                }.values.toList(),
                            )
                        }
                        PreparedHistory(
                            groupedEntries = grouped,
                            listItems = buildList {
                                days.forEach { day ->
                                    add(HistoryListItem.Header(day))
                                    day.groups.forEach { add(HistoryListItem.Group(it)) }
                                }
                            },
                        )
                    }
                }
                val groupedEntries = preparedHistory.groupedEntries
                val historyListItems = preparedHistory.listItems
                val historyProjectsById = remember(uiState.projects) { uiState.projects.associateBy { it.id } }
                val historyTasksById = remember(uiState.tasks) { uiState.tasks.associateBy { it.id } }
                val syncStatusByEntryId = remember(uiState.syncOperations) {
                    uiState.syncOperations.groupBy { it.entryId }.mapValues { (_, operations) ->
                        operations.last().status
                    }
                }
                val overlapCount = uiState.overlapCount
                val onHistoryEdit = remember<(TimeEntry) -> Unit> { { entry -> showEditDialog = entry } }
                val onHistoryDelete = remember<(TimeEntry) -> Unit> { { entry -> deletedEntry = entry; onDeleteEntry(entry.id) } }
                val onHistoryDateClick = remember<(LocalDate) -> Unit> { { date -> calendarInitialDate = date } }

                LaunchedEffect(uiState.historyJumpDate, groupedEntries) {
                    val target = uiState.historyJumpDate ?: return@LaunchedEffect
                    val historyIndex = historyHeaderIndex(target, groupedEntries)
                    if (historyIndex >= 0) {
                        val primaryItemCount = 2 +
                            (if (uiState.error != null) 1 else 0) +
                            (if (!uiState.isTracking && !uiState.isPaused && lastEntry != null) 1 else 0)
                        compactListState.scrollToItem(primaryItemCount + historyIndex)
                        wideHistoryListState.scrollToItem(1 + historyIndex)
                    }
                    onHistoryJumpConsumed()
                }

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wideLayout = maxWidth >= 840.dp
                    val primaryContent: LazyListScope.() -> Unit = {
                        item { Spacer(Modifier.height(8.dp)) }
                        item {
                            val elapsed by elapsedSeconds.collectAsState()
                            TrackingControls(
                                uiState = uiState,
                                elapsedSeconds = elapsed,
                                onDescriptionChange = onDescriptionChange,
                                onProjectChange = onProjectChange,
                                onTaskChange = onTaskChange,
                                onTagsChange = onTagsChange,
                                onBillableChange = onBillableChange,
                                onStart = onStartTracking,
                                onStop = onStopTracking,
                                onPause = onPauseTracking,
                                onResume = onResumeTracking,
                                onUpdate = onUpdateCurrentEntry
                            )
                        }
                        item(key = "long_timer_warning") {
                            val elapsed by elapsedSeconds.collectAsState()
                            if (uiState.isTracking && elapsed >= longTimerHours * 3600L &&
                                elapsed >= longTimerSnoozedUntil) {
                                LongTimerWarning(
                                    hours = longTimerHours,
                                    onStop = onStopTracking,
                                    onKeepRunning = {
                                        longTimerSnoozedUntil = elapsed + 3600L
                                        TimeTrackingNotificationService.snoozeLongTimerWarning(context)
                                    },
                                    onAdjust = { uiState.currentTimeEntry?.let { showEditDialog = it } },
                                )
                            }
                        }
                        if (overlapCount > 0) {
                            item { OverlapWarning(overlapCount, currentMembership?.organization?.preventOverlappingTimeEntries == true) }
                        }
                        if (!uiState.isTracking && !uiState.isPaused &&
                            templateState.quickStart.isNotEmpty()
                        ) {
                            item(key = "favorites_quick_start") {
                                FavoriteTemplatesRow(
                                    templates = templateState.quickStart,
                                    projects = templateState.projects,
                                    tasks = templateState.tasks,
                                    tags = templateState.tags,
                                    onStart = { start ->
                                        onDescriptionChange(start.description ?: "")
                                        onProjectChange(start.projectId)
                                        onTaskChange(start.taskId)
                                        onTagsChange(start.tagIds)
                                        onBillableChange(start.billable)
                                        onStartTracking()
                                    },
                                )
                            }
                        }
                        if (!uiState.isTracking && !uiState.isPaused && lastEntry != null) {
                            item(key = "continue_last") {
                                ContinueLastEntryButton(
                                    entry = lastEntry,
                                    projects = uiState.projects,
                                    onContinue = {
                                        onDescriptionChange(lastEntry.description ?: "")
                                        onProjectChange(lastEntry.projectId)
                                        onTaskChange(lastEntry.taskId)
                                        onTagsChange(lastEntry.tags.map { it.id })
                                        onBillableChange(lastEntry.billable)
                                        onStartTracking()
                                    }
                                )
                            }
                        }
                    }

                    if (wideLayout) {
                        Row(Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(0.9f)
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                content = primaryContent
                            )
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            LazyColumn(
                                state = wideHistoryListState,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .fillMaxSize()
                                    .testTag(TrackingTestTags.HISTORY_LIST)
                                    .nestedScroll(historyScrollConnection)
                                    .padding(horizontal = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item { Spacer(Modifier.height(8.dp)) }
                                item { HistoryFilters(historyFilter, uiState) { historyFilter = it } }
                                if (uiState.syncOperations.isNotEmpty()) {
                                    item { SyncCenter(uiState.syncOperations, onRetrySync, onRetrySyncEntry) }
                                }
                                trackingHistoryItems(
                                    uiState = uiState,
                                    historyItems = historyListItems,
                                    projectsById = historyProjectsById,
                                    tasksById = historyTasksById,
                                    syncStatusByEntryId = syncStatusByEntryId,
                                    onEdit = onHistoryEdit,
                                    onDelete = onHistoryDelete,
                                    onDateClick = onHistoryDateClick
                                )
                                item { Spacer(Modifier.height(16.dp)) }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = compactListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag(TrackingTestTags.HISTORY_LIST)
                                .nestedScroll(historyScrollConnection)
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            primaryContent()
                            item { HistoryFilters(historyFilter, uiState) { historyFilter = it } }
                            if (uiState.syncOperations.isNotEmpty()) {
                                item { SyncCenter(uiState.syncOperations, onRetrySync, onRetrySyncEntry) }
                            }
                            trackingHistoryItems(
                                uiState = uiState,
                                historyItems = historyListItems,
                                projectsById = historyProjectsById,
                                tasksById = historyTasksById,
                                syncStatusByEntryId = syncStatusByEntryId,
                                onEdit = onHistoryEdit,
                                onDelete = onHistoryDelete,
                                onDateClick = onHistoryDateClick
                            )
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }

    // Edit dialog
    showEditDialog?.let { entry ->
        TimeEntryFormSheet(
            entry = entry,
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
        )
    }

    // Add-entry dialog
    if (showAddDialog) {
        val suggestedStart = remember(uiState.timeEntries) {
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            uiState.timeEntries
                .mapNotNull { it.end }
                .mapNotNull { runCatching { ZonedDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull() }
                .maxOrNull()
                ?.withZoneSameInstant(ZoneId.systemDefault())
                ?.takeIf { it.toLocalDate() == now.toLocalDate() && it.isBefore(now) }
                ?: now.minusHours(1)
        }
        TimeEntryFormSheet(
            entry = null,
            suggestedStart = suggestedStart,
            projects = uiState.projects,
            tasks = uiState.tasks,
            tags = uiState.tags,
            existingEntries = uiState.timeEntries,
            preventOverlap = currentMembership?.organization?.preventOverlappingTimeEntries == true,
            onDismiss = { showAddDialog = false },
            onSave = { description, projectId, taskId, tagIds, billable, start, end ->
                onCreateEntry(description, projectId, taskId, tagIds, billable, start, end)
                showAddDialog = false
            },
            templates = templateState.templates,
            onSaveAsTemplate = onSaveTemplateFromForm,
        )
    }

    calendarInitialDate?.let { initialDate ->
        androidx.compose.runtime.key(initialDate) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { calendarInitialDate = null },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onJumpToDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        calendarInitialDate = null
                    }) { Text(stringResource(R.string.jump_to_date)) }
                },
                dismissButton = {
                    TextButton(onClick = { calendarInitialDate = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            ) { DatePicker(state = datePickerState) }
        }
    }

    uiState.historyJumpTarget?.let { targetDate ->
        Dialog(onDismissRequest = { }) {
            Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.finding_date_entries,
                            formatDate(targetDate, LocalContext.current)
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                    LinearProgressIndicator(
                        progress = { uiState.historyJumpProgress ?: 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = uiState.historyRateLimitWaitSeconds?.let { seconds ->
                            stringResource(R.string.rate_limit_wait, seconds)
                        } ?: stringResource(R.string.finding_date_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilters(filter: HistoryFilter, uiState: TrackingUiState, onChange: (HistoryFilter) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val activeCount = listOfNotNull(
        filter.query.takeIf { it.isNotBlank() }, filter.billable, filter.runningOnly.takeIf { it },
        filter.syncStatus, filter.startDate, filter.endDate, filter.clientId, filter.projectId, filter.taskId, filter.tagId,
        filter.missingProjectOnly.takeIf { it }, filter.missingDescriptionOnly.takeIf { it },
        filter.needsCategorization.takeIf { it },
    ).size
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = expanded || activeCount > 0,
                onClick = { expanded = !expanded },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, Modifier.size(18.dp)) },
                label = {
                    Text(
                        if (activeCount == 0) stringResource(R.string.search_and_filter)
                        else stringResource(R.string.active_filters_count, activeCount),
                    )
                },
                trailingIcon = if (expanded) {{ Icon(Icons.Default.Close, stringResource(R.string.close), Modifier.size(18.dp)) }} else null,
            )
            if (!expanded && filter.query.isNotBlank()) {
                Text(filter.query, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = { onChange(filter.copy(query = it)) },
                    label = { Text(stringResource(R.string.search_history)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter.billable == true,
                onClick = { onChange(filter.copy(billable = if (filter.billable == true) null else true)) },
                label = { Text(stringResource(R.string.billable)) },
            )
            FilterChip(
                selected = filter.billable == false,
                onClick = { onChange(filter.copy(billable = if (filter.billable == false) null else false)) },
                label = { Text(stringResource(R.string.non_billable)) },
            )
            FilterChip(
                selected = filter.runningOnly,
                onClick = { onChange(filter.copy(runningOnly = !filter.runningOnly)) },
                label = { Text(stringResource(R.string.running_entries)) },
            )
            FilterChip(
                selected = filter.syncStatus == TimeEntryRepository.EntrySyncStatus.FAILED,
                onClick = { onChange(filter.copy(syncStatus = if (filter.syncStatus == TimeEntryRepository.EntrySyncStatus.FAILED) null else TimeEntryRepository.EntrySyncStatus.FAILED)) },
                label = { Text(stringResource(R.string.sync_failed)) },
            )
            val today = LocalDate.now()
            FilterChip(
                selected = filter.startDate == today && filter.endDate == today,
                onClick = { onChange(filter.copy(startDate = today, endDate = today)) },
                label = { Text(stringResource(R.string.today)) },
            )
            FilterChip(
                selected = filter.startDate == today.minusDays(6) && filter.endDate == today,
                onClick = { onChange(filter.copy(startDate = today.minusDays(6), endDate = today)) },
                label = { Text(stringResource(R.string.stats_last_7_days)) },
            )
            FilterChip(
                selected = filter.startDate != null && filter.endDate != null &&
                    !(filter.startDate == today && filter.endDate == today) &&
                    !(filter.startDate == today.minusDays(6) && filter.endDate == today),
                onClick = { showDateRangePicker = true },
                label = { Text(stringResource(R.string.stats_custom)) },
            )
            FilterChip(
                selected = filter.needsCategorization,
                onClick = { onChange(filter.copy(needsCategorization = !filter.needsCategorization)) },
                label = { Text(stringResource(R.string.needs_categorization)) },
            )
            FilterChip(
                selected = filter.missingProjectOnly,
                onClick = { onChange(filter.copy(missingProjectOnly = !filter.missingProjectOnly)) },
                label = { Text(stringResource(R.string.without_project)) },
            )
            FilterChip(
                selected = filter.missingDescriptionOnly,
                onClick = { onChange(filter.copy(missingDescriptionOnly = !filter.missingDescriptionOnly)) },
                label = { Text(stringResource(R.string.without_description)) },
            )
            if (filter != HistoryFilter()) {
                TextButton(onClick = { onChange(HistoryFilter()) }) { Text(stringResource(R.string.clear_filters)) }
            }
                }
                FilterDropdown(
            label = stringResource(R.string.client),
            selectedId = filter.clientId,
            options = uiState.clients.map { it.id to it.name },
            onSelect = { onChange(filter.copy(clientId = it, projectId = null, taskId = null)) },
        )
                FilterDropdown(
            label = stringResource(R.string.project),
            selectedId = filter.projectId,
            options = uiState.projects.filter { filter.clientId == null || it.clientId == filter.clientId }.map { it.id to it.name },
            onSelect = { onChange(filter.copy(projectId = it, taskId = null)) },
        )
                if (filter.projectId != null) {
            FilterDropdown(
                label = stringResource(R.string.task),
                selectedId = filter.taskId,
                options = uiState.tasks.filter { it.projectId == filter.projectId }.map { it.id to it.name },
                onSelect = { onChange(filter.copy(taskId = it)) },
            )
                }
                FilterDropdown(
            label = stringResource(R.string.tags),
            selectedId = filter.tagId,
            options = uiState.tags.map { it.id to it.name },
            onSelect = { onChange(filter.copy(tagId = it)) },
                )
            }
        }
    }
    if (showDateRangePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = filter.startDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = filter.endDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                            ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        val end = pickerState.selectedEndDateMillis
                            ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        if (start != null && end != null) onChange(filter.copy(startDate = start, endDate = end))
                        showDateRangePicker = false
                    },
                ) { Text(stringResource(R.string.apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) { DateRangePicker(state = pickerState) }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    selectedId: String?,
    options: List<Pair<String, String>>,
    onSelect: (String?) -> Unit,
) {
    if (options.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedId != null,
            onClick = { expanded = true },
            label = { Text(options.firstOrNull { it.first == selectedId }?.second ?: label, maxLines = 1) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all_items, label)) },
                onClick = { onSelect(null); expanded = false },
            )
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}

@Composable
private fun SyncCenter(
    operations: List<TimeEntryRepository.SyncOperation>,
    onRetry: () -> Unit,
    onRetryEntry: (String) -> Unit,
) {
    val failed = operations.count { it.status == TimeEntryRepository.EntrySyncStatus.FAILED }
    val retrying = operations.count { it.status == TimeEntryRepository.EntrySyncStatus.RETRYING }
    val summaryStatus = when {
        failed > 0 -> TimeEntryRepository.EntrySyncStatus.FAILED
        retrying > 0 -> TimeEntryRepository.EntrySyncStatus.RETRYING
        else -> TimeEntryRepository.EntrySyncStatus.PENDING
    }
    SectionCard(title = stringResource(R.string.sync_center)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            ) {
                SyncChip(status = summaryStatus, showLabel = false)
                Text(
                    when {
                        failed > 0 -> stringResource(R.string.sync_failed_count, failed)
                        retrying > 0 -> stringResource(R.string.sync_retrying_count, retrying)
                        else -> stringResource(R.string.sync_pending_count, operations.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (failed > 0 || retrying > 0) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
        operations.filter { it.status == TimeEntryRepository.EntrySyncStatus.FAILED }.forEach { operation ->
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            ) {
                Text(
                    stringResource(R.string.sync_entry_failed),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { onRetryEntry(operation.entryId) }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LongTimerWarning(hours: Int, onStop: () -> Unit, onKeepRunning: () -> Unit, onAdjust: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(stringResource(R.string.timer_running_long, hours), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onStop) { Text(stringResource(R.string.stop_now)) }
                TextButton(onClick = onKeepRunning) { Text(stringResource(R.string.keep_running)) }
                TextButton(onClick = onAdjust) { Text(stringResource(R.string.adjust_end_time)) }
            }
        }
    }
}

@Composable
private fun OverlapWarning(count: Int, prohibitedByOrganization: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(
            stringResource(
                if (prohibitedByOrganization) R.string.overlap_policy_warning_count else R.string.overlap_warning_count,
                count,
            ),
            Modifier.fillMaxWidth().padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val HISTORY_PREFETCH_ITEMS = 75
private const val HISTORY_INITIAL_PREFETCH_MAX_ENTRIES = 250

private fun historyHeaderIndex(
    requestedDate: LocalDate,
    groupedEntries: Map<LocalDate, List<TimeEntry>>
): Int {
    val groups = groupedEntries.mapValues { (_, entries) ->
        entries.filter { (it.duration ?: 0) > 0 }
    }.filterValues { it.isNotEmpty() }
    val targetDate = groups.keys.minByOrNull { kotlin.math.abs(it.toEpochDay() - requestedDate.toEpochDay()) }
        ?: return -1
    var index = 0
    for ((date, entries) in groups) {
        if (date == targetDate) return index
        index += 1 + entries.groupBy {
            "${it.projectId}_${it.taskId}_${it.description ?: ""}"
        }.size
    }
    return -1
}

internal fun LazyListScope.trackingHistoryItems(
    uiState: TrackingUiState,
    historyItems: List<HistoryListItem>,
    projectsById: Map<String, Project>,
    tasksById: Map<String, Task>,
    syncStatusByEntryId: Map<String, TimeEntryRepository.EntrySyncStatus>,
    onEdit: (TimeEntry) -> Unit,
    onDelete: (TimeEntry) -> Unit,
    onDateClick: (LocalDate) -> Unit
) {
    if (!uiState.hasLoadedTimeEntries && uiState.timeEntries.isEmpty()) {
        item(key = "history_loading_header") { HistoryLoadingHeader() }
        repeat(4) { index ->
            item(key = "history_loading_$index") { HistoryLoadingEntry(index) }
        }
        return
    }

    items(
        items = historyItems,
        key = {
            when (it) {
                is HistoryListItem.Header -> "header_${it.day.date}"
                is HistoryListItem.Group -> "group_${it.entries.first().id}"
            }
        },
        contentType = {
            when (it) {
                is HistoryListItem.Header -> "history_header"
                is HistoryListItem.Group -> "history_group"
            }
        },
    ) { historyItem ->
        when (historyItem) {
            is HistoryListItem.Header -> DateHeader(
                date = historyItem.day.date,
                entries = historyItem.day.entries,
                projectsById = projectsById,
                onClick = { onDateClick(historyItem.day.date) },
            )
            is HistoryListItem.Group -> {
                val entriesForProject = historyItem.entries
                    CollapsibleTimeEntryGroup(
                        entries = entriesForProject,
                        projectsById = projectsById,
                        tasksById = tasksById,
                        syncStatusByEntryId = syncStatusByEntryId,
                        onEdit = onEdit,
                        onDelete = onDelete
                    )
            }
        }
    }

    if (uiState.hasMoreTimeEntries || uiState.isLoadingMoreTimeEntries) {
        item(key = "history_pagination") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                uiState.totalTimeEntries?.let { total ->
                    Text(
                        text = "  ${uiState.timeEntries.size} / $total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (uiState.timeEntries.isEmpty() && uiState.hasLoadedTimeEntries && !uiState.isLoading) {
        item {
            Text(
                text = stringResource(R.string.no_time_entries),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp)
            )
        }
    }
}

/** Compatibility entry point for previews and screenshot tests that provide grouped history. */
internal fun LazyListScope.trackingHistoryItems(
    uiState: TrackingUiState,
    groupedEntries: Map<LocalDate, List<TimeEntry>>,
    onEdit: (TimeEntry) -> Unit,
    onDelete: (TimeEntry) -> Unit,
    onDateClick: (LocalDate) -> Unit,
) {
    val historyItems = buildList {
        groupedEntries.forEach { (date, entries) ->
            val completed = entries.filter { (it.duration ?: 0) > 0 }
            if (completed.isNotEmpty()) {
                val day = HistoryDay(
                    date = date,
                    entries = completed,
                    groups = completed.groupBy {
                        "${it.projectId}_${it.taskId}_${it.description.orEmpty()}"
                    }.values.toList(),
                )
                add(HistoryListItem.Header(day))
                day.groups.forEach { add(HistoryListItem.Group(it)) }
            }
        }
    }
    trackingHistoryItems(
        uiState = uiState,
        historyItems = historyItems,
        projectsById = uiState.projects.associateBy { it.id },
        tasksById = uiState.tasks.associateBy { it.id },
        syncStatusByEntryId = uiState.syncOperations.groupBy { it.entryId }.mapValues { (_, operations) ->
            operations.last().status
        },
        onEdit = onEdit,
        onDelete = onDelete,
        onDateClick = onDateClick,
    )
}

@Immutable
 internal data class HistoryDay(
     val date: LocalDate,
     val entries: List<TimeEntry>,
     val groups: List<List<TimeEntry>>,
 )

@Immutable
 internal sealed interface HistoryListItem {
    @Immutable
     data class Header(val day: HistoryDay) : HistoryListItem
    @Immutable
     data class Group(val entries: List<TimeEntry>) : HistoryListItem
 }

@Immutable
 private data class PreparedHistory(
     val groupedEntries: Map<LocalDate, List<TimeEntry>>,
     val listItems: List<HistoryListItem>,
 ) {
     companion object {
         val Empty = PreparedHistory(emptyMap(), emptyList())
     }
 }

@Composable
private fun rememberGhostAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "history loading")
    val alpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ghost alpha"
    )
    return alpha
}

@Composable
private fun GhostBlock(
    modifier: Modifier,
    alpha: Float
) {
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                RoundedCornerShape(6.dp)
            )
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
        verticalAlignment = Alignment.CenterVertically
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
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GhostBlock(Modifier.size(10.dp), alpha)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                GhostBlock(
                    Modifier
                        .fillMaxWidth(if (index % 2 == 0) 0.62f else 0.45f)
                        .height(13.dp),
                    alpha
                )
                GhostBlock(Modifier.width(if (index % 2 == 0) 96.dp else 128.dp).height(10.dp), alpha)
            }
            GhostBlock(Modifier.width(58.dp).height(14.dp), alpha)
        }
    }
}

/**
 * Tracking controls card with timer and input fields
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TrackingControls(
    uiState: TrackingUiState,
    elapsedSeconds: Long = 0L,
    onDescriptionChange: (String) -> Unit,
    onProjectChange: (String?) -> Unit,
    onTaskChange: (String?) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onBillableChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onUpdate: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Timer display
            if (uiState.isTracking) {
                Text(
                    text = formatElapsedTime(elapsedSeconds),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 48.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag(TrackingTestTags.ELAPSED_TIMER)
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else if (uiState.isPaused) {
                Text(
                    text = stringResource(R.string.paused),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 36.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Description field with recent entry suggestions
            DescriptionFieldWithSuggestions(
                description = uiState.editingDescription,
                onDescriptionChange = onDescriptionChange,
                timeEntries = uiState.timeEntries,
                projects = uiState.projects,
                tags = uiState.tags,
                enabled = !uiState.isMutating,
                onEntryCopied = { entry ->
                    onDescriptionChange(entry.description ?: "")
                    onProjectChange(entry.projectId)
                    onTaskChange(entry.taskId)
                    onTagsChange(entry.tags.map { it.id })
                    onBillableChange(entry.billable)
                }
            )

            // Combined Project/Task selector
            ProjectTaskDropdown(
                selectedProjectId = uiState.editingProjectId,
                selectedTaskId = uiState.editingTaskId,
                projects = uiState.projects,
                tasks = uiState.tasks,
                onSelectionChanged = { projectId, taskId ->
                    onProjectChange(projectId)
                    onTaskChange(taskId)
                },
                enabled = !uiState.isMutating
            )

            // Tags selector
            if (uiState.tags.isNotEmpty()) {
                TagsSelector(
                    selectedTagIds = uiState.editingTags,
                    availableTags = uiState.tags,
                    onTagsChanged = onTagsChange,
                    enabled = !uiState.isMutating
                )
            }

            // Billable checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.editingBillable,
                    onCheckedChange = onBillableChange,
                    enabled = !uiState.isMutating
                )
                Text(
                    text = stringResource(R.string.billable),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isTracking) {
                    Button(
                        onClick = onUpdate,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isMutating,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.update), fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPause()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isMutating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.pause), fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStop()
                        },
                        modifier = Modifier.weight(1f).testTag(TrackingTestTags.STOP_BUTTON),
                        enabled = !uiState.isMutating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.stop), fontWeight = FontWeight.SemiBold)
                    }
                } else if (uiState.isPaused) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onResume()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isMutating,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.resume), fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStop()
                        },
                        modifier = Modifier.weight(1f).testTag(TrackingTestTags.STOP_BUTTON),
                        enabled = !uiState.isMutating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.stop), fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStart()
                        },
                        modifier = Modifier.fillMaxWidth().testTag(TrackingTestTags.START_BUTTON),
                        enabled = !uiState.isMutating,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.start), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * "Continue last entry" button that starts tracking with the same params as the last entry.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContinueLastEntryButton(
    entry: TimeEntry,
    projects: List<Project>,
    onContinue: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val project = projects.find { it.id == entry.projectId }

    OutlinedButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onContinue()
        },
        modifier = Modifier.fillMaxWidth().testTag(TrackingTestTags.CONTINUE_BUTTON),
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.continue_last_entry),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.description ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (entry.projectId != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (project != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(project.color)))
                        )
                    } else {
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        text = project?.name ?: " ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (entry.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    entry.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 0.dp
                        ) {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Description field with dropdown suggestions from recent time entries.
 * Shows the last 5 unique entries (deduplicated by description+project+task+tags).
 * Filters out entries with no description.
 * Tapping a suggestion copies all fields (description, project, task, tags, billable).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DescriptionFieldWithSuggestions(
    description: String,
    onDescriptionChange: (String) -> Unit,
    timeEntries: List<TimeEntry>,
    projects: List<Project>,
    tags: List<Tag>,
    enabled: Boolean,
    onEntryCopied: (TimeEntry) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Compute last 5 unique recent entries, filtering out empty descriptions
    val recentEntries = remember(timeEntries) {
        timeEntries
            .filter { it.end != null && !it.description.isNullOrBlank() }
            .sortedByDescending { it.start }
            .distinctBy { entry ->
                "${entry.description}|${entry.projectId}|${entry.taskId}|${entry.tags.map { it.id }.sorted()}"
            }
            .take(5)
    }

    ExposedDropdownMenuBox(
        expanded = expanded && recentEntries.isNotEmpty(),
        onExpandedChange = {
            if (enabled) expanded = it
        }
    ) {
        OutlinedTextField(
            value = description,
            onValueChange = {
                onDescriptionChange(it)
                expanded = false
            },
            label = { Text(stringResource(R.string.description)) },
            placeholder = { Text(stringResource(R.string.what_are_you_working_on)) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = enabled)
                .onFocusChanged {
                    if (it.isFocused && enabled) expanded = true
                },
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded && recentEntries.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            Text(
                stringResource(R.string.recent_entries),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                                overflow = TextOverflow.Ellipsis
                            )
                            // Project row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (project != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Color(
                                                    android.graphics.Color.parseColor(
                                                        project.color
                                                    )
                                                )
                                            )
                                    )
                                    Text(
                                        text = project.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.no_project_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            // Tag chips
                            if (entryTagNames.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    entryTagNames.forEach { tagName ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            tonalElevation = 0.dp
                                        ) {
                                            Text(
                                                text = tagName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(
                                                    horizontal = 6.dp,
                                                    vertical = 2.dp
                                                )
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
                    }
                )
            }
        }
    }
}

/**
 * Combined Project/Task dropdown selector
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectTaskDropdown(
    selectedProjectId: String?,
    selectedTaskId: String?,
    projects: List<Project>,
    tasks: List<Task>,
    onSelectionChanged: (projectId: String?, taskId: String?) -> Unit,
    enabled: Boolean
) {
    // Determine current selection
    val selection = remember(selectedProjectId, selectedTaskId, projects, tasks) {
        when {
            selectedProjectId == null -> ProjectTaskSelection.NoProject
            selectedTaskId == null -> {
                projects.find { it.id == selectedProjectId }?.let {
                    ProjectTaskSelection.ProjectOnly(it)
                } ?: ProjectTaskSelection.NoProject
            }

            else -> {
                val project = projects.find { it.id == selectedProjectId }
                val task = tasks.find { it.id == selectedTaskId }
                if (project != null && task != null) {
                    ProjectTaskSelection.ProjectWithTask(project, task)
                } else if (project != null) {
                    ProjectTaskSelection.ProjectOnly(project)
                } else {
                    ProjectTaskSelection.NoProject
                }
            }
        }
    }

    // Build display text
    val displayText = when (selection) {
        is ProjectTaskSelection.NoProject -> stringResource(R.string.no_project)
        is ProjectTaskSelection.ProjectOnly -> selection.project.name
        is ProjectTaskSelection.ProjectWithTask -> "${selection.project.name} - ${selection.task.name}"
    }

    SharedProjectTaskDropdown(
        projects = projects,
        tasks = tasks,
        displayText = displayText,
        onSelectionChanged = onSelectionChanged,
        enabled = enabled,
        showProjectColors = true,
        rounded = true
    )
}

/**
 * Tags selector with chips
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagsSelector(
    selectedTagIds: List<String>,
    availableTags: List<Tag>,
    onTagsChanged: (List<String>) -> Unit,
    enabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.tags),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(availableTags, key = { it.id }) { tag ->
                FilterChip(
                    selected = tag.id in selectedTagIds,
                    onClick = {
                        if (enabled) {
                            val newTags = if (tag.id in selectedTagIds) {
                                selectedTagIds - tag.id
                            } else {
                                selectedTagIds + tag.id
                            }
                            onTagsChanged(newTags)
                        }
                    },
                    label = { Text(tag.name) },
                    enabled = enabled,
                    border = null,
                    shape = RoundedCornerShape(6.dp)
                )
            }
        }
    }
}

/**
 * Date header for time entries
 */
@Composable
private fun DateHeader(
    date: LocalDate,
    entries: List<TimeEntry>,
    projectsById: Map<String, Project>,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val headerStats = remember(entries, projectsById) {
        val projectIds = entries.mapNotNull { it.projectId }.toSet()
        val customerCount = projectIds.mapNotNull { projectsById[it]?.clientId }.toSet().size
        val totalDuration = entries.sumOf { it.duration ?: 0 }
        val summary = buildList {
            add(formatCompactDuration(totalDuration))
            if (customerCount > 0) {
                add(context.resources.getQuantityString(R.plurals.customer_count, customerCount, customerCount))
            }
            if (projectIds.isNotEmpty()) {
                add(context.resources.getQuantityString(R.plurals.project_count, projectIds.size, projectIds.size))
            }
        }.joinToString(" · ")
        summary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = formatDate(date, context),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1
        )
        Text(
            text = headerStats,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Collapsible group of time entries with same project/task
 */
@Composable
private fun CollapsibleTimeEntryGroup(
    entries: List<TimeEntry>,
    projectsById: Map<String, Project>,
    tasksById: Map<String, Task>,
    syncStatusByEntryId: Map<String, TimeEntryRepository.EntrySyncStatus>,
    onEdit: (TimeEntry) -> Unit,
    onDelete: (TimeEntry) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val worstSyncStatus = remember(entries, syncStatusByEntryId) {
        entries.mapNotNull { entry -> syncStatusByEntryId[entry.id] }
            .maxByOrNull { it.ordinal }
    }
    val totalDuration = remember(entries) {
        entries.sumOf { it.duration ?: 0 }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (entries.size == 1) {
            // Single entry, show normally
            CompactTimeEntryRow(
                entry = entries.first(),
                project = projectsById[entries.first().projectId],
                task = tasksById[entries.first().taskId],
                syncStatus = syncStatusByEntryId[entries.first().id],
                onEdit = { onEdit(entries.first()) },
                onDelete = { onDelete(entries.first()) },
                count = null
            )
        } else {
            // Multiple entries, show collapsed or expanded
            if (!isExpanded) {
                // Show grouped entry
                CompactTimeEntryRow(
                    entry = entries.first(),
                    project = projectsById[entries.first().projectId],
                    task = tasksById[entries.first().taskId],
                    syncStatus = worstSyncStatus,
                    onEdit = { isExpanded = true },
                    onDelete = { /* Don't allow deleting grouped entries */ },
                    count = entries.size,
                    totalDuration = totalDuration
                )
            } else {
                // Show all entries
                entries.forEach { entry ->
                    CompactTimeEntryRow(
                        entry = entry,
                        project = projectsById[entry.projectId],
                        task = tasksById[entry.taskId],
                        syncStatus = syncStatusByEntryId[entry.id],
                        onEdit = { onEdit(entry) },
                        onDelete = { onDelete(entry) },
                        count = null,
                        isIndented = true
                    )
                }
                // Collapse button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.collapse_entries, entries.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { isExpanded = false }
                    )
                }
            }
        }
    }
}

/**
 * Compact time entry row showing past entry details (collapsed view)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactTimeEntryRow(
    entry: TimeEntry,
    project: Project?,
    task: Task?,
    syncStatus: TimeEntryRepository.EntrySyncStatus?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    count: Int? = null,
    totalDuration: Int? = null,
    isIndented: Boolean = false
) {
    val timeRange = remember(entry.start, entry.end) { formatTimeRange(entry.start, entry.end) }
    val durationText = remember(totalDuration, entry.duration) {
        formatDuration(totalDuration ?: entry.duration ?: 0)
    }
    val projectColor = remember(project?.color) {
        project?.let { runCatching { Color(android.graphics.Color.parseColor(it.color)) }.getOrNull() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TrackingTestTags.ENTRY_ROW)
            .then(if (isIndented) Modifier.padding(start = 16.dp) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clickable(enabled = count != null && count > 1) { onEdit() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Count badge (if grouped)
        if (count != null && count > 1) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        // Left side: Description and project info
        Column(modifier = Modifier.weight(1f)) {
            // Description
            Text(
                text = entry.description?.takeIf { it.isNotEmpty() }
                    ?: stringResource(R.string.no_description),
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.description.isNullOrEmpty())
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(2.dp))

            // Project â€¢ Task and time range on same line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Project color dot + name
                if (project != null) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(projectColor ?: MaterialTheme.colorScheme.outline)
                    )
                    val projectTaskText = remember(project?.name, task?.name) {
                        buildString {
                            append(project.name)
                            task?.let { append(" · ${it.name}") }
                        }
                    }
                    Text(
                        text = projectTaskText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                // Time range
                Text(
                    text = timeRange,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Right side: Duration and action icons
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kit chip renders nothing for SYNCED (and null); only PENDING /
            // RETRYING / FAILED surface, so a healthy row stays clutter-free.
            syncStatus?.let { SyncChip(status = it) }
            // Duration (use totalDuration if grouped, otherwise entry duration)
            Text(
                text = durationText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Only show action buttons if not grouped or if expanded
            if (count == null || count == 1) {
                // Edit button
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(48.dp).testTag(TrackingTestTags.ENTRY_EDIT_BUTTON)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp).testTag(TrackingTestTags.ENTRY_DELETE_BUTTON)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Create/edit time entry bottom sheet. [entry] null = create mode.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TimeEntryFormSheet(
    entry: TimeEntry?, // null = create mode
    suggestedStart: ZonedDateTime?, // create mode: pre-filled start (end of last entry / now-1h)
    projects: List<Project>,
    tasks: List<Task>,
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onSave: (String?, String?, String?, List<String>, Boolean, String, String) -> Unit,
    existingEntries: List<TimeEntry> = emptyList(),
    preventOverlap: Boolean = false,
    templates: List<EntryTemplate> = emptyList(),
    onSaveAsTemplate: ((TemplateDraft) -> Unit)? = null,
) {
    var description by remember { mutableStateOf(entry?.description ?: "") }
    var projectId by remember { mutableStateOf(entry?.projectId) }
    var taskId by remember { mutableStateOf(entry?.taskId) }
    var selectedTags by remember { mutableStateOf(entry?.tags?.map { it.id } ?: emptyList<String>()) }
    var billable by remember { mutableStateOf(entry?.billable ?: false) }
    var templateMenuExpanded by remember { mutableStateOf(false) }
    val originalStart = remember(entry?.id) {
        entry?.let { ZonedDateTime.parse(it.start, DateTimeFormatter.ISO_DATE_TIME) }
            ?: (suggestedStart ?: ZonedDateTime.now(ZoneId.systemDefault()).minusHours(1))
                .withSecond(0).withNano(0)
    }
    val originalEnd = remember(entry?.id) {
        when {
            entry?.end != null -> ZonedDateTime.parse(entry.end, DateTimeFormatter.ISO_DATE_TIME)
            entry != null -> originalStart.plusSeconds((entry.duration ?: 0).toLong())
            else -> ZonedDateTime.now(originalStart.zone).withSecond(0).withNano(0)
                .let { if (it.isAfter(originalStart)) it else originalStart.plusMinutes(1) }
        }
    }
    var startTime by remember(entry?.id) { mutableStateOf(originalStart) }
    var endTime by remember(entry?.id) { mutableStateOf(originalEnd) }
    var durationMinutes by remember(entry?.id) {
        mutableStateOf(java.time.Duration.between(originalStart, originalEnd).toMinutes().coerceAtLeast(1).toString())
    }
    var editingTime by remember { mutableStateOf<TimeField?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val durationIsValid = durationMinutes.toLongOrNull()?.let { it > 0 } == true
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val overlaps = remember(startTime, endTime, existingEntries, entry) {
        val org = entry?.organizationId ?: existingEntries.firstOrNull()?.organizationId
        if (org == null || existingEntries.isEmpty()) {
            false
        } else {
            val candidate = TimeEntry(
                id = entry?.id ?: "",
                userId = entry?.userId ?: "",
                start = startTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                end = endTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                organizationId = org,
            )
            existingEntries.any { it.id != candidate.id && EntryTrustRules.overlaps(candidate, it) }
        }
    }
    val validation = remember(startTime, endTime, overlaps, preventOverlap) {
        EntryTimeValidator.evaluate(startTime, endTime, overlaps, preventOverlap)
    }
    val durationHours = remember(startTime, endTime) {
        java.time.Duration.between(startTime, endTime).toHours().coerceAtLeast(0)
    }

    fun setDuration(minutes: Long) {
        val safeMinutes = minutes.coerceAtLeast(1)
        durationMinutes = safeMinutes.toString()
        endTime = startTime.plusMinutes(safeMinutes)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                Text(
                    text = stringResource(if (entry == null) R.string.add_time_entry else R.string.edit_time_entry),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                // Save-as-template / start-from-template affordance (gap analysis #1, #9).
                if (templates.isNotEmpty() || onSaveAsTemplate != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (templates.isNotEmpty()) {
                            Box {
                                OutlinedButton(
                                    onClick = { templateMenuExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(stringResource(R.string.templates_use_template))
                                }
                                DropdownMenu(
                                    expanded = templateMenuExpanded,
                                    onDismissRequest = { templateMenuExpanded = false },
                                ) {
                                    templates.forEach { template ->
                                        val label = templateDisplayLabel(template, projects)
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                val resolution = TemplateResolver.resolve(
                                                    template, projects, tasks, tags,
                                                )
                                                description = template.description ?: ""
                                                projectId = resolution.projectId
                                                taskId = resolution.taskId
                                                selectedTags = resolution.tagIds
                                                billable = resolution.billable
                                                templateMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (onSaveAsTemplate != null) {
                            OutlinedButton(
                                onClick = {
                                    onSaveAsTemplate(
                                        TemplateDraft(
                                            name = null,
                                            projectId = projectId,
                                            taskId = taskId,
                                            description = description.trim().takeIf { it.isNotEmpty() },
                                            tagIds = selectedTags,
                                            billable = billable,
                                            isFavorite = false,
                                        )
                                    )
                                },
                                enabled = projectId != null || description.isNotBlank() || selectedTags.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(stringResource(R.string.templates_save_as_template))
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.time_and_duration),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (entry == null) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.entry_date), style = MaterialTheme.typography.labelSmall)
                            Text(
                                startTime.format(entryDateFormatter),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TimeFieldButton(
                        label = stringResource(R.string.start_time),
                        value = startTime,
                        onClick = { editingTime = TimeField.Start },
                        modifier = Modifier.weight(1f)
                    )
                    TimeFieldButton(
                        label = stringResource(R.string.end_time),
                        value = endTime,
                        onClick = { editingTime = TimeField.End },
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.total_time),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatEditableDuration(durationMinutes.toLongOrNull() ?: 0),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { setDuration((durationMinutes.toLongOrNull() ?: 1) - 15) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = stringResource(R.string.decrease_15_minutes)
                                )
                            }
                            OutlinedTextField(
                                value = durationMinutes,
                                onValueChange = { value ->
                                    if (value.all(Char::isDigit)) {
                                        durationMinutes = value
                                        value.toLongOrNull()?.takeIf { it > 0 }?.let { endTime = startTime.plusMinutes(it) }
                                    }
                                },
                                label = { Text(stringResource(R.string.minutes)) },
                                suffix = { Text(stringResource(R.string.minutes_short)) },
                                isError = !durationIsValid,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            FilledTonalIconButton(
                                onClick = { setDuration((durationMinutes.toLongOrNull() ?: 0) + 15) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.increase_15_minutes)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth().testTag(TrackingTestTags.SHEET_DESCRIPTION_FIELD),
                    shape = RoundedCornerShape(8.dp)
                )

                ProjectTaskDropdown(
                    selectedProjectId = projectId,
                    selectedTaskId = taskId,
                    projects = projects,
                    tasks = tasks,
                    onSelectionChanged = { newProjectId, newTaskId ->
                        projectId = newProjectId
                        taskId = newTaskId
                    },
                    enabled = true
                )

                if (tags.isNotEmpty()) {
                    TagsSelector(
                        selectedTagIds = selectedTags,
                        availableTags = tags,
                        onTagsChanged = { selectedTags = it },
                        enabled = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = billable,
                        onCheckedChange = { billable = it }
                    )
                    Text(
                        text = stringResource(R.string.billable),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                EntryValidationBanner(result = validation, durationHours = durationHours)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                description.ifEmpty { null },
                                projectId,
                                taskId,
                                selectedTags,
                                billable,
                                startTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                endTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            )
                        },
                        enabled = durationIsValid && validation.canSave,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag(TrackingTestTags.SHEET_SAVE_BUTTON)
                    ) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                    }
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
                    val minutes = durationMinutes.toLongOrNull() ?: 1
                    startTime = startTime.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                    endTime = startTime.plusMinutes(minutes)
                } else {
                    val sameDayEnd = endTime.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                    // Do not silently roll an earlier clock-time into a ~24h entry: only a plausible
                    // overnight span becomes cross-midnight, otherwise keep it same-day so the
                    // validation banner surfaces the end-before-start error for the user to fix.
                    endTime = EntryTimeValidator.resolveEnd(startTime, sameDayEnd) ?: sameDayEnd
                    durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes().toString()
                }
                editingTime = null
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startTime.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        startTime = startTime.with(newDate)
                        endTime = startTime.plusMinutes(durationMinutes.toLongOrNull() ?: 1)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) { DatePicker(state = datePickerState) }
    }
}

private enum class TimeField { Start, End }

@Composable
private fun TimeFieldButton(
    label: String,
    value: ZonedDateTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
    ) {
        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value.format(hourMinuteFormatter), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryTimePickerDialog(
    title: String,
    initial: ZonedDateTime,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { Button(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/**
 * Error card
 */
@Composable
private fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.error),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFEF4444),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFEF4444).copy(alpha = 0.9f)
            )
        }
    }
}

/**
 * About section with version info, verification details, and Obtainium button
 */
@Composable
private fun AboutSection(context: Context) {
    Text(
        text = stringResource(R.string.about),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    // Version
    Text(
        text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    // Verification Info card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.verification_info),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Package ID
            Text(
                text = stringResource(R.string.package_id_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = context.packageName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.clickable {
                    copyToClipboard(context, context.packageName)
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Signing Certificate SHA-256
            Text(
                text = stringResource(R.string.signing_certificate_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val signingHash = remember { getSigningCertificateHash(context) }
            Text(
                text = signingHash,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.clickable {
                    copyToClipboard(context, signingHash)
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    val lifecycleOwner = LocalLifecycleOwner.current
    var obtainiumInstalled by remember { mutableStateOf(isObtainiumInstalled(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                obtainiumInstalled = isObtainiumInstalled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Add to Obtainium, or help the user install it first.
    OutlinedButton(
        onClick = {
            val obtainiumUrl = if (obtainiumInstalled) {
                OBTAINIUM_ADD_APP_URL
            } else {
                OBTAINIUM_INSTALL_URL
            }
            val intent = Intent(Intent.ACTION_VIEW, obtainiumUrl.toUri()).apply {
                if (obtainiumInstalled) setPackage(OBTAINIUM_PACKAGE)
            }
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(
                    if (obtainiumInstalled) R.string.add_to_obtainium else R.string.install_obtainium
                ),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    if (obtainiumInstalled) {
                        R.string.add_to_obtainium_description
                    } else {
                        R.string.install_obtainium_description
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val OBTAINIUM_PACKAGE = "dev.imranr.obtainium"
private const val OBTAINIUM_INSTALL_URL = "https://obtainium.imranr.dev/"
private const val OBTAINIUM_ADD_APP_URL = "obtainium://app/%7B%22id%22%3A%22dev.tricked.solidverdant%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FTricked-dev%2FSolidVerdant%22%2C%22author%22%3A%22Tricked-dev%22%2C%22name%22%3A%22SolidVerdant%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%7D%22%7D"

private fun isObtainiumInstalled(context: Context): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            OBTAINIUM_PACKAGE,
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(OBTAINIUM_PACKAGE, 0)
    }
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

/**
 * Get the SHA-256 hash of the app's signing certificate
 */
private fun getSigningCertificateHash(context: Context): String {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
        }

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        val signature = signatures?.firstOrNull() ?: return "Unknown"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(signature.toByteArray())
        hashBytes.joinToString(":") { "%02X".format(it) }
    } catch (e: Exception) {
        "Unknown"
    }
}

/**
 * Copy text to clipboard and show a toast
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("SolidVerdant", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
}

/**
 * Format elapsed time as HH:MM:SS
 */
internal fun formatElapsedTime(seconds: Long): String {
    // Defensive floor: a device clock behind the entry's start must never render as "-1:-5:-3".
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val secs = safeSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

/**
 * Deterministic client-side validation for a manually edited or created time entry. Server policy
 * stays authoritative (see [EntryTrustRules]); these are local guards so the user never silently
 * creates a ~24h entry from a typo, and is warned before saving an unusually long or overlapping
 * entry. Warnings do not block: an explicit Save is the user's confirmation.
 */
internal object EntryTimeValidator {
    /** An end clock-time earlier than start rolls to the next day only within this span; beyond it
     *  the inversion is treated as a mistake rather than an intended overnight shift. */
    val MAX_CROSS_MIDNIGHT: java.time.Duration = java.time.Duration.ofHours(18)
    /** Durations at or above this are plausible but worth confirming before saving. */
    val LONG_DURATION_WARNING: java.time.Duration = java.time.Duration.ofHours(12)
    /** Hard ceiling; a single entry longer than this is almost certainly an error. */
    val MAX_DURATION: java.time.Duration = java.time.Duration.ofHours(24)

    enum class Error { END_NOT_AFTER_START, TOO_LONG }
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

    fun evaluate(
        start: ZonedDateTime,
        end: ZonedDateTime,
        overlaps: Boolean = false,
        overlapProhibited: Boolean = false,
    ): Result {
        val duration = java.time.Duration.between(start, end)
        val error = when {
            !end.isAfter(start) -> Error.END_NOT_AFTER_START
            duration > MAX_DURATION -> Error.TOO_LONG
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
        color = if (isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSecondaryContainer
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            result.error?.let { error ->
                Text(
                    text = stringResource(
                        when (error) {
                            EntryTimeValidator.Error.END_NOT_AFTER_START -> R.string.entry_error_end_before_start
                            EntryTimeValidator.Error.TOO_LONG -> R.string.entry_error_duration_too_long
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
            result.warnings.forEach { warning ->
                Text(
                    text = when (warning) {
                        EntryTimeValidator.Warning.LONG_DURATION ->
                            stringResource(R.string.entry_warning_long_duration, durationHours)
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
private fun formatDate(date: LocalDate, context: android.content.Context): String {
    val today = LocalDate.now()
    return when {
        date == today -> context.getString(R.string.today)
        date == today.minusDays(1) -> context.getString(R.string.yesterday)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

// Shared formatter instances: DateTimeFormatter.ofPattern() builds a new parser every call,
// which is measurable when every visible history row formats its time range during a scroll.
private val hourMinuteFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val entryDateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

/**
 * Format time range
 */
private fun formatTimeRange(start: String, end: String?): String {
    val startFormatted = IsoTimes.hourMinute(start) ?: return "Invalid time"
    return if (end != null) {
        val endFormatted = IsoTimes.hourMinute(end) ?: return "Invalid time"
        "$startFormatted - $endFormatted"
    } else {
        "$startFormatted - now"
    }
}

/**
 * Format duration in seconds to HH:MM:SS
 */
private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

/** Format a day total without seconds to keep history headers compact. */
private fun formatCompactDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatEditableDuration(minutes: Long): String {
    val safeMinutes = minutes.coerceAtLeast(0)
    val hours = safeMinutes / 60
    val remainingMinutes = safeMinutes % 60
    return when {
        hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
        hours > 0 -> "${hours}h"
        else -> "${remainingMinutes}m"
    }
}

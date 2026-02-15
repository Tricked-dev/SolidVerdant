package dev.tricked.solidverdant.ui.tracking

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.util.NotificationPermissionHelper
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    user: User?,
    uiState: TrackingUiState,
    alwaysShowNotifications: Boolean,
    onAlwaysShowNotificationsChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
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
    onUpdatePastEntry: (TimeEntry, String?, String?, String?, List<String>, Boolean) -> Unit,
    onDeleteEntry: (String) -> Unit,
    getGroupedEntries: () -> Map<LocalDate, List<TimeEntry>>
) {
    var showEditDialog by remember { mutableStateOf<TimeEntry?>(null) }
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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
                    ) {
                        Text(
                            text = stringResource(R.string.settings_menu),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

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
                    }

                    // Logout button at the bottom
                    Button(
                        onClick = {
                            scope.launch { drawerState.close() }
                            onLogout()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
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
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.settings_menu)
                            )
                        }
                    },
                    actions = {
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
                                    contentDescription = "Enable notifications"
                                )
                            }
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh)
                            )
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = onRefresh,
                modifier = Modifier.padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }

                    // Error display
                    if (uiState.error != null) {
                        item {
                            ErrorCard(error = uiState.error)
                        }
                    }

                    // Tracking controls
                    item {
                        TrackingControls(
                            uiState = uiState,
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

                    // Time entries history
                    val groupedEntries = getGroupedEntries()
                    val filteredGroupedEntries = groupedEntries.mapValues { (_, entries) ->
                        entries.filter { (it.duration ?: 0) > 0 }
                    }.filterValues { it.isNotEmpty() }

                    if (filteredGroupedEntries.isNotEmpty()) {
                        filteredGroupedEntries.forEach { (date, entries) ->
                            item(key = "header_$date") {
                                DateHeader(date = date)
                            }

                            // Group entries by project and task
                            val groupedByProjectTask = entries.groupBy {
                                "${it.projectId}_${it.taskId}_${it.description ?: ""}"
                            }

                            groupedByProjectTask.forEach { (_, groupedEntries) ->
                                item(key = "group_${groupedEntries.first().id}") {
                                    CollapsibleTimeEntryGroup(
                                        entries = groupedEntries,
                                        projects = uiState.projects,
                                        tasks = uiState.tasks,
                                        onEdit = { entry -> showEditDialog = entry },
                                        onDelete = { entry -> onDeleteEntry(entry.id) }
                                    )
                                }
                            }
                        }
                    }

                    // Empty state
                    if (uiState.timeEntries.isEmpty() && !uiState.isLoading) {
                        item {
                            Text(
                                text = stringResource(R.string.no_time_entries),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Edit dialog
    showEditDialog?.let { entry ->
        EditTimeEntryDialog(
            entry = entry,
            projects = uiState.projects,
            tasks = uiState.tasks,
            tags = uiState.tags,
            onDismiss = { showEditDialog = null },
            onSave = { description, projectId, taskId, tagIds, billable ->
                onUpdatePastEntry(entry, description, projectId, taskId, tagIds, billable)
                showEditDialog = null
            }
        )
    }
}

/**
 * Tracking controls card with timer and input fields
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TrackingControls(
    uiState: TrackingUiState,
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
                    text = formatElapsedTime(uiState.elapsedSeconds),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 48.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
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

            // Description field
            OutlinedTextField(
                value = uiState.editingDescription,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.description)) },
                placeholder = { Text(stringResource(R.string.what_are_you_working_on)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(8.dp)
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
                enabled = !uiState.isLoading
            )

            // Tags selector
            if (uiState.tags.isNotEmpty()) {
                TagsSelector(
                    selectedTagIds = uiState.editingTags,
                    availableTags = uiState.tags,
                    onTagsChanged = onTagsChange,
                    enabled = !uiState.isLoading
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
                    enabled = !uiState.isLoading
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
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.update), fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onPause,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isLoading,
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
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isLoading,
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
                        onClick = onResume,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isLoading,
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
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isLoading,
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
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
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
 * Combined Project/Task dropdown selector
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectTaskDropdown(
    selectedProjectId: String?,
    selectedTaskId: String?,
    projects: List<Project>,
    tasks: List<Task>,
    onSelectionChanged: (projectId: String?, taskId: String?) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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

    // Filter projects and tasks based on search query
    val filteredProjects = remember(searchQuery, projects) {
        if (searchQuery.isBlank()) {
            projects
        } else {
            projects.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val filteredTasks = remember(searchQuery, tasks) {
        if (searchQuery.isBlank()) {
            tasks
        } else {
            tasks.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (enabled) {
                expanded = it
                if (!it) searchQuery = "" // Clear search when closing
            }
        }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.project_task)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled),
            enabled = enabled,
            shape = RoundedCornerShape(8.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                searchQuery = ""
            }
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            // No project option
            DropdownMenuItem(
                text = { Text(stringResource(R.string.no_project)) },
                onClick = {
                    onSelectionChanged(null, null)
                    expanded = false
                    searchQuery = ""
                }
            )

            // Projects and their tasks
            filteredProjects.forEach { project ->
                val projectTasks = filteredTasks.filter { it.projectId == project.id }

                // Project item with color dot
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(project.color)))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    },
                    onClick = {
                        onSelectionChanged(project.id, null)
                        expanded = false
                        searchQuery = ""
                    }
                )

                // Tasks for this project (indented)
                projectTasks.forEach { task ->
                    DropdownMenuItem(
                        text = {
                            Row {
                                Spacer(modifier = Modifier.width(24.dp))
                                Text(
                                    text = task.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onSelectionChanged(project.id, task.id)
                            expanded = false
                            searchQuery = ""
                        }
                    )
                }
            }

            // Show message if no results
            if (searchQuery.isNotBlank() && filteredProjects.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.no_results_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { }
                )
            }
        }
    }
}

/**
 * Tags selector with chips
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSelector(
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
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableTags.forEach { tag ->
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
private fun DateHeader(date: LocalDate) {
    val context = LocalContext.current
    Text(
        text = formatDate(date, context),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        fontSize = 12.sp
    )
}

/**
 * Collapsible group of time entries with same project/task
 */
@Composable
private fun CollapsibleTimeEntryGroup(
    entries: List<TimeEntry>,
    projects: List<Project>,
    tasks: List<Task>,
    onEdit: (TimeEntry) -> Unit,
    onDelete: (TimeEntry) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (entries.size == 1) {
            // Single entry, show normally
            CompactTimeEntryRow(
                entry = entries.first(),
                projects = projects,
                tasks = tasks,
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
                    projects = projects,
                    tasks = tasks,
                    onEdit = { isExpanded = true },
                    onDelete = { /* Don't allow deleting grouped entries */ },
                    count = entries.size,
                    totalDuration = entries.sumOf { it.duration ?: 0 }
                )
            } else {
                // Show all entries
                entries.forEach { entry ->
                    CompactTimeEntryRow(
                        entry = entry,
                        projects = projects,
                        tasks = tasks,
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
                        text = "Collapse ${entries.size} entries",
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
    projects: List<Project>,
    tasks: List<Task>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    count: Int? = null,
    totalDuration: Int? = null,
    isIndented: Boolean = false
) {
    val project = projects.find { it.id == entry.projectId }
    val task = tasks.find { it.id == entry.taskId }

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
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

            // Project • Task and time range on same line
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Project color dot + name
                if (project != null) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(project.color)))
                    )
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    if (task != null) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Text(
                            text = task.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Time range
                Text(
                    text = formatTimeRange(entry.start, entry.end),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }

        // Right side: Duration and action icons
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Duration (use totalDuration if grouped, otherwise entry duration)
            Text(
                text = formatDuration(totalDuration ?: entry.duration ?: 0),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )

            // Only show action buttons if not grouped or if expanded
            if (count == null || count == 1) {
                // Edit button
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
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
                    modifier = Modifier.size(32.dp)
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
 * Edit time entry dialog
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditTimeEntryDialog(
    entry: TimeEntry,
    projects: List<Project>,
    tasks: List<Task>,
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onSave: (String?, String?, String?, List<String>, Boolean) -> Unit
) {
    var description by remember { mutableStateOf(entry.description ?: "") }
    var projectId by remember { mutableStateOf(entry.projectId) }
    var taskId by remember { mutableStateOf(entry.taskId) }
    var selectedTags by remember { mutableStateOf(entry.tags.map { it.id }) }
    var billable by remember { mutableStateOf(entry.billable) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.edit_time_entry),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth(),
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
                                billable
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
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
 * Format elapsed time as HH:MM:SS
 */
private fun formatElapsedTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
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

/**
 * Format time range
 */
private fun formatTimeRange(start: String, end: String?): String {
    return try {
        val startTime = ZonedDateTime.parse(start, DateTimeFormatter.ISO_DATE_TIME)
        val startFormatted = startTime.format(DateTimeFormatter.ofPattern("HH:mm"))

        if (end != null) {
            val endTime = ZonedDateTime.parse(end, DateTimeFormatter.ISO_DATE_TIME)
            val endFormatted = endTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            "$startFormatted - $endFormatted"
        } else {
            "$startFormatted - now"
        }
    } catch (e: Exception) {
        "Invalid time"
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

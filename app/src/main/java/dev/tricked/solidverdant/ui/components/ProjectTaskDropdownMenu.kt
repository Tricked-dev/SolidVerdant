/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.ui.theme.Dimens

private enum class PickerKind { PROJECT, TASK }

/** Shared, separately searchable project and task selectors used by entry forms. */
@Composable
fun ProjectTaskDropdown(
    projects: List<Project>,
    tasks: List<Task>,
    selectedProjectId: String?,
    selectedTaskId: String?,
    onSelectionChanged: (projectId: String?, taskId: String?) -> Unit,
    enabled: Boolean = true,
    showProjectColors: Boolean = false,
    rounded: Boolean = false,
    onCreateProject: ((String) -> Unit)? = null,
    onCreateTask: ((String, String) -> Unit)? = null,
) {
    var activePicker by rememberSaveable { mutableStateOf<PickerKind?>(null) }
    var projectQuery by rememberSaveable { mutableStateOf("") }
    var taskQuery by rememberSaveable { mutableStateOf("") }

    val selectedProject = remember(projects, selectedProjectId) {
        projects.firstOrNull { it.id == selectedProjectId }
    }
    val tasksByProject = remember(tasks) { groupTasksByProject(tasks) }
    val projectTasks = remember(tasksByProject, selectedProjectId) {
        selectedProjectId?.let { tasksByProject[it] }.orEmpty()
    }
    val selectedTask = remember(projectTasks, selectedTaskId) {
        projectTasks.firstOrNull { it.id == selectedTaskId }
    }
    val fieldShape = if (rounded) MaterialTheme.shapes.medium else MaterialTheme.shapes.small

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
    ) {
        SearchableSelectorField(
            value = selectedProject?.name ?: stringResource(R.string.no_project),
            label = stringResource(R.string.project),
            expanded = activePicker == PickerKind.PROJECT,
            onExpandedChange = { expanded -> activePicker = PickerKind.PROJECT.takeIf { expanded } },
            enabled = enabled,
            shape = fieldShape,
            testTag = EditTimeEntryTestTags.PROJECT_SELECTOR,
        )
        SearchableSelectorField(
            value = when {
                selectedProject == null -> stringResource(R.string.select_project_first)
                selectedTask == null -> stringResource(R.string.no_task)
                else -> selectedTask.name
            },
            label = stringResource(R.string.task),
            expanded = activePicker == PickerKind.TASK,
            onExpandedChange = { expanded -> activePicker = PickerKind.TASK.takeIf { expanded } },
            enabled = enabled && selectedProject != null,
            shape = fieldShape,
            testTag = EditTimeEntryTestTags.TASK_SELECTOR,
        )
    }

    when (activePicker) {
        PickerKind.PROJECT -> ProjectPickerDialog(
            projects = projects,
            selectedProjectId = selectedProjectId,
            searchQuery = projectQuery,
            onSearchQueryChange = { projectQuery = it },
            onSelect = { projectId ->
                val retainedTaskId = selectedTaskId.takeIf { projectId == selectedProjectId }
                onSelectionChanged(projectId, retainedTaskId)
                activePicker = null
                projectQuery = ""
            },
            onClose = {
                activePicker = null
                projectQuery = ""
            },
            showProjectColors = showProjectColors,
            onCreateProject = onCreateProject,
        )

        PickerKind.TASK -> selectedProject?.id?.let { projectId ->
            TaskPickerDialog(
                tasks = projectTasks,
                projectId = projectId,
                selectedTaskId = selectedTaskId,
                searchQuery = taskQuery,
                onSearchQueryChange = { taskQuery = it },
                onSelect = { taskId ->
                    onSelectionChanged(projectId, taskId)
                    activePicker = null
                    taskQuery = ""
                },
                onClose = {
                    activePicker = null
                    taskQuery = ""
                },
                onCreateTask = onCreateTask,
            )
        }

        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchableSelectorField(
    value: String,
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    testTag: String,
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) onExpandedChange(it) }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            enabled = enabled,
            singleLine = true,
            shape = shape,
        )
    }
}

internal fun filterProjects(projects: List<Project>, query: String): List<Project> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return projects
    return projects.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
}

internal fun filterTasks(tasks: List<Task>, query: String): List<Task> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return tasks
    return tasks.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
}

internal fun groupTasksByProject(tasks: List<Task>): Map<String, List<Task>> = tasks.groupBy { it.projectId }

@Composable
private fun ProjectPickerDialog(
    projects: List<Project>,
    selectedProjectId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (String?) -> Unit,
    onClose: () -> Unit,
    showProjectColors: Boolean,
    onCreateProject: ((String) -> Unit)?,
) {
    val normalizedQuery = searchQuery.trim()
    val filteredProjects = remember(projects, normalizedQuery) { filterProjects(projects, normalizedQuery) }
    PickerDialog(
        title = stringResource(R.string.select_project),
        searchPlaceholder = stringResource(R.string.search_projects),
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onClose = onClose,
        listTestTag = EditTimeEntryTestTags.PROJECT_LIST,
        searchTestTag = EditTimeEntryTestTags.PROJECT_SEARCH,
    ) {
        item(key = "no_project") {
            PickerItem(
                text = stringResource(R.string.no_project),
                selected = selectedProjectId == null,
                onClick = { onSelect(null) },
            )
        }
        items(filteredProjects, key = { "project_${it.id}" }) { project ->
            PickerItem(
                text = project.name,
                selected = project.id == selectedProjectId,
                onClick = { onSelect(project.id) },
                leadingContent = if (showProjectColors) {
                    {
                        Box(
                            modifier = Modifier
                                .size(Dimens.Space12)
                                .clip(CircleShape)
                                .background(Color(project.color.toColorInt())),
                        )
                    }
                } else {
                    null
                },
            )
        }
        if (filteredProjects.isEmpty()) {
            emptyResultItem(searchQuery, R.string.no_projects_available)
        }
        if (normalizedQuery.isNotBlank() && onCreateProject != null) {
            item(key = "create_project") {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.create_project_named, normalizedQuery)) },
                    onClick = {
                        onCreateProject(normalizedQuery)
                        onClose()
                    },
                    modifier = Modifier.testTag(EditTimeEntryTestTags.CREATE_PROJECT),
                )
            }
        }
    }
}

@Composable
private fun TaskPickerDialog(
    tasks: List<Task>,
    projectId: String,
    selectedTaskId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (String?) -> Unit,
    onClose: () -> Unit,
    onCreateTask: ((String, String) -> Unit)?,
) {
    val normalizedQuery = searchQuery.trim()
    val filteredTasks = remember(tasks, normalizedQuery) { filterTasks(tasks, normalizedQuery) }
    PickerDialog(
        title = stringResource(R.string.select_task),
        searchPlaceholder = stringResource(R.string.search_tasks),
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onClose = onClose,
        listTestTag = EditTimeEntryTestTags.TASK_LIST,
        searchTestTag = EditTimeEntryTestTags.TASK_SEARCH,
    ) {
        item(key = "no_task") {
            PickerItem(
                text = stringResource(R.string.no_task),
                selected = selectedTaskId == null,
                onClick = { onSelect(null) },
            )
        }
        items(filteredTasks, key = { "task_${it.id}" }) { task ->
            PickerItem(
                text = task.name,
                selected = task.id == selectedTaskId,
                onClick = { onSelect(task.id) },
            )
        }
        if (filteredTasks.isEmpty()) {
            emptyResultItem(searchQuery, R.string.no_tasks_available)
        }
        if (normalizedQuery.isNotBlank() && onCreateTask != null) {
            item(key = "create_task") {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.create_task_named, normalizedQuery)) },
                    onClick = {
                        onCreateTask(normalizedQuery, projectId)
                        onClose()
                    },
                    modifier = Modifier.testTag(EditTimeEntryTestTags.CREATE_TASK),
                )
            }
        }
    }
}

private fun LazyListScope.emptyResultItem(searchQuery: String, emptyMessage: Int) {
    item(key = "empty_result") {
        Text(
            text = stringResource(if (searchQuery.isBlank()) emptyMessage else R.string.no_results_found),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.Space24, vertical = Dimens.Space16),
        )
    }
}

@Composable
private fun PickerItem(text: String, selected: Boolean, onClick: () -> Unit, leadingContent: (@Composable () -> Unit)? = null) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        onClick = onClick,
        leadingIcon = leadingContent,
        trailingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else {
            null
        },
    )
}

@Composable
private fun PickerDialog(
    title: String,
    searchPlaceholder: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    listTestTag: String,
    searchTestTag: String,
    content: LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxWidth < Dimens.NarrowCalendarWidth
            Surface(
                modifier = if (compact) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth().widthIn(max = Dimens.PickerMaxWidth).heightIn(max = Dimens.PickerMaxHeight)
                },
                shape = if (compact) RectangleShape else MaterialTheme.shapes.extraLarge,
                tonalElevation = if (compact) Dimens.Space1 else Dimens.Space8,
            ) {
                Column(
                    modifier = if (compact) {
                        Modifier.fillMaxSize().safeDrawingPadding()
                    } else {
                        Modifier.fillMaxWidth()
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = Dimens.Space24, end = Dimens.Space8, top = Dimens.Space8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onClose, modifier = Modifier.size(Dimens.MinTouchTarget)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(searchPlaceholder) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.Space16, vertical = Dimens.Space8)
                            .testTag(searchTestTag),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag(listTestTag),
                        content = content,
                    )
                    Spacer(Modifier.size(Dimens.Space8))
                }
            }
        }
    }
}

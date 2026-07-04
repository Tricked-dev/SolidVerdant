package dev.tricked.solidverdant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task

/** Shared searchable project/task selector used by tracking and quick-start forms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectTaskDropdown(
    projects: List<Project>,
    tasks: List<Task>,
    displayText: String,
    onSelectionChanged: (projectId: String?, taskId: String?) -> Unit,
    enabled: Boolean = true,
    showProjectColors: Boolean = false,
    rounded: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val close = {
        expanded = false
        searchQuery = ""
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (enabled) {
                expanded = it
                if (!it) searchQuery = ""
            }
        }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.project_task)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
            enabled = enabled,
            shape = if (rounded) RoundedCornerShape(8.dp) else OutlinedTextFieldDefaultsShape
        )

    }

    if (expanded) {
        ProjectTaskPickerDialog(
            projects = projects,
            tasks = tasks,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onSelectionChanged = onSelectionChanged,
            onClose = close,
            showProjectColors = showProjectColors
        )
    }
}

private val OutlinedTextFieldDefaultsShape = RoundedCornerShape(4.dp)

@Composable
private fun ProjectTaskPickerDialog(
    projects: List<Project>,
    tasks: List<Task>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectionChanged: (projectId: String?, taskId: String?) -> Unit,
    onClose: () -> Unit,
    showProjectColors: Boolean
) {
    val normalizedQuery = searchQuery.trim()
    val filteredTasks = remember(normalizedQuery, tasks) {
        tasks.filter { normalizedQuery.isBlank() || it.name.contains(normalizedQuery, true) }
    }
    val matchingTaskProjectIds = remember(filteredTasks, normalizedQuery) {
        if (normalizedQuery.isBlank()) emptySet() else filteredTasks.map { it.projectId }.toSet()
    }
    val filteredProjects = remember(normalizedQuery, projects, matchingTaskProjectIds) {
        projects.filter {
            normalizedQuery.isBlank() ||
                it.name.contains(normalizedQuery, true) ||
                it.id in matchingTaskProjectIds
        }
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.project_task),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                item {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.no_project)) },
                        onClick = {
                            onSelectionChanged(null, null)
                            onClose()
                        }
                    )
                }
                filteredProjects.forEach { project ->
                    item(key = "project_${project.id}") {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (showProjectColors) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(android.graphics.Color.parseColor(project.color)))
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(project.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            },
                            onClick = {
                                onSelectionChanged(project.id, null)
                                onClose()
                            }
                        )
                    }

                    filteredTasks.filter { it.projectId == project.id }.forEach { task ->
                        item(key = "task_${task.id}") {
            DropdownMenuItem(
                text = {
                    Row {
                        Spacer(Modifier.width(24.dp))
                        Text(
                            task.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                onClick = {
                    onSelectionChanged(project.id, task.id)
                    onClose()
                }
            )
                        }
                    }
        }
                if (searchQuery.isNotBlank() && filteredProjects.isEmpty()) {
                    item {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.no_results_found),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {}
                        )
                    }
                }
            }
        }
    }
}

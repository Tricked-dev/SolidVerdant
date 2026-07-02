package dev.tricked.solidverdant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = close
        ) {
            ProjectTaskDropdownMenuContent(
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
}

private val OutlinedTextFieldDefaultsShape = RoundedCornerShape(4.dp)

@Composable
private fun ProjectTaskDropdownMenuContent(
    projects: List<Project>,
    tasks: List<Task>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectionChanged: (projectId: String?, taskId: String?) -> Unit,
    onClose: () -> Unit,
    showProjectColors: Boolean
) {
    val filteredProjects = remember(searchQuery, projects) {
        projects.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }
    }
    val filteredTasks = remember(searchQuery, tasks) {
        tasks.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }
    }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true
    )

    DropdownMenuItem(
        text = { Text(stringResource(R.string.no_project)) },
        onClick = {
            onSelectionChanged(null, null)
            onClose()
        }
    )

    filteredProjects.forEach { project ->
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

        filteredTasks.filter { it.projectId == project.id }.forEach { task ->
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

    if (searchQuery.isNotBlank() && filteredProjects.isEmpty()) {
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

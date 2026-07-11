/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.AppThemeMode
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.service.TimeTrackingNotificationService
import dev.tricked.solidverdant.ui.components.ProjectTaskDropdown
import dev.tricked.solidverdant.ui.theme.SolidVerdantTheme

/**
 * Activity for selecting a project when starting time tracking from Quick Settings.
 *
 * This activity closes immediately after selection - the actual API call
 * happens in the TileService to survive the activity lifecycle.
 */
@AndroidEntryPoint
class ProjectSelectionActivity : ComponentActivity() {

    private val viewModel: ProjectSelectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val appTheme by viewModel.appTheme.collectAsState(initial = AppThemeMode.SYSTEM)
            SolidVerdantTheme(themeMode = appTheme) {
                ProjectSelectionContent(
                    viewModel = viewModel,
                    onStartTracking = { projectId, taskId, description, projectName, taskName ->
                        TimeTrackingNotificationService.quickStart(
                            context = this,
                            projectId = projectId,
                            taskId = taskId,
                            description = description,
                            projectName = projectName,
                            taskName = taskName,
                        )

                        // Close immediately - TileService handles the rest
                        finish()
                    },
                    onCancel = {
                        finish()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSelectionContent(
    viewModel: ProjectSelectionViewModel,
    onStartTracking: (projectId: String?, taskId: String?, description: String, projectName: String?, taskName: String?) -> Unit,
    onCancel: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProjects()
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { viewModel.loadProjects(forceRefresh = true) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
        ) {
            when {
                uiState.isLoading && uiState.projects.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.loading_projects))
                    }
                }

                uiState.error != null && uiState.projects.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.error_format, uiState.error ?: ""),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadProjects(forceRefresh = true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }

                else -> {
                    StartTrackingForm(
                        projects = uiState.projects.filter { !it.isArchived },
                        tasks = uiState.tasks.filter { !it.isDone },
                        onStartTracking = onStartTracking,
                        onCancel = onCancel,
                    )
                }
            }
        }
    }
}

sealed class ProjectTaskSelection {
    object NoProject : ProjectTaskSelection()
    data class ProjectOnly(val project: Project) : ProjectTaskSelection()
    data class ProjectWithTask(val project: Project, val task: Task) : ProjectTaskSelection()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartTrackingForm(
    projects: List<Project>,
    tasks: List<Task>,
    onStartTracking: (projectId: String?, taskId: String?, description: String, projectName: String?, taskName: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var selection by remember { mutableStateOf<ProjectTaskSelection>(ProjectTaskSelection.NoProject) }
    var description by remember { mutableStateOf("") }

    val displayText = when (selection) {
        is ProjectTaskSelection.NoProject -> stringResource(R.string.no_project)
        is ProjectTaskSelection.ProjectOnly -> (selection as ProjectTaskSelection.ProjectOnly).project.name
        is ProjectTaskSelection.ProjectWithTask -> {
            val sel = selection as ProjectTaskSelection.ProjectWithTask
            "${sel.project.name} - ${sel.task.name}"
        }
    }

    Text(
        text = stringResource(R.string.start_time_tracking),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 16.dp),
    )

    ProjectTaskDropdown(
        projects = projects,
        tasks = tasks,
        displayText = displayText,
        onSelectionChanged = { projectId, taskId ->
            val project = projects.find { it.id == projectId }
            val task = tasks.find { it.id == taskId }
            selection = when {
                project == null -> ProjectTaskSelection.NoProject
                task == null -> ProjectTaskSelection.ProjectOnly(project)
                else -> ProjectTaskSelection.ProjectWithTask(project, task)
            }
        },
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text(stringResource(R.string.description_optional)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    Spacer(modifier = Modifier.height(20.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.cancel))
        }
        Button(
            onClick = {
                val (projectId, taskId, projectName, taskName) = when (selection) {
                    is ProjectTaskSelection.NoProject ->
                        listOf(null, null, null, null)

                    is ProjectTaskSelection.ProjectOnly -> {
                        val proj = (selection as ProjectTaskSelection.ProjectOnly).project
                        listOf(proj.id, null, proj.name, null)
                    }

                    is ProjectTaskSelection.ProjectWithTask -> {
                        val sel = selection as ProjectTaskSelection.ProjectWithTask
                        listOf(sel.project.id, sel.task.id, sel.project.name, sel.task.name)
                    }
                }
                onStartTracking(
                    projectId as String?,
                    taskId as String?,
                    description,
                    projectName as String?,
                    taskName as String?,
                )
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.start))
        }
    }
}

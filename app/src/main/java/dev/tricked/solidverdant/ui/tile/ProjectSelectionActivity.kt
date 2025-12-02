package dev.tricked.solidverdant.ui.tile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.ui.theme.SolidVerdantTheme

/**
 * Activity for selecting a project when starting time tracking from Quick Settings
 */
@AndroidEntryPoint
class ProjectSelectionActivity : ComponentActivity() {

    private val viewModel: ProjectSelectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SolidVerdantTheme {
                ProjectSelectionContent(
                    viewModel = viewModel,
                    onStartTracking = { projectId, taskId, description, projectName, taskName ->
                        // Broadcast to tile service for optimistic update
                        val broadcastIntent =
                            Intent("dev.tricked.solidverdant.TIME_TRACKING_STARTED").apply {
                                putExtra("PROJECT_NAME", projectName)
                                putExtra("TASK_NAME", taskName)
                                setPackage(packageName)
                            }
                        sendBroadcast(broadcastIntent)

                        viewModel.startTracking(projectId, taskId, description)
                        finish()
                    },
                    onCancel = {
                        finish()
                    }
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
    onCancel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProjects()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
    ) {
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading projects...")
                }
            }

            uiState.error != null -> {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }

            else -> {
                StartTrackingForm(
                    projects = uiState.projects.filter { !it.isArchived },
                    tasks = uiState.tasks.filter { !it.isDone },
                    onStartTracking = onStartTracking,
                    onCancel = onCancel
                )
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
    onCancel: () -> Unit
) {
    var selection by remember { mutableStateOf<ProjectTaskSelection>(ProjectTaskSelection.NoProject) }
    var description by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    // Build display text
    val displayText = when (selection) {
        is ProjectTaskSelection.NoProject -> "No Project"
        is ProjectTaskSelection.ProjectOnly -> (selection as ProjectTaskSelection.ProjectOnly).project.name
        is ProjectTaskSelection.ProjectWithTask -> {
            val sel = selection as ProjectTaskSelection.ProjectWithTask
            "${sel.project.name} - ${sel.task.name}"
        }
    }

    Text(
        text = "Start Time Tracking",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    // Combined Project/Task dropdown
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Project / Task") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // No project option
            DropdownMenuItem(
                text = { Text("No Project") },
                onClick = {
                    selection = ProjectTaskSelection.NoProject
                    expanded = false
                }
            )

            // Projects and their tasks
            projects.forEach { project ->
                // Project item
                DropdownMenuItem(
                    text = {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    onClick = {
                        selection = ProjectTaskSelection.ProjectOnly(project)
                        expanded = false
                    }
                )

                // Tasks for this project (indented)
                val projectTasks = tasks.filter { it.projectId == project.id }
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
                            selection = ProjectTaskSelection.ProjectWithTask(project, task)
                            expanded = false
                        }
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Description field
    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Description (Optional)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(20.dp))

    // Buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        ) {
            Text("Cancel")
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
                    taskName as String?
                )
            },
            modifier = Modifier.weight(1f)
        ) {
            Text("Start")
        }
    }
}

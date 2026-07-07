/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.repository.EntryTemplate

/** Human label for a template: its name, else description, else project name, else a fallback. */
@Composable
fun templateDisplayLabel(template: EntryTemplate, projects: List<Project>): String {
    template.name?.takeIf { it.isNotBlank() }?.let { return it }
    template.description?.takeIf { it.isNotBlank() }?.let { return it }
    template.projectId?.let { id -> projects.firstOrNull { it.id == id }?.name }?.let { return it }
    return stringResource(R.string.template_untitled)
}

/** Short "Project • Task" (names resolved from the catalogue) for a template row/chip. */
fun templateProjectTaskSummary(template: EntryTemplate, projects: List<Project>, tasks: List<Task>): String? {
    val projectName = template.projectId?.let { id -> projects.firstOrNull { it.id == id }?.name }
    val taskName = template.taskId?.let { id -> tasks.firstOrNull { it.id == id }?.name }
    return when {
        projectName != null && taskName != null -> "$projectName • $taskName"
        projectName != null -> projectName
        else -> null
    }
}

/** Build the localized "why this field won't be applied" lines for an unavailable template. */
@Composable
fun templateIssueMessages(resolution: TemplateResolution): List<String> = buildList {
    when (resolution.projectStatus) {
        RefStatus.ARCHIVED -> add(stringResource(R.string.templates_issue_project_archived))
        RefStatus.MISSING -> add(stringResource(R.string.templates_issue_project_missing))
        else -> {}
    }
    when (resolution.taskStatus) {
        RefStatus.ARCHIVED -> add(stringResource(R.string.templates_issue_task_archived))
        RefStatus.MISSING -> add(stringResource(R.string.templates_issue_task_missing))
        else -> {}
    }
    if (resolution.droppedTagCount > 0) {
        add(
            pluralStringResource(
                R.plurals.templates_issue_tags_dropped,
                resolution.droppedTagCount,
                resolution.droppedTagCount,
            ),
        )
    }
}

/** Renders the issue lines as a compact block. */
@Composable
fun TemplateIssueList(resolution: TemplateResolution, modifier: Modifier = Modifier) {
    val messages = templateIssueMessages(resolution)
    if (messages.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        messages.forEach { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

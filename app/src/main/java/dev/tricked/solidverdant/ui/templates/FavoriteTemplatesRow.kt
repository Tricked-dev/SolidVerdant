package dev.tricked.solidverdant.ui.templates

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.repository.EntryTemplate

/**
 * Quick-start chip row shown on the Track screen while idle (gap analysis #1). Favorites come first,
 * then recents (see [TemplateOrdering]). One tap resolves the template against the current catalogue
 * and starts a timer; when the description has placeholders or a referenced item is unavailable, a
 * dialog collects/confirms first so nothing is submitted silently.
 */
@Composable
fun FavoriteTemplatesRow(
    templates: List<EntryTemplate>,
    projects: List<Project>,
    tasks: List<Task>,
    tags: List<Tag>,
    onStart: (TemplateStart) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (templates.isEmpty()) return

    var pending by remember { mutableStateOf<EntryTemplate?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.templates_quick_start_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            templates.forEach { template ->
                val label = templateDisplayLabel(template, projects)
                val resolution = remember(template, projects, tasks, tags) {
                    TemplateResolver.resolve(template, projects, tasks, tags)
                }
                val startDesc = stringResource(R.string.templates_start_content_desc, label)
                val warnDesc = stringResource(R.string.templates_unavailable_content_desc)
                AssistChip(
                    onClick = {
                        val placeholders = TemplatePlaceholders.extract(template.description)
                        if (placeholders.isEmpty() && !resolution.hasIssues) {
                            onStart(resolution.toStart())
                        } else {
                            pending = template
                        }
                    },
                    label = { Text(label) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (template.isFavorite) Icons.Filled.Star else Icons.Filled.PlayArrow,
                            contentDescription = if (template.isFavorite) {
                                stringResource(R.string.templates_reason_favorite)
                            } else {
                                null
                            },
                        )
                    },
                    trailingIcon = if (resolution.hasIssues) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = warnDesc,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = startDesc },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }
    }

    pending?.let { template ->
        val resolution = remember(template, projects, tasks, tags) {
            TemplateResolver.resolve(template, projects, tasks, tags)
        }
        TemplateStartDialog(
            template = template,
            resolution = resolution,
            onStart = { start ->
                onStart(start)
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

/**
 * Collects placeholder values and/or confirms an unavailable-field start for a template. Placeholder
 * tokens (`{topic}`) are shown as fields; unresolved fields left blank stay verbatim.
 */
@Composable
private fun TemplateStartDialog(
    template: EntryTemplate,
    resolution: TemplateResolution,
    onStart: (TemplateStart) -> Unit,
    onDismiss: () -> Unit,
) {
    val placeholders = remember(template) { TemplatePlaceholders.extract(template.description) }
    val values = remember(template) { mutableStateMapOf<String, String>() }

    val titleRes = if (placeholders.isNotEmpty()) {
        R.string.templates_fill_details_title
    } else {
        R.string.templates_issues_title
    }
    val confirmRes = if (resolution.hasIssues && placeholders.isEmpty()) {
        R.string.templates_start_anyway
    } else {
        R.string.start
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                placeholders.forEach { token ->
                    OutlinedTextField(
                        value = values[token].orEmpty(),
                        onValueChange = { values[token] = it },
                        label = { Text(token) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (resolution.hasIssues) {
                    TemplateIssueList(resolution)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val filled = TemplatePlaceholders.fill(template.description, values.toMap())
                onStart(resolution.toStart(description = filled))
            }) {
                Text(stringResource(confirmRes), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

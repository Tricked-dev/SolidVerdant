/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.repository.EntryTemplate
import dev.tricked.solidverdant.ui.components.ProjectTaskDropdown

/**
 * Create/edit sheet for a template. Backed only by local state; the caller persists via [onSave]
 * (create) or [onUpdate] (edit). [projects]/[tasks] are the full catalogue so an archived current
 * selection is still shown, while only active items are offered in the picker.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorSheet(
    existing: EntryTemplate?,
    projects: List<Project>,
    tasks: List<Task>,
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onSave: (TemplateDraft) -> Unit,
    onUpdate: (EntryTemplate) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var projectId by remember(existing?.id) { mutableStateOf(existing?.projectId) }
    var taskId by remember(existing?.id) { mutableStateOf(existing?.taskId) }
    var selectedTags by remember(existing?.id) { mutableStateOf(existing?.tagIds ?: emptyList()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var billable by remember(existing?.id) { mutableStateOf(existing?.billable ?: false) }
    var favorite by remember(existing?.id) { mutableStateOf(existing?.isFavorite ?: false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val displayText = run {
        val projectName = projectId?.let { id -> projects.firstOrNull { it.id == id }?.name }
        val taskName = taskId?.let { id -> tasks.firstOrNull { it.id == id }?.name }
        when {
            projectId == null -> stringResource(R.string.templates_no_project)
            projectName != null && taskName != null -> "$projectName - $taskName"
            projectName != null -> projectName
            else -> stringResource(R.string.templates_no_project)
        }
    }

    val canSave = name.isNotBlank() ||
        projectId != null ||
        description.isNotBlank() ||
        selectedTags.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(
                    if (existing == null) {
                        R.string.template_editor_new_title
                    } else {
                        R.string.template_editor_edit_title
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.template_name_label)) },
                placeholder = { Text(stringResource(R.string.template_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            )

            ProjectTaskDropdown(
                projects = projects.filterNot { it.isArchived },
                tasks = tasks.filterNot { it.isDone },
                displayText = displayText,
                onSelectionChanged = { newProjectId, newTaskId ->
                    projectId = newProjectId
                    taskId = newTaskId
                },
                showProjectColors = true,
                rounded = true,
            )

            if (tags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.tags),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in selectedTags,
                                onClick = {
                                    selectedTags = if (tag.id in selectedTags) {
                                        selectedTags - tag.id
                                    } else {
                                        selectedTags + tag.id
                                    }
                                },
                                label = { Text(tag.name) },
                                shape = RoundedCornerShape(6.dp),
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description)) },
                supportingText = { Text(stringResource(R.string.template_description_hint)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = billable, onCheckedChange = { billable = it })
                Text(
                    text = stringResource(R.string.billable),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.template_favorite_label),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.template_favorite_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = favorite, onCheckedChange = { favorite = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val trimmedName = name.trim().takeIf { it.isNotEmpty() }
                        val trimmedDescription = description.trim().takeIf { it.isNotEmpty() }
                        if (existing == null) {
                            onSave(
                                TemplateDraft(
                                    name = trimmedName,
                                    projectId = projectId,
                                    taskId = taskId,
                                    description = trimmedDescription,
                                    tagIds = selectedTags,
                                    billable = billable,
                                    isFavorite = favorite,
                                ),
                            )
                        } else {
                            onUpdate(
                                existing.copy(
                                    name = trimmedName,
                                    projectId = projectId,
                                    taskId = taskId,
                                    description = trimmedDescription,
                                    tagIds = selectedTags,
                                    billable = billable,
                                    isFavorite = favorite,
                                ),
                            )
                        }
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** A "Cancel / Delete" confirmation dialog reused by rows and the editor. */
@Composable
fun TemplateDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.templates_delete_confirm_title)) },
        text = { Text(stringResource(R.string.templates_delete_confirm_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

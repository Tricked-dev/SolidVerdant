/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.repository.EntryTemplate
import dev.tricked.solidverdant.ui.components.ConfirmDialog
import dev.tricked.solidverdant.ui.components.ConsumeVerticalOverscroll
import dev.tricked.solidverdant.ui.components.DestructiveActionRow
import dev.tricked.solidverdant.ui.components.EntrySheetHeader
import dev.tricked.solidverdant.ui.components.GroupedDivider
import dev.tricked.solidverdant.ui.components.GroupedSection
import dev.tricked.solidverdant.ui.components.GroupedSwitchRow
import dev.tricked.solidverdant.ui.components.ProjectTaskDropdown
import dev.tricked.solidverdant.ui.components.SelectorStyle
import dev.tricked.solidverdant.ui.components.TagsSelector
import dev.tricked.solidverdant.ui.theme.Dimens

internal object TemplateEditorTestTags {
    const val SHEET = "template_editor_sheet"
    const val CANCEL = "template_editor_cancel"
    const val SAVE = "template_editor_save"
    const val NAME = "template_editor_name"
    const val DESCRIPTION = "template_editor_description"
    const val BILLABLE = "template_editor_billable"
    const val FAVORITE = "template_editor_favorite"
    const val DELETE = "template_editor_delete"
    const val DELETE_CONFIRM = "template_delete_confirm"
}

/**
 * Create/edit sheet for a template, laid out like the time-entry form: Cancel, the title and Save
 * across the top, the name and description as borderless cells, then project, task, tags and
 * billable as grouped rows, the favorite switch, and Delete when editing. Backed only by local
 * state; the caller persists via [onSave] (create), [onUpdate] (edit) or [onDelete]. [projects] and
 * [tasks] are the full catalogue; only active items are offered in the pickers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "LongParameterList")
fun TemplateEditorSheet(
    existing: EntryTemplate?,
    projects: List<Project>,
    tasks: List<Task>,
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onSave: (TemplateDraft) -> Unit,
    onUpdate: (EntryTemplate) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var projectId by remember(existing?.id) { mutableStateOf(existing?.projectId) }
    var taskId by remember(existing?.id) { mutableStateOf(existing?.taskId) }
    var selectedTags by remember(existing?.id) { mutableStateOf(existing?.tagIds ?: emptyList()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var billable by remember(existing?.id) { mutableStateOf(existing?.billable ?: false) }
    var favorite by remember(existing?.id) { mutableStateOf(existing?.isFavorite ?: false) }
    var confirmDelete by remember(existing?.id) { mutableStateOf(false) }
    val activeProjects = remember(projects) { projects.filterNot { it.isArchived } }
    val activeTasks = remember(tasks) { tasks.filterNot { it.isDone } }

    val canSave = name.isNotBlank() ||
        projectId != null ||
        description.isNotBlank() ||
        selectedTags.isNotEmpty()

    val save = {
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
    }

    ModalBottomSheet(
        // The delete confirmation is a separate dialog window; its focus change must not close the editor.
        onDismissRequest = { if (!confirmDelete) onDismiss() },
        modifier = Modifier.testTag(TemplateEditorTestTags.SHEET),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Dimens.RadiusXl, topEnd = Dimens.RadiusXl),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Only the drag handle may dismiss the sheet; swipes in the content just scroll it.
                .nestedScroll(ConsumeVerticalOverscroll)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = Dimens.Space24),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space16),
        ) {
            Box(Modifier.padding(horizontal = Dimens.Space8)) {
                EntrySheetHeader(
                    title = stringResource(
                        if (existing ==
                            null
                        ) {
                            R.string.template_editor_new_title
                        } else {
                            R.string.template_editor_edit_title
                        },
                    ),
                    onCancel = onDismiss,
                    onSave = save,
                    saveEnabled = canSave,
                    cancelTag = TemplateEditorTestTags.CANCEL,
                    saveTag = TemplateEditorTestTags.SAVE,
                )
            }

            GroupedSection(footer = stringResource(R.string.template_description_hint)) {
                BorderlessTextCell(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.template_name_label),
                    singleLine = true,
                    testTag = TemplateEditorTestTags.NAME,
                )
                GroupedDivider()
                BorderlessTextCell(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = stringResource(R.string.description),
                    singleLine = false,
                    testTag = TemplateEditorTestTags.DESCRIPTION,
                )
            }

            GroupedSection {
                ProjectTaskDropdown(
                    projects = activeProjects,
                    tasks = activeTasks,
                    selectedProjectId = projectId,
                    selectedTaskId = taskId,
                    onSelectionChanged = { newProjectId, newTaskId ->
                        projectId = newProjectId
                        taskId = newTaskId
                    },
                    showProjectColors = true,
                    style = SelectorStyle.Grouped,
                )
                GroupedDivider(inset = Dimens.SettingsIconInset)
                TagsSelector(
                    selectedTagIds = selectedTags,
                    availableTags = tags,
                    onTagsChanged = { selectedTags = it },
                    enabled = true,
                    style = SelectorStyle.Grouped,
                )
                GroupedDivider(inset = Dimens.SettingsIconInset)
                GroupedSwitchRow(
                    title = stringResource(R.string.billable),
                    leadingIcon = Icons.Outlined.AttachMoney,
                    checked = billable,
                    onCheckedChange = { billable = it },
                    modifier = Modifier.testTag(TemplateEditorTestTags.BILLABLE),
                )
            }

            GroupedSection(footer = stringResource(R.string.template_favorite_hint)) {
                GroupedSwitchRow(
                    title = stringResource(R.string.template_favorite_label),
                    leadingIcon = Icons.Outlined.StarOutline,
                    checked = favorite,
                    onCheckedChange = { favorite = it },
                    modifier = Modifier.testTag(TemplateEditorTestTags.FAVORITE),
                )
            }

            if (existing != null && onDelete != null) {
                DestructiveActionRow(
                    label = stringResource(R.string.templates_sweep_delete_template),
                    onClick = { confirmDelete = true },
                    testTag = TemplateEditorTestTags.DELETE,
                )
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        TemplateDeleteDialog(
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** A text value as a single borderless grouped cell, like the entry form's description. */
@Composable
private fun BorderlessTextCell(value: String, onValueChange: (String) -> Unit, placeholder: String, singleLine: Boolean, testTag: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics { contentDescription = placeholder },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** The destructive "Delete template?" confirmation used by the rows and the editor. */
@Composable
fun TemplateDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.templates_delete_confirm_title),
        message = stringResource(R.string.templates_delete_confirm_body),
        confirmLabel = stringResource(R.string.delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
        confirmTestTag = TemplateEditorTestTags.DELETE_CONFIRM,
    )
}

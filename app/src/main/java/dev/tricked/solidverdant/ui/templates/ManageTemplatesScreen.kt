/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.repository.EntryTemplate

/**
 * Manage favorites & templates (gap analysis #9). Create, edit, reorder, favorite and delete
 * reusable entry templates. Everything is Room-backed and works offline; archived/deleted catalogue
 * references are surfaced per row rather than silently substituted (gap analysis #81).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTemplatesScreen(onBack: () -> Unit = {}, viewModel: ManageTemplatesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<EntryTemplate?>(null) }
    var deletingTemplate by remember { mutableStateOf<EntryTemplate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_templates_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.review_navigate_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isLoading && !state.error && state.organizationId != null) {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.templates_add)) },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> LoadingState()
                state.error -> ErrorState(onRetry = viewModel::retry)
                state.templates.isEmpty() -> EmptyState(
                    canCreate = state.organizationId != null,
                    onCreate = { showCreate = true },
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("templates_list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val ordered = state.templates
                    items(ordered, key = { it.id }) { template ->
                        val index = ordered.indexOfFirst { it.id == template.id }
                        val canMoveUp = index > 0 && ordered[index - 1].isFavorite == template.isFavorite
                        val canMoveDown = index < ordered.lastIndex &&
                            ordered[index + 1].isFavorite == template.isFavorite
                        TemplateRow(
                            template = template,
                            resolution = TemplateResolver.resolve(
                                template,
                                state.projects,
                                state.tasks,
                                state.tags,
                            ),
                            projectTaskSummary = templateProjectTaskSummary(
                                template,
                                state.projects,
                                state.tasks,
                            ),
                            label = templateDisplayLabel(template, state.projects),
                            canMoveUp = canMoveUp,
                            canMoveDown = canMoveDown,
                            onToggleFavorite = { viewModel.setFavorite(template.id, !template.isFavorite) },
                            onMoveUp = { viewModel.moveTemplate(template.id, up = true) },
                            onMoveDown = { viewModel.moveTemplate(template.id, up = false) },
                            onEdit = { editingTemplate = template },
                            onDelete = { deletingTemplate = template },
                        )
                    }
                }
            }
        }
    }

    if (showCreate || editingTemplate != null) {
        TemplateEditorSheet(
            existing = editingTemplate,
            projects = state.projects,
            tasks = state.tasks,
            tags = state.tags,
            onDismiss = {
                showCreate = false
                editingTemplate = null
            },
            onSave = { draft ->
                viewModel.saveNewTemplate(draft)
                showCreate = false
            },
            onUpdate = { updated ->
                viewModel.updateTemplate(updated)
                editingTemplate = null
            },
        )
    }

    deletingTemplate?.let { template ->
        TemplateDeleteDialog(
            onConfirm = {
                viewModel.deleteTemplate(template.id)
                deletingTemplate = null
            },
            onDismiss = { deletingTemplate = null },
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.templates_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.templates_error_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.templates_error_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun EmptyState(canCreate: Boolean, onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.templates_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.templates_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canCreate) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.templates_empty_create),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun TemplateRow(
    template: EntryTemplate,
    resolution: TemplateResolution,
    projectTaskSummary: String?,
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleFavorite: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val secondary = projectTaskSummary
                        ?: if (template.description.isNullOrBlank() && template.projectId == null) {
                            stringResource(R.string.templates_details_none)
                        } else {
                            null
                        }
                    secondary?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val metaParts = buildList {
                        if (template.tagIds.isNotEmpty()) {
                            add(
                                pluralStringResource(
                                    R.plurals.templates_tag_count,
                                    template.tagIds.size,
                                    template.tagIds.size,
                                ),
                            )
                        }
                        if (template.billable) add(stringResource(R.string.billable))
                    }
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = stringResource(
                            if (template.isFavorite) {
                                R.string.templates_favorite_remove
                            } else {
                                R.string.templates_favorite_add
                            },
                        ),
                        tint = if (template.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.templates_row_details),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.templates_move_up)) },
                            enabled = canMoveUp,
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onMoveUp()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.templates_move_down)) },
                            enabled = canMoveDown,
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onMoveDown()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            if (resolution.hasIssues) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = stringResource(R.string.templates_unavailable),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    TemplateIssueList(resolution, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

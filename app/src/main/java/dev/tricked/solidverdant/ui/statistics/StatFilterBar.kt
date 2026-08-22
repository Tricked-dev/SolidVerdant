/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.theme.Dimens

internal object StatisticsFilterTestTags {
    const val OPEN = "stats_filter_open"
    const val PROJECT_SEARCH = "stats_project_filter_search"
    fun projectOption(id: String) = "stats_project_filter_$id"
    fun section(section: StatFilterSection) = "stats_filter_section_${section.name.lowercase()}"
}

internal enum class StatFilterSection { BILLABLE, TASKS, TAGS, CLIENTS, PROJECTS }

internal val statFilterSectionOrder = listOf(
    StatFilterSection.BILLABLE,
    StatFilterSection.TASKS,
    StatFilterSection.TAGS,
    StatFilterSection.CLIENTS,
    StatFilterSection.PROJECTS,
)

internal fun filterProjectOptions(options: List<Pair<String, String>>, query: String): List<Pair<String, String>> {
    val normalized = query.trim()
    return if (normalized.isEmpty()) {
        options
    } else {
        options.filter { (_, name) ->
            name.contains(normalized, ignoreCase = true)
        }
    }
}

/**
 * Persistent filter bar: a "Filters" button that opens the editing sheet, a legible summary of the
 * active scope, and a one-tap clear. Active constraints are echoed as removable chips so the current
 * scope is always visible (AGENTS: preserve user context, make state legible, offer easy clearing).
 */
@Composable
fun StatFilterBar(
    filters: StatFilters,
    catalog: StatCatalog,
    onFiltersChange: (StatFilters) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalButton(
                onClick = { showSheet = true },
                modifier = Modifier.heightIn(min = Dimens.MinTouchTarget).testTag(StatisticsFilterTestTags.OPEN),
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = stringResource(R.string.stats2_filters_content_description),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Dimens.Space8))
                Text(stringResource(R.string.stats2_filters))
            }
            Spacer(Modifier.weight(1f))
            if (filters.isActive) {
                TextButton(
                    onClick = onClearFilters,
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.stats2_clear_filters_content_description),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Dimens.Space4))
                    Text(stringResource(R.string.stats2_clear_filters))
                }
            }
        }
        Text(
            text = if (filters.isActive) {
                pluralStringResource(
                    R.plurals.stats2_active_filters,
                    filters.activeCount,
                    filters.activeCount,
                )
            } else {
                stringResource(R.string.stats2_active_scope_none)
            },
            style = if (filters.isActive) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
            color = if (filters.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        if (filters.isActive) {
            ActiveFilterChips(filters, catalog, onFiltersChange)
        }
    }
    if (showSheet) {
        StatFilterSheet(
            filters = filters,
            catalog = catalog,
            onFiltersChange = onFiltersChange,
            onReset = onClearFilters,
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilterChips(filters: StatFilters, catalog: StatCatalog, onFiltersChange: (StatFilters) -> Unit) {
    val projectName = catalog.projects.associate { it.id to it.name }
    val clientName = catalog.clients.associate { it.id to it.name }
    val taskName = catalog.tasks.associate { it.id to it.name }
    val tagName = catalog.tags.associate { it.id to it.name }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
        if (filters.billable != BillableFilter.All) {
            RemovableChip(billableLabel(filters.billable)) {
                onFiltersChange(filters.copy(billable = BillableFilter.All))
            }
        }
        filters.projectIds.forEach { id ->
            RemovableChip(projectName[id] ?: stringResource(R.string.stats2_no_project)) {
                onFiltersChange(filters.toggleProject(id))
            }
        }
        filters.clientIds.forEach { id ->
            RemovableChip(clientName[id] ?: id) { onFiltersChange(filters.toggleClient(id)) }
        }
        filters.taskIds.forEach { id ->
            RemovableChip(taskName[id] ?: id) { onFiltersChange(filters.toggleTask(id)) }
        }
        filters.tagIds.forEach { id ->
            RemovableChip(tagName[id] ?: id) { onFiltersChange(filters.toggleTag(id)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingIcon = {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StatFilterSheet(
    filters: StatFilters,
    catalog: StatCatalog,
    onFiltersChange: (StatFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedSection by rememberSaveable { mutableStateOf(StatFilterSection.BILLABLE) }
    var projectQuery by rememberSaveable { mutableStateOf("") }
    val filteredProjects = remember(catalog.projects, projectQuery) {
        val query = projectQuery.trim()
        if (query.isEmpty()) catalog.projects else catalog.projects.filter { it.name.contains(query, ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.Space16)
                .padding(bottom = Dimens.Space24),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.stats2_filter_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReset, enabled = filters.isActive) {
                    Text(stringResource(R.string.stats2_reset))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space4),
            ) {
                statFilterSectionOrder.forEach { section ->
                    FilterChip(
                        selected = selectedSection == section,
                        onClick = { selectedSection = section },
                        label = { Text(statFilterSectionLabel(section)) },
                        modifier = Modifier.testTag(StatisticsFilterTestTags.section(section)),
                    )
                }
            }

            when (selectedSection) {
                StatFilterSection.BILLABLE -> {
                    val billableOptions = listOf(
                        BillableFilter.All,
                        BillableFilter.Billable,
                        BillableFilter.NonBillable,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        billableOptions.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = filters.billable == option,
                                onClick = { onFiltersChange(filters.copy(billable = option)) },
                                shape = SegmentedButtonDefaults.itemShape(index, billableOptions.size),
                            ) { Text(billableLabel(option)) }
                        }
                    }
                }

                StatFilterSection.TASKS -> FilterSection(
                    emptyText = stringResource(R.string.stats2_filter_empty_tasks),
                    options = catalog.tasks.map { it.id to it.name },
                    selected = filters.taskIds,
                    onToggle = { onFiltersChange(filters.toggleTask(it)) },
                )

                StatFilterSection.TAGS -> FilterSection(
                    emptyText = stringResource(R.string.stats2_filter_empty_tags),
                    options = catalog.tags.map { it.id to it.name },
                    selected = filters.tagIds,
                    onToggle = { onFiltersChange(filters.toggleTag(it)) },
                )

                StatFilterSection.CLIENTS -> FilterSection(
                    emptyText = stringResource(R.string.stats2_filter_empty_clients),
                    options = catalog.clients.map { it.id to it.name },
                    selected = filters.clientIds,
                    onToggle = { onFiltersChange(filters.toggleClient(it)) },
                )

                StatFilterSection.PROJECTS -> {
                    OutlinedTextField(
                        value = projectQuery,
                        onValueChange = { projectQuery = it },
                        modifier = Modifier.fillMaxWidth().testTag(StatisticsFilterTestTags.PROJECT_SEARCH),
                        label = { Text(stringResource(R.string.stats2_search_projects)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (projectQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { projectQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                                }
                            }
                        } else {
                            null
                        },
                        singleLine = true,
                    )
                    val emptyProjectsText = if (projectQuery.isBlank()) {
                        R.string.stats2_filter_empty_projects
                    } else {
                        R.string.no_results_found
                    }
                    FilterSection(
                        emptyText = stringResource(emptyProjectsText),
                        options = filteredProjects.map { it.id to it.name },
                        selected = filters.projectIds,
                        onToggle = { onFiltersChange(filters.toggleProject(it)) },
                        optionTestTag = StatisticsFilterTestTags::projectOption,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FilterSection(
    emptyText: String,
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    optionTestTag: ((String) -> String)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
        if (options.isEmpty()) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
                options.forEach { (id, name) ->
                    FilterChip(
                        selected = id in selected,
                        onClick = { onToggle(id) },
                        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = optionTestTag?.let { Modifier.testTag(it(id)) } ?: Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun statFilterSectionLabel(section: StatFilterSection): String = stringResource(
    when (section) {
        StatFilterSection.BILLABLE -> R.string.stats2_filter_billable
        StatFilterSection.TASKS -> R.string.stats2_filter_tasks
        StatFilterSection.TAGS -> R.string.stats2_filter_tags
        StatFilterSection.CLIENTS -> R.string.stats2_filter_clients
        StatFilterSection.PROJECTS -> R.string.stats2_filter_projects
    },
)

@Composable
private fun billableLabel(filter: BillableFilter): String = when (filter) {
    BillableFilter.All -> stringResource(R.string.stats2_billable_all)
    BillableFilter.Billable -> stringResource(R.string.stats2_billable_billable)
    BillableFilter.NonBillable -> stringResource(R.string.stats2_billable_nonbillable)
}

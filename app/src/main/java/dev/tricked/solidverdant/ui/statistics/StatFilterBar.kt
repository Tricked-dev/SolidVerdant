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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R

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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalButton(
                onClick = { showSheet = true },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = stringResource(R.string.stats2_filters_content_description),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.stats2_filters))
            }
            if (filters.isActive) {
                Text(
                    pluralStringResource(
                        R.plurals.stats2_active_filters,
                        filters.activeCount,
                        filters.activeCount,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onClearFilters,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.stats2_clear_filters_content_description),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.stats2_clear_filters))
                }
            } else {
                Text(
                    stringResource(R.string.stats2_active_scope_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
private fun ActiveFilterChips(
    filters: StatFilters,
    catalog: StatCatalog,
    onFiltersChange: (StatFilters) -> Unit,
) {
    val projectName = catalog.projects.associate { it.id to it.name }
    val clientName = catalog.clients.associate { it.id to it.name }
    val taskName = catalog.tasks.associate { it.id to it.name }
    val tagName = catalog.tags.associate { it.id to it.name }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

            Text(
                stringResource(R.string.stats2_filter_billable),
                style = MaterialTheme.typography.titleSmall,
            )
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

            FilterSection(
                title = stringResource(R.string.stats2_filter_projects),
                emptyText = stringResource(R.string.stats2_filter_empty_projects),
                options = catalog.projects.map { it.id to it.name },
                selected = filters.projectIds,
                onToggle = { onFiltersChange(filters.toggleProject(it)) },
            )
            FilterSection(
                title = stringResource(R.string.stats2_filter_clients),
                emptyText = stringResource(R.string.stats2_filter_empty_clients),
                options = catalog.clients.map { it.id to it.name },
                selected = filters.clientIds,
                onToggle = { onFiltersChange(filters.toggleClient(it)) },
            )
            FilterSection(
                title = stringResource(R.string.stats2_filter_tasks),
                emptyText = stringResource(R.string.stats2_filter_empty_tasks),
                options = catalog.tasks.map { it.id to it.name },
                selected = filters.taskIds,
                onToggle = { onFiltersChange(filters.toggleTask(it)) },
            )
            FilterSection(
                title = stringResource(R.string.stats2_filter_tags),
                emptyText = stringResource(R.string.stats2_filter_empty_tags),
                options = catalog.tags.map { it.id to it.name },
                selected = filters.tagIds,
                onToggle = { onFiltersChange(filters.toggleTag(it)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FilterSection(
    title: String,
    emptyText: String,
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (options.isEmpty()) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (id, name) ->
                    FilterChip(
                        selected = id in selected,
                        onClick = { onToggle(id) },
                        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
    }
}

@Composable
private fun billableLabel(filter: BillableFilter): String = when (filter) {
    BillableFilter.All -> stringResource(R.string.stats2_billable_all)
    BillableFilter.Billable -> stringResource(R.string.stats2_billable_billable)
    BillableFilter.NonBillable -> stringResource(R.string.stats2_billable_nonbillable)
}

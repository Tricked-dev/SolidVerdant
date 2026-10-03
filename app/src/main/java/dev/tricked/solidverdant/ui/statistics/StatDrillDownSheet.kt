/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.components.ConsumeVerticalOverscroll
import dev.tricked.solidverdant.ui.components.EmptyState
import dev.tricked.solidverdant.ui.components.ErrorState
import dev.tricked.solidverdant.ui.components.GroupedPosition
import dev.tricked.solidverdant.ui.components.LazyGroupedRow
import dev.tricked.solidverdant.ui.components.LoadingState
import dev.tricked.solidverdant.ui.components.SheetTitleRow
import dev.tricked.solidverdant.ui.localization.appLocale
import dev.tricked.solidverdant.ui.theme.Dimens
import dev.tricked.solidverdant.ui.theme.tabular
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal object StatDrillDownTestTags {
    const val SHEET = "stats_drilldown_sheet"
    const val LIST = "stats_drilldown_list"
    const val LOAD_FAILED = "stats_drilldown_load_failed"
    const val OWN_ONLY = "stats_drilldown_own_only"
    const val REFRESHING = "stats_drilldown_refreshing"
    fun row(entryId: String) = "stats_drilldown_row_$entryId"
}

/**
 * Lists the entries behind a tapped bar, project or estimate row in the app's sheet style: the
 * page background, a bold title, then the entries as one grouped section. The section is a lazy
 * list that fills the fully expanded sheet, so a busy slice composes only its visible rows. While
 * entries are still arriving a spinner follows the rows; an unreachable server gets one Retry line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatDrillDownSheet(state: DrillDownUiState, onDismiss: () -> Unit, onRetry: () -> Unit = {}) {
    val locale = appLocale()
    // Rows from another year carry it; a project's history can span several.
    val dateFormatters = remember(locale) {
        listOf("EEEdMMM", "EEEdMMMy").map { skeleton ->
            DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        }
    }
    val currentYear = remember { LocalDate.now().year }
    val title = when (val target = state.target) {
        is DrillDownTarget.ProjectSlice -> target.projectName ?: stringResource(R.string.stats2_no_project)
        is DrillDownTarget.TrendSlice -> target.label
        is DrillDownTarget.OtherProjects ->
            pluralStringResource(R.plurals.stats_sweep_other_projects, target.projectIds.size, target.projectIds.size)
        is DrillDownTarget.ProjectHistory -> target.projectName
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(StatDrillDownTestTags.SHEET),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                // Only the drag handle may dismiss the sheet; swipes in the content just scroll it.
                .nestedScroll(ConsumeVerticalOverscroll)
                .navigationBarsPadding()
                .testTag(StatDrillDownTestTags.LIST),
            contentPadding = PaddingValues(bottom = Dimens.Space24),
        ) {
            item(key = "title") {
                Box(Modifier.padding(bottom = Dimens.Space16)) { SheetTitleRow(title = title) }
            }
            if (state.loadFailed) {
                item(key = "load_failed") {
                    ErrorState(
                        text = stringResource(R.string.stats2_drilldown_load_failed),
                        onRetry = onRetry,
                        modifier = Modifier.testTag(StatDrillDownTestTags.LOAD_FAILED),
                    )
                }
            }
            when {
                state.isLoading -> item(key = "loading") { LoadingState() }
                state.rows.isEmpty() && state.isRefreshing -> item(key = "refreshing") { LoadingState() }
                state.rows.isEmpty() -> {
                    if (!state.loadFailed) item(key = "empty") { EmptyState(text = stringResource(R.string.stats2_drilldown_empty)) }
                }
                else -> {
                    item(key = "summary") {
                        DrillDownSummary(count = state.rows.size, totalSeconds = state.totalSeconds, ownEntriesOnly = state.ownEntriesOnly)
                    }
                    itemsIndexed(state.rows, key = { _, row -> row.entryId }) { index, row ->
                        LazyGroupedRow(position = GroupedPosition.of(index, state.rows.size), dividerInset = Dimens.SettingsIconInset) {
                            DrillDownRowItem(row, dateFormatters[if (row.startDate.year == currentYear) 0 else 1])
                        }
                    }
                    if (state.isRefreshing) item(key = "refreshing") { LoadingState(Modifier.testTag(StatDrillDownTestTags.REFRESHING)) }
                }
            }
        }
    }
}

/**
 * "12 entries" and the total, set like a grouped section's header above the rows. When the account
 * may only see its own time, one line says so: the total then covers only that.
 */
@Composable
private fun DrillDownSummary(count: Int, totalSeconds: Long, ownEntriesOnly: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.Space32, end = Dimens.Space32, bottom = Dimens.Space8),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space4),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
            Text(
                pluralStringResource(R.plurals.stats2_drilldown_entries, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.stats2_drilldown_total, formatDuration(totalSeconds)),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ownEntriesOnly) {
            Text(
                stringResource(R.string.stats2_drilldown_own_only),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(StatDrillDownTestTags.OWN_ONLY),
            )
        }
    }
}

@Composable
private fun DrillDownRowItem(row: DrillDownRow, dateFormatter: DateTimeFormatter) {
    val noProject = stringResource(R.string.stats2_no_project)
    val meta = remember(row, dateFormatter, noProject) {
        listOfNotNull(row.memberName, row.projectName ?: noProject, row.taskName, row.startDate.format(dateFormatter)).joinToString(" · ")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTouchTarget)
            .testTag(StatDrillDownTestTags.row(row.entryId))
            .padding(horizontal = Dimens.Space16, vertical = Dimens.Space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
    ) {
        // The swatch sits in an icon-sized slot so the text and dividers line up with grouped rows.
        Box(Modifier.size(Dimens.IconSmall), contentAlignment = Alignment.Center) {
            ProjectSwatch(hexToColor(row.colorHex, MaterialTheme.colorScheme.outline))
        }
        Column(Modifier.weight(1f)) {
            val description = row.description?.takeIf { it.isNotBlank() }
            Text(
                text = description ?: stringResource(R.string.stats2_drilldown_no_description),
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = if (description == null) FontStyle.Italic else FontStyle.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatDuration(row.seconds),
                style = MaterialTheme.typography.bodyLarge.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.billable) {
                Text(
                    stringResource(R.string.stats2_billable_billable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

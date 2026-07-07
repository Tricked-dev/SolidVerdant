package dev.tricked.solidverdant.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import java.time.format.DateTimeFormatter

private val drillDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * Lists the individual entries behind a tapped chart slice. Uses a [LazyColumn] with a bounded
 * height so a busy slice does not eagerly compose hundreds of rows, and surfaces loading and empty
 * states rather than a blank sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatDrillDownSheet(
    state: DrillDownUiState,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        val title = when (val t = state.target) {
            is DrillDownTarget.ProjectSlice -> t.projectName ?: stringResource(R.string.stats2_no_project)
            is DrillDownTarget.TrendSlice -> t.label
        }
        val accentColor = when (val t = state.target) {
            is DrillDownTarget.ProjectSlice -> hexToColor(t.colorHex)
            is DrillDownTarget.TrendSlice -> MaterialTheme.colorScheme.primary
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.rows.isEmpty() -> {
                    Text(
                        stringResource(R.string.stats2_drilldown_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.stats2_drilldown_entries,
                                state.rows.size,
                                state.rows.size,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.stats2_drilldown_total, formatDuration(state.totalSeconds)),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp),
                    ) {
                        items(state.rows, key = { it.entryId }) { row ->
                            DrillDownRowItem(row)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrillDownRowItem(row: DrillDownRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(hexToColor(row.colorHex)),
        )
        Column(Modifier.weight(1f)) {
            val description = row.description?.takeIf { it.isNotBlank() }
            Text(
                text = description ?: stringResource(R.string.stats2_drilldown_no_description),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = if (description == null) FontStyle.Italic else FontStyle.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val noProjectLabel = stringResource(R.string.stats2_no_project)
            val meta = buildList {
                add(row.projectName ?: noProjectLabel)
                row.taskName?.let { add(it) }
                add(row.startDate.format(drillDateFmt))
            }.joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatDuration(row.seconds), style = MaterialTheme.typography.bodyMedium)
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

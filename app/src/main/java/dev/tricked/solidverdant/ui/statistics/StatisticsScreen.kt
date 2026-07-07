package dev.tricked.solidverdant.ui.statistics

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.tricked.solidverdant.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tricked.solidverdant.ui.statistics.charts.DonutChart
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun hexToColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (t: Throwable) {
    Color(0xFF9E9E9E)
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0m"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m"
        else -> "${m}m"
    }
}

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val drillDown by viewModel.drillDown.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val chooserTitle = stringResource(R.string.stats2_export_chooser_title)
    val emptyMsg = stringResource(R.string.stats2_export_empty)
    val errorMsg = stringResource(R.string.stats2_export_error)

    LaunchedEffect(exportState) {
        when (val es = exportState) {
            is ExportState.Ready -> {
                shareCsv(context, es.uri, chooserTitle)
                viewModel.onExportHandled()
            }
            ExportState.Empty -> {
                snackbarHostState.showSnackbar(emptyMsg)
                viewModel.onExportHandled()
            }
            ExportState.Error -> {
                snackbarHostState.showSnackbar(errorMsg)
                viewModel.onExportHandled()
            }
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RangeChips(state.range, viewModel::setRange)

                StatFilterBar(
                    filters = state.filters,
                    catalog = state.catalog,
                    onFiltersChange = viewModel::setFilters,
                    onClearFilters = viewModel::clearFilters,
                )

                ExportButton(
                    exporting = exportState is ExportState.Running,
                    onExport = viewModel::export,
                )

                if (state.isRefreshing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (state.refreshFailed) {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.stats_cached_refresh_failed),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.retry)) }
                        }
                    }
                }

                if (state.isEmpty) {
                    Text(
                        stringResource(R.string.stats_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                    // A previous period with data is still worth showing even when the current
                    // (possibly over-filtered) range is empty.
                    state.comparison?.let { if (it.previousHasData) StatComparisonCard(it) }
                } else {
                    val s = state.summary
                    KpiGrid(s)

                    state.comparison?.let { StatComparisonCard(it) }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.stats_by_project), style = MaterialTheme.typography.titleMedium)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                DonutChart(
                                    slices = s.perProject.map { hexToColor(it.colorHex) to it.seconds.toFloat() },
                                    modifier = Modifier.size(140.dp),
                                )
                                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                    s.perProject.forEach { p ->
                                        val pct = if (s.totalSeconds > 0) p.seconds * 100 / s.totalSeconds else 0
                                        ProjectLegendRow(
                                            projectName = p.projectName,
                                            colorHex = p.colorHex,
                                            valueText = "${formatDuration(p.seconds)} ($pct%)",
                                            onClick = {
                                                viewModel.openProjectDrillDown(p.projectId, p.projectName, p.colorHex)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.stats_trend), style = MaterialTheme.typography.titleMedium)
                            InteractiveBarChart(
                                bars = s.trend,
                                barColor = MaterialTheme.colorScheme.primary,
                                onBarClick = { bucket ->
                                    val end = when (state.granularity) {
                                        TrendGranularity.DAY -> bucket.startDate
                                        TrendGranularity.WEEK -> bucket.startDate.plusDays(6)
                                    }
                                    viewModel.openTrendDrillDown(bucket.label, bucket.startDate, end)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    drillDown?.let { dd ->
        StatDrillDownSheet(state = dd, onDismiss = viewModel::closeDrillDown)
    }
}

@Composable
private fun ExportButton(exporting: Boolean, onExport: () -> Unit) {
    FilledTonalButton(
        onClick = onExport,
        enabled = !exporting,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        if (exporting) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Default.Share,
                contentDescription = stringResource(R.string.stats2_export_content_description),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(stringResource(if (exporting) R.string.stats2_exporting else R.string.stats2_export))
    }
}

@Composable
private fun ProjectLegendRow(
    projectName: String,
    colorHex: String,
    valueText: String,
    onClick: () -> Unit,
) {
    val cd = stringResource(R.string.stats2_drilldown_project_content_description, projectName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$cd, $valueText" }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(hexToColor(colorHex)),
        )
        Text(
            "$projectName — $valueText",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RangeChips(current: StatRange, onSelect: (StatRange) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val options = listOf(
        R.string.today to StatRange.Today,
        R.string.yesterday to StatRange.Yesterday,
        R.string.stats_last_7_days to StatRange.Last7Days,
        R.string.stats_last_week to StatRange.LastWeek,
        R.string.stats_this_week to StatRange.ThisWeek,
        R.string.stats_this_month to StatRange.ThisMonth,
        R.string.stats_previous_month to StatRange.PreviousMonth,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, range) ->
            FilterChip(selected = current == range, onClick = { onSelect(range) }, label = { Text(stringResource(label)) })
        }
        FilterChip(
            selected = current is StatRange.Custom,
            onClick = { showPicker = true },
            label = {
                Text(if (current is StatRange.Custom) {
                    "${current.start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${current.end.format(DateTimeFormatter.ofPattern("d MMM"))}"
                } else stringResource(R.string.stats_custom))
            },
        )
    }
    if (showPicker) {
        val selected = current as? StatRange.Custom
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = selected?.start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = selected?.end?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis?.toUtcDate()
                        val end = pickerState.selectedEndDateMillis?.toUtcDate()
                        if (start != null && end != null) onSelect(StatRange.Custom(start, end))
                        showPicker = false
                    },
                ) { Text(stringResource(R.string.apply)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DateRangePicker(state = pickerState) }
    }
}

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun KpiGrid(s: StatisticsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile(stringResource(R.string.stats_total), formatDuration(s.totalSeconds), Modifier.weight(1f))
            KpiTile(stringResource(R.string.stats_entries), s.entryCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile(stringResource(R.string.stats_average_day), formatDuration(s.avgSecondsPerDay), Modifier.weight(1f))
            KpiTile(stringResource(R.string.billable), formatDuration(s.billableSeconds), Modifier.weight(1f))
        }
    }
}

@Composable
private fun KpiTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Shares the exported CSV through the Android system share sheet. Grants temporary read access to the
 * receiving app for the FileProvider content URI; the file itself only ever holds the user's own work
 * data, never tokens or credentials.
 */
private fun shareCsv(context: Context, uri: Uri, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, chooserTitle).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(chooser) }
}

package dev.tricked.solidverdant.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.tricked.solidverdant.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tricked.solidverdant.ui.statistics.charts.BarChart
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

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RangeChips(state.range, viewModel::setRange)

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
            return@Column
        }

        val s = state.summary
        KpiGrid(s)

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
                            Text(
                                "${p.projectName} — ${formatDuration(p.seconds)} ($pct%)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.stats_trend), style = MaterialTheme.typography.titleMedium)
                BarChart(
                    bars = s.trend.map { it.label to it.seconds.toFloat() },
                    barColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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

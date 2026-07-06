package dev.tricked.solidverdant.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tricked.solidverdant.ui.statistics.charts.BarChart
import dev.tricked.solidverdant.ui.statistics.charts.DonutChart

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

        if (state.isEmpty) {
            Text(
                "No tracked time in this range.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 32.dp),
            )
            return@Column
        }

        val s = state.summary
        KpiGrid(s)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("By project", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        slices = s.perProject.map { hexToColor(it.colorHex) to it.seconds.toFloat() },
                        modifier = Modifier.size(140.dp),
                    )
                    Column(Modifier.padding(start = 16.dp)) {
                        s.perProject.take(6).forEach { p ->
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
                Text("Trend", style = MaterialTheme.typography.titleMedium)
                BarChart(
                    bars = s.trend.map { it.label to it.seconds.toFloat() },
                    barColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RangeChips(current: StatRange, onSelect: (StatRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = current is StatRange.ThisWeek,
            onClick = { onSelect(StatRange.ThisWeek) },
            label = { Text("This week") },
        )
        FilterChip(
            selected = current is StatRange.ThisMonth,
            onClick = { onSelect(StatRange.ThisMonth) },
            label = { Text("This month") },
        )
        // Custom range: wire a Material DateRangePicker dialog here; on confirm call
        // onSelect(StatRange.Custom(start, end)). Left as a follow-up chip.
    }
}

@Composable
private fun KpiGrid(s: StatisticsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile("Total", formatDuration(s.totalSeconds), Modifier.weight(1f))
            KpiTile("Entries", s.entryCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile("Avg / day", formatDuration(s.avgSecondsPerDay), Modifier.weight(1f))
            KpiTile("Billable", formatDuration(s.billableSeconds), Modifier.weight(1f))
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

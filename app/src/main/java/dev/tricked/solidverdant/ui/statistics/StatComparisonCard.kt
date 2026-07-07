package dev.tricked.solidverdant.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.components.DeltaBadge
import dev.tricked.solidverdant.ui.components.SectionCard
import dev.tricked.solidverdant.ui.theme.Dimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

private val comparisonDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/** Tabular figures so stacked metric values and deltas keep their digits aligned. */
private val tabularBodyMedium
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")

/** Signed duration such as "+2h 05m", "-30m" or "0m" for a zero delta. */
fun formatSignedDuration(deltaSeconds: Long): String = when {
    deltaSeconds > 0 -> "+" + formatDuration(deltaSeconds)
    deltaSeconds < 0 -> "-" + formatDuration(abs(deltaSeconds))
    else -> formatDuration(0)
}

/** Signed integer percent such as "+25" for a [MetricDelta], or null when there is no baseline. */
fun signedPercentValue(delta: MetricDelta): Long? =
    delta.percentChange()?.roundToLong()

@Composable
fun StatComparisonCard(comparison: PeriodComparison, modifier: Modifier = Modifier) {
    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.stats2_comparison_title),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
    ) {
        Text(
            stringResource(
                R.string.stats2_comparison_range,
                comparison.previousStart.formatComparison(),
                comparison.previousEnd.formatComparison(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!comparison.previousHasData) {
            Text(
                stringResource(R.string.stats2_comparison_no_data),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        MetricDeltaRow(stringResource(R.string.stats2_metric_total), comparison.total)
        MetricDeltaRow(stringResource(R.string.stats2_metric_billable), comparison.billable)
        MetricDeltaRow(stringResource(R.string.stats2_metric_avg_per_day), comparison.avgPerDay)

        if (comparison.topChanges.isNotEmpty()) {
            Text(
                stringResource(R.string.stats2_top_changes),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Dimens.Space4),
            )
            comparison.topChanges.forEach { change ->
                ProjectChangeRow(change)
            }
        }
    }
}

@Composable
private fun MetricDeltaRow(label: String, delta: MetricDelta) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            formatDuration(delta.current),
            style = tabularBodyMedium,
            textAlign = TextAlign.End,
        )
        MetricDeltaBadge(delta)
    }
}

@Composable
private fun MetricDeltaBadge(delta: MetricDelta) {
    val sign = delta.absoluteDelta
    val durationText = formatSignedDuration(sign)
    val percent = signedPercentValue(delta)
    val percentText = when {
        percent == null -> stringResource(R.string.stats2_delta_new)
        else -> {
            val signed = if (percent > 0) "+$percent" else percent.toString()
            stringResource(R.string.stats2_delta_percent, signed)
        }
    }
    val cd = when {
        sign > 0 -> stringResource(R.string.stats2_delta_increase_content_description, formatDuration(sign))
        sign < 0 -> stringResource(R.string.stats2_delta_decrease_content_description, formatDuration(abs(sign)))
        else -> stringResource(R.string.stats2_delta_flat_content_description)
    }
    DeltaBadge(
        sign = sign.compareTo(0L),
        text = "$durationText · $percentText",
        modifier = Modifier.clearAndSetSemantics { contentDescription = cd },
    )
}

@Composable
private fun ProjectChangeRow(change: ProjectChange) {
    val swatchFallback = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
    ) {
        ProjectSwatch(hexToColor(change.colorHex, swatchFallback))
        Text(
            text = change.projectName ?: stringResource(R.string.stats2_no_project),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        DeltaBadge(
            sign = change.delta.compareTo(0L),
            text = formatSignedDuration(change.delta),
        )
    }
}

private fun LocalDate.formatComparison(): String = format(comparisonDateFmt)

package dev.tricked.solidverdant.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.theme.Dimens

/** Plotted height of the bars, matched to the static [charts.BarChart] so the two read identically. */
private val BarChartHeight = 140.dp

/**
 * The tappable sibling of [charts.BarChart]: it keeps that chart's proportions — a full [BarChartHeight]
 * plot with bars that fill their column and share one [Dimens.CornerRadius] on top — and adds only an
 * interaction layer. Each bar is a focusable button with a spoken label ("<bucket>: <duration>") so the
 * drill-down works with TalkBack, where per-pixel Canvas taps would not. Bars keep at least a finger
 * width and the row scrolls horizontally when a long range produces more bars than fit.
 */
@Composable
fun InteractiveBarChart(
    bars: List<TrendBucket>,
    barColor: Color,
    onBarClick: (TrendBucket) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) return
    val max = bars.maxOf { it.seconds }.coerceAtLeast(1L)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Give each bar a full finger-width touch target, but let them shrink to fill the width when
        // there are only a few so short ranges are not needlessly cramped into a scroll.
        val fitWidth = maxWidth / bars.size
        val barWidth = if (fitWidth >= Dimens.MinTouchTarget) fitWidth else Dimens.MinTouchTarget
        val scroll = rememberScrollState()
        Row(
            modifier = Modifier.horizontalScroll(scroll),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { bucket ->
                val fraction = (bucket.seconds.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                val label = stringResource(
                    R.string.stats2_drilldown_bucket_content_description,
                    "${bucket.label}: ${formatDuration(bucket.seconds)}",
                )
                Column(
                    modifier = Modifier
                        .width(barWidth)
                        .clip(RoundedCornerShape(Dimens.CornerRadius))
                        .clickable(role = Role.Button) { onBarClick(bucket) }
                        .semantics(mergeDescendants = true) { contentDescription = label }
                        .padding(horizontal = Dimens.Space4, vertical = Dimens.Space4),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .height(BarChartHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = Dimens.CornerRadius, topEnd = Dimens.CornerRadius))
                                .background(barColor),
                        )
                    }
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.Space4),
                    )
                }
            }
        }
    }
}

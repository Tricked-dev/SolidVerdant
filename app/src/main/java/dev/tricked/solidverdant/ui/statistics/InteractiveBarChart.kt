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

/**
 * A trend chart whose bars are individually tappable so a segment drills into its entries, instead
 * of a decorative Canvas. Each bar is a focusable button with a spoken label ("<bucket>: <duration>")
 * so the interaction works with TalkBack, where per-pixel Canvas taps would not. Bars keep at least
 * a finger-width and the row scrolls horizontally when a long range produces more bars than fit.
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
    val barHeight = 132.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Give each bar a comfortable touch width, but let them shrink to fit when there are only a
        // few so short ranges are not needlessly cramped into a scroll.
        val minBarWidth = 44.dp
        val fitWidth = maxWidth / bars.size
        val barWidth = if (fitWidth >= minBarWidth) fitWidth else minBarWidth
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
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { onBarClick(bucket) }
                        .semantics(mergeDescendants = true) { contentDescription = label }
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .height(barHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(barColor),
                        )
                    }
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

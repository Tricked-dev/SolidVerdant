/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val GAP_FRACTION = 0.02f
private const val MAX_GAP_FRACTION = 0.5f
private const val MIN_NON_ZERO_VALUE = 0.0001f

/**
 * Computes gap and bar width for [count] bars across [width] pixels.
 *
 * The naive `barWidth = (width - gap * (count + 1)) / count` goes negative once the inter-bar
 * gaps exceed the available width (many bars, e.g. WEEK granularity over a multi-year custom
 * range), which renders empty or inverted bars. We cap the total gap budget to half the width so
 * bars always keep at least half the canvas, and floor the bar width at a visible minimum. This is
 * a pure function so it can be unit tested without a Compose canvas.
 */
fun barChartSizing(width: Float, count: Int, minBarWidth: Float = 1f): BarChartSizing {
    if (count <= 0 || width <= 0f) return BarChartSizing(0f, 0f)
    // Gaps get at most half the width; the desired 2% shrinks as bars multiply.
    val gap = (width * GAP_FRACTION).coerceAtMost(width * MAX_GAP_FRACTION / (count + 1))
    val barWidth = ((width - gap * (count + 1)) / count).coerceAtLeast(minBarWidth)
    return BarChartSizing(gap = gap, barWidth = barWidth)
}

@Composable
fun BarChart(bars: List<Pair<String, Float>>, barColor: Color, modifier: Modifier = Modifier) {
    val max = bars.maxOfOrNull { it.second }?.coerceAtLeast(MIN_NON_ZERO_VALUE) ?: MIN_NON_ZERO_VALUE
    val summary = bars.joinToString(", ") { (label, seconds) -> "$label: ${seconds.toLong()} seconds" }
    Column(modifier = modifier.semantics { contentDescription = summary }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            if (bars.isEmpty()) return@Canvas
            val sizing = barChartSizing(size.width, bars.size)
            bars.forEachIndexed { i, (_, value) ->
                val h = (value / max) * size.height
                val left = sizing.gap + i * (sizing.barWidth + sizing.gap)
                drawRect(
                    color = barColor,
                    topLeft = androidx.compose.ui.geometry.Offset(left, size.height - h),
                    size = androidx.compose.ui.geometry.Size(sizing.barWidth, h),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            bars.forEach { (label, _) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp),
                )
            }
        }
    }
}

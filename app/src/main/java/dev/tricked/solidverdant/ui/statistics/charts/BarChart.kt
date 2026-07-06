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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun BarChart(
    bars: List<Pair<String, Float>>,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val max = bars.maxOfOrNull { it.second }?.coerceAtLeast(0.0001f) ?: 0.0001f
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            if (bars.isEmpty()) return@Canvas
            val gap = size.width * 0.02f
            val barWidth = (size.width - gap * (bars.size + 1)) / bars.size
            bars.forEachIndexed { i, (_, value) ->
                val h = (value / max) * size.height
                val left = gap + i * (barWidth + gap)
                drawRect(
                    color = barColor,
                    topLeft = androidx.compose.ui.geometry.Offset(left, size.height - h),
                    size = androidx.compose.ui.geometry.Size(barWidth, h),
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

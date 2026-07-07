package dev.tricked.solidverdant.ui.statistics.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics

/** Fallback diameter when a caller supplies no size; real callers pass their own via [modifier]. */
private val DefaultDonutSize = 140.dp

@Composable
fun DonutChart(
    slices: List<Pair<Color, Float>>,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    // Respect the caller's sizing (they place the chart in the layout); only guarantee a sensible
    // minimum so an unsized caller still renders a visible ring instead of a zero-size canvas.
    Canvas(
        modifier = modifier
            .defaultMinSize(minWidth = DefaultDonutSize, minHeight = DefaultDonutSize)
            .semantics { invisibleToUser() },
    ) {
        val stroke = Stroke(width = size.minDimension * 0.18f)
        val diameter = size.minDimension - stroke.width
        val topLeft = androidx.compose.ui.geometry.Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f,
        )
        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
        var startAngle = -90f
        slices.forEach { (color, value) ->
            val sweep = value / total * 360f
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            startAngle += sweep
        }
    }
}

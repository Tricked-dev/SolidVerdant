/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private const val MIN_NON_ZERO_TOTAL = 0.0001f
private const val START_ANGLE_DEGREES = -90f
private const val FULL_CIRCLE_DEGREES = 360f

/** Fallback diameter when a caller supplies no size; real callers pass their own via [modifier]. */
private val DefaultDonutSize = 140.dp

@Composable
fun DonutChart(slices: List<Pair<Color, Float>>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(MIN_NON_ZERO_TOTAL)
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
        var startAngle = START_ANGLE_DEGREES
        slices.forEach { (color, value) ->
            val sweep = value / total * FULL_CIRCLE_DEGREES
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

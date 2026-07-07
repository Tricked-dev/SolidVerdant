/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.ui.theme.SolidVerdantTheme
import dev.tricked.solidverdant.ui.theme.negative
import dev.tricked.solidverdant.ui.theme.neutral
import dev.tricked.solidverdant.ui.theme.positive

/** Direction of a delta, driving the [DeltaBadge] colour and arrow. */
enum class DeltaDirection { UP, DOWN, NEUTRAL }

/**
 * Up/down/neutral delta indicator. Renders an arrow icon plus a preformatted
 * [text] (e.g. "+2h 05m . +25%"), coloured via the semantic
 * positive/negative/neutral tokens and rendered with tabular figures so digits
 * stay aligned.
 */
@Composable
fun DeltaBadge(direction: DeltaDirection, text: String, modifier: Modifier = Modifier) {
    val color = when (direction) {
        DeltaDirection.UP -> MaterialTheme.colorScheme.positive
        DeltaDirection.DOWN -> MaterialTheme.colorScheme.negative
        DeltaDirection.NEUTRAL -> MaterialTheme.colorScheme.neutral
    }
    val icon: ImageVector = when (direction) {
        DeltaDirection.UP -> Icons.Filled.ArrowUpward
        DeltaDirection.DOWN -> Icons.Filled.ArrowDownward
        DeltaDirection.NEUTRAL -> Icons.Filled.Remove
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Arrow direction already conveys the meaning; the text is the label.
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = color,
        )
    }
}

/**
 * Convenience overload that derives the [DeltaDirection] from a numeric [sign]
 * (positive -> UP, negative -> DOWN, zero -> NEUTRAL).
 */
@Composable
fun DeltaBadge(sign: Int, text: String, modifier: Modifier = Modifier) {
    val direction = when {
        sign > 0 -> DeltaDirection.UP
        sign < 0 -> DeltaDirection.DOWN
        else -> DeltaDirection.NEUTRAL
    }
    DeltaBadge(direction = direction, text = text, modifier = modifier)
}

@Preview
@Composable
private fun DeltaBadgePreview() {
    SolidVerdantTheme {
        DeltaBadge(direction = DeltaDirection.UP, text = "+2h 05m · +25%")
    }
}

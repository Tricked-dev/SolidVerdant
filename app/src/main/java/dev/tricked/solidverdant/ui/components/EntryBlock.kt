/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.theme.Dimens
import dev.tricked.solidverdant.ui.theme.SolidVerdantTheme

/**
 * A single calendar entry block, adopting the month/day calendar treatment:
 * a tinted background derived from the entry [color] (22% alpha), a solid
 * coloured leading side bar, onSurface text and [MaterialTheme.shapes.small]
 * corners. Use this everywhere an entry is rendered so the per-view block
 * renderers stay consistent.
 *
 * @param color    entry accent colour (e.g. its project colour).
 * @param title    entry title; null/blank falls back to the shared
 *                 "Untitled entry" string.
 * @param subtitle optional secondary line (e.g. "Project - Task"), onSurfaceVariant.
 * @param time     optional trailing text (e.g. formatted duration or time range).
 * @param minHeight minimum block height; defaults to [Dimens.EntryMinHeight].
 */
@Composable
fun EntryBlock(
    color: Color,
    title: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    time: String? = null,
    minHeight: Dp = Dimens.EntryMinHeight,
) {
    val resolvedTitle = title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.uikit_untitled_entry)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.22f))
            .padding(
                horizontal = Dimens.EntryPaddingHorizontal,
                vertical = Dimens.EntryPaddingVertical,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(
            Modifier
                .width(Dimens.EntryBarWidth)
                .height(Dimens.EntryBarHeight)
                .background(color),
        )
        Spacer(Modifier.width(Dimens.EntryBarGap))
        Column(Modifier.weight(1f)) {
            Text(
                text = resolvedTitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!time.isNullOrBlank()) {
            Spacer(Modifier.width(Dimens.EntryBarGap))
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview
@Composable
fun EntryBlockPreview() {
    SolidVerdantTheme {
        EntryBlock(
            color = MaterialTheme.colorScheme.primary,
            title = "Design review",
            subtitle = "Acme - UI kit",
            time = "1h 05m",
        )
    }
}

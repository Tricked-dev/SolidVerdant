package dev.tricked.solidverdant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.tricked.solidverdant.ui.theme.Dimens
import dev.tricked.solidverdant.ui.theme.SolidVerdantTheme

/**
 * The canonical content Card used across screens. Matches the KPI / donut /
 * trend cards: a default M3 [Card] (default elevation + shape) wrapping a
 * [Column] with 16.dp content padding and 8.dp inner vertical gaps. When
 * [title] is non-null it is rendered as a titleMedium header above the content.
 *
 * Wrap screen content and control groups in this instead of a bare Card so
 * padding, spacing and shape stay consistent.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Dimens.CardContentGap),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.CardContentPadding),
            verticalArrangement = verticalArrangement,
        ) {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Preview
@Composable
private fun SectionCardPreview() {
    SolidVerdantTheme {
        SectionCard(title = "By project") {
            Text("Content row A", style = MaterialTheme.typography.bodyMedium)
            Text("Content row B", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

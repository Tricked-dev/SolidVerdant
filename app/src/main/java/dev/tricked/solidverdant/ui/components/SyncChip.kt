package dev.tricked.solidverdant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.repository.TimeEntryRepository.EntrySyncStatus
import dev.tricked.solidverdant.ui.theme.Dimens
import dev.tricked.solidverdant.ui.theme.SolidVerdantTheme
import dev.tricked.solidverdant.ui.theme.syncFailed
import dev.tricked.solidverdant.ui.theme.syncPending

/**
 * Compact per-entry sync-state indicator for PENDING / RETRYING / FAILED only.
 * For a healthy [EntrySyncStatus.SYNCED] state it renders nothing, so callers
 * can unconditionally call `SyncChip(status)` and get an invisible result when
 * the entry is healthy. Uses the tertiary (pending/retrying) and error (failed)
 * semantic families — never the primary accent.
 *
 * @param showLabel when true, shows a short text label next to the icon.
 */
@Composable
fun SyncChip(
    status: EntrySyncStatus,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val icon: ImageVector
    val color: Color
    val labelRes: Int
    when (status) {
        EntrySyncStatus.SYNCED -> return
        EntrySyncStatus.PENDING -> {
            icon = Icons.Filled.Schedule
            color = MaterialTheme.colorScheme.syncPending
            labelRes = R.string.sync_pending
        }
        EntrySyncStatus.RETRYING -> {
            icon = Icons.Filled.Sync
            color = MaterialTheme.colorScheme.syncPending
            labelRes = R.string.sync_retrying
        }
        EntrySyncStatus.FAILED -> {
            icon = Icons.Filled.SyncProblem
            color = MaterialTheme.colorScheme.syncFailed
            labelRes = R.string.sync_failed
        }
    }
    val label = stringResource(labelRes)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space4),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        if (showLabel) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Preview
@Composable
private fun SyncChipPreview() {
    SolidVerdantTheme {
        SyncChip(status = EntrySyncStatus.FAILED)
    }
}

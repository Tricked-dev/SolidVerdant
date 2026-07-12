/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.repository.TimeEntryRepository.SyncOperation
import dev.tricked.solidverdant.ui.components.SectionCard
import dev.tricked.solidverdant.ui.theme.Dimens
import dev.tricked.solidverdant.util.RelativeTime
import java.text.DateFormat
import java.util.Date

/**
 * Dedicated Sync Center screen (#33): shows sync freshness (last pull / last push), a plain-language
 * status summary, the changes waiting to sync, and any failures with per-item Retry / Discard plus a
 * Retry All action. All state is communicated with text + icon (never colour alone) for a11y. It is a
 * read/display + existing-action surface; it does not implement any sync control flow itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncCenterScreen(onBack: () -> Unit, viewModel: SyncCenterViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_center_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.sync_center_navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.Space16),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
        ) {
            Spacer(Modifier.height(Dimens.Space4))
            FreshnessSection(state = state, onSyncNow = viewModel::syncNow, nowMs = viewModel.nowMs())
            StatusSummarySection(state = state)
            if (state.pending.isNotEmpty()) {
                PendingSection(state.pending)
            }
            if (state.conflicts.isNotEmpty()) {
                ConflictsSection(state.conflicts)
            }
            if (state.failed.isNotEmpty()) {
                FailuresSection(
                    failed = state.failed,
                    onRetry = viewModel::retry,
                    onDiscard = viewModel::discard,
                    onRetryAll = viewModel::retryAll,
                )
            }
            if (state.topLine == SyncCenterUiState.TopLine.SYNCED) {
                Text(
                    text = stringResource(R.string.sync_all_caught_up),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Dimens.Space8),
                )
            }
            Spacer(Modifier.height(Dimens.Space16))
        }
    }
}

@Composable
private fun FreshnessSection(state: SyncCenterUiState, onSyncNow: () -> Unit, nowMs: Long) {
    SectionCard(title = stringResource(R.string.sync_freshness_title)) {
        FreshnessRow(
            label = stringResource(R.string.sync_last_pulled_label),
            timeMs = state.lastFullSyncAtMs,
            neverRes = R.string.sync_never_pulled,
            nowMs = nowMs,
        )
        FreshnessRow(
            label = stringResource(R.string.sync_last_pushed_label),
            timeMs = state.lastPushAtMs,
            neverRes = R.string.sync_never_pushed,
            nowMs = nowMs,
        )
        Button(onClick = onSyncNow, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(Dimens.Space8))
            Text(stringResource(R.string.sync_now))
        }
    }
}

@Composable
private fun FreshnessRow(label: String, timeMs: Long?, neverRes: Int, nowMs: Long) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = relativeTimeText(timeMs, neverRes, nowMs),
            style = MaterialTheme.typography.bodyLarge,
        )
        timeMs?.let { absolute ->
            Text(
                text = rememberAbsoluteTime(absolute),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusSummarySection(state: SyncCenterUiState) {
    val (icon, text, iconDescRes) = when (state.topLine) {
        SyncCenterUiState.TopLine.SYNCED -> Triple(
            Icons.Filled.CheckCircle,
            stringResource(R.string.sync_status_synced),
            R.string.sync_status_icon_synced,
        )
        SyncCenterUiState.TopLine.PENDING -> Triple(
            Icons.Filled.CloudUpload,
            stringResource(R.string.sync_status_pending, state.pendingCount),
            R.string.sync_status_icon_warning,
        )
        SyncCenterUiState.TopLine.FAILURES -> Triple(
            Icons.Filled.SyncProblem,
            stringResource(R.string.sync_status_failures, state.failedCount),
            R.string.sync_status_icon_warning,
        )
        SyncCenterUiState.TopLine.CONFLICTS -> Triple(
            Icons.Filled.SyncProblem,
            stringResource(R.string.sync_status_conflicts, state.conflictCount),
            R.string.sync_status_icon_warning,
        )
    }
    val iconDesc = stringResource(iconDescRes)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space12)) {
            Icon(icon, contentDescription = iconDesc, modifier = Modifier.size(28.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PendingSection(pending: List<SyncOperation>) {
    SectionCard(title = stringResource(R.string.sync_pending_section_title)) {
        pending.forEachIndexed { index, op ->
            if (index > 0) HorizontalDivider()
            Column {
                Text(opLabel(op.type), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.sync_pending_item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConflictsSection(conflicts: List<SyncOperation>) {
    SectionCard(title = stringResource(R.string.sync_conflicts_section_title)) {
        conflicts.forEachIndexed { index, op ->
            if (index > 0) HorizontalDivider()
            Column {
                Text(opLabel(op.type), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.sync_conflict_item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FailuresSection(failed: List<SyncOperation>, onRetry: (String) -> Unit, onDiscard: (String) -> Unit, onRetryAll: () -> Unit) {
    SectionCard(title = stringResource(R.string.sync_failures_section_title)) {
        failed.forEachIndexed { index, op ->
            if (index > 0) HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
                Text(opLabel(op.type), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(failureReasonRes(op.error)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
                    TextButton(onClick = { onRetry(op.entryId) }) { Text(stringResource(R.string.sync_retry)) }
                    TextButton(onClick = { onDiscard(op.entryId) }) { Text(stringResource(R.string.sync_discard)) }
                }
            }
        }
        HorizontalDivider()
        OutlinedButton(onClick = onRetryAll, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sync_retry_all))
        }
    }
}

@Composable
private fun opLabel(type: OutboxOpType): String = stringResource(
    when (type) {
        OutboxOpType.START -> R.string.sync_op_start
        OutboxOpType.CREATE -> R.string.sync_op_create
        OutboxOpType.STOP -> R.string.sync_op_stop
        OutboxOpType.UPDATE -> R.string.sync_op_update
        OutboxOpType.DELETE -> R.string.sync_op_delete
    },
)

/**
 * Map a raw outbox error to a plain-language reason. The stored [SyncOperation.error] holds
 * implementation strings (exception messages, HTTP status text); this classifies them into a few
 * friendly buckets and always falls back to a generic reassurance rather than leaking raw detail.
 */
internal fun failureReasonRes(error: String?): Int {
    val lower = error?.lowercase().orEmpty()
    return when {
        lower.isBlank() -> R.string.sync_reason_generic
        listOf("offline", "timeout", "unable to resolve host", "connect", "unreachable", "network")
            .any { it in lower } -> R.string.sync_reason_offline
        listOf("400", "401", "403", "404", "409", "422", "unprocessable", "forbidden", "unauthorized", "bad request")
            .any { it in lower } -> R.string.sync_reason_rejected
        listOf("500", "502", "503", "504", "server", "gateway", "unavailable")
            .any { it in lower } -> R.string.sync_reason_server
        else -> R.string.sync_reason_generic
    }
}

@Composable
private fun relativeTimeText(timeMs: Long?, neverRes: Int, nowMs: Long): String = when (RelativeTime.bucketOf(timeMs, nowMs)) {
    RelativeTime.Bucket.Never -> stringResource(neverRes)
    RelativeTime.Bucket.JustNow -> stringResource(R.string.sync_time_just_now)
    RelativeTime.Bucket.Minutes -> stringResource(R.string.sync_time_minutes, RelativeTime.amount(timeMs, nowMs))
    RelativeTime.Bucket.Hours -> stringResource(R.string.sync_time_hours, RelativeTime.amount(timeMs, nowMs))
    RelativeTime.Bucket.Days -> stringResource(R.string.sync_time_days, RelativeTime.amount(timeMs, nowMs))
}

@Composable
private fun rememberAbsoluteTime(timeMs: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return formatter.format(Date(timeMs))
}

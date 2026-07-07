/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.tricked.solidverdant.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.tricked.solidverdant.R
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The compact, guided end-of-day review (gap analysis #18). Shows the day's facts (tracked time,
 * billable share, entries, largest untracked gap) and then walks the user through corrections one
 * at a time — a still-running timer, changes that failed to sync, and uncategorized entries — with
 * a progress indicator. Data comes from cached Room state, so it renders offline. Rendered both by
 * the "Review day" segment and, full-screen, by the end-of-day review route.
 */
@Composable
fun ReviewDayPane() {
    val viewModel: ReviewDayViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()

    var showAdjustDialog by remember { mutableStateOf(false) }
    var projectPickerFor by remember { mutableStateOf<ReviewItem?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3500)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingState()
            !state.hasOrganization -> CenteredMessage(
                icon = { Icon(Icons.Outlined.HourglassEmpty, contentDescription = null) },
                text = stringResource(R.string.review_no_org),
            )
            else -> ReviewContent(
                state = state,
                onStop = viewModel::stopRunningTimer,
                onKeepRunning = viewModel::keepRunning,
                onAdjustEnd = { showAdjustDialog = true },
                onRetry = viewModel::retryFailedSync,
                onKeepAsIs = viewModel::keepAsIs,
                onAssign = { item -> projectPickerFor = item },
                onReviewAgain = viewModel::reviewAgain,
            )
        }

        message?.let { messageRes ->
            MessageBanner(
                text = stringResource(messageRes),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }

    if (showAdjustDialog) {
        val now = remember { LocalTime.now() }
        ReviewTimePickerDialog(
            title = stringResource(R.string.review_adjust_end_dialog_title),
            initialHour = now.hour,
            initialMinute = now.minute,
            onConfirm = { hour, minute ->
                showAdjustDialog = false
                viewModel.adjustEndTime(hour, minute)
            },
            onDismiss = { showAdjustDialog = false },
        )
    }

    projectPickerFor?.let { item ->
        ProjectPickerDialog(
            projects = state.projects,
            onSelect = { projectId ->
                projectPickerFor = null
                viewModel.assignProject(item, projectId)
            },
            onDismiss = { projectPickerFor = null },
        )
    }
}

@Composable
internal fun ReviewContent(
    state: ReviewDayUiState,
    onStop: () -> Unit,
    onKeepRunning: () -> Unit,
    onAdjustEnd: () -> Unit,
    onRetry: (ReviewItem) -> Unit,
    onKeepAsIs: (ReviewItem) -> Unit,
    onAssign: (ReviewItem) -> Unit,
    onReviewAgain: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SummaryCard(state)

        val current = state.currentItem
        when {
            current != null -> {
                ProgressHeader(state)
                ReviewItemCard(
                    item = current,
                    state = state,
                    onStop = onStop,
                    onKeepRunning = onKeepRunning,
                    onAdjustEnd = onAdjustEnd,
                    onRetry = onRetry,
                    onKeepAsIs = onKeepAsIs,
                    onAssign = onAssign,
                )
            }

            state.allCaughtUp -> CompletionCard(
                title = stringResource(R.string.review_all_caught_up_title),
                body = stringResource(R.string.review_all_caught_up_body),
                onReviewAgain = onReviewAgain,
            )

            state.nothingTracked -> CompletionCard(
                title = stringResource(R.string.review_nothing_tracked_title),
                body = stringResource(R.string.review_nothing_tracked_body),
                onReviewAgain = null,
            )

            else -> CompletionCard(
                title = stringResource(R.string.review_all_caught_up_title),
                body = stringResource(R.string.review_all_caught_up_body),
                onReviewAgain = null,
            )
        }
    }
}

@Composable
private fun SummaryCard(state: ReviewDayUiState) {
    val date = remember(state.dateEpochDay) { LocalDate.ofEpochDay(state.dateEpochDay) }
    val dateLabel = remember(date) {
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = dateLabel, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStat(
                    label = stringResource(R.string.review_summary_tracked_label),
                    value = formatDuration(state.totalTrackedSeconds),
                )
                SummaryStat(
                    label = stringResource(R.string.review_summary_billable_label),
                    value = state.billablePercent?.let { stringResource(R.string.review_percent, it) }
                        ?: stringResource(R.string.review_value_none),
                )
                SummaryStat(
                    label = stringResource(R.string.review_summary_entries_label),
                    value = state.entryCount.toString(),
                )
                if (state.largestGapSeconds > 0) {
                    SummaryStat(
                        label = stringResource(R.string.review_summary_gap_label),
                        value = formatDuration(state.largestGapSeconds),
                    )
                }
                if (state.uncategorizedCount > 0) {
                    SummaryStat(
                        label = stringResource(R.string.review_summary_uncategorized_label),
                        value = state.uncategorizedCount.toString(),
                    )
                }
                if (state.failedSyncCount > 0) {
                    SummaryStat(
                        label = stringResource(R.string.review_summary_failed_label),
                        value = state.failedSyncCount.toString(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column {
        Text(text = value, style = MaterialTheme.typography.titleLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProgressHeader(state: ReviewDayUiState) {
    val progress = state.progress
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.review_progress_step, progress.position, progress.total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReviewItemCard(
    item: ReviewItem,
    state: ReviewDayUiState,
    onStop: () -> Unit,
    onKeepRunning: () -> Unit,
    onAdjustEnd: () -> Unit,
    onRetry: (ReviewItem) -> Unit,
    onKeepAsIs: (ReviewItem) -> Unit,
    onAssign: (ReviewItem) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (item.type) {
                ReviewItemType.RUNNING_TIMER -> {
                    Text(stringResource(R.string.review_item_running_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.review_item_running_body), style = MaterialTheme.typography.bodyMedium)
                    EntryDescription(item.description)
                    item.startIso?.let { StartedAt(it) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.review_action_stop))
                        }
                        OutlinedButton(onClick = onAdjustEnd, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.review_action_adjust_end))
                        }
                        TextButton(onClick = onKeepRunning, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.review_action_keep_running))
                        }
                    }
                }

                ReviewItemType.FAILED_SYNC -> {
                    Text(stringResource(R.string.review_item_failed_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = item.detail ?: stringResource(R.string.review_item_failed_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRetry(item) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.review_action_retry))
                        }
                        TextButton(onClick = { onKeepAsIs(item) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.review_action_skip))
                        }
                    }
                }

                ReviewItemType.UNCATEGORIZED -> {
                    Text(stringResource(R.string.review_item_uncategorized_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.review_item_uncategorized_body), style = MaterialTheme.typography.bodyMedium)
                    EntryDescription(item.description)
                    TimeRange(item.startIso, item.endIso)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAssign(item) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.review_action_assign_project))
                        }
                        TextButton(onClick = { onKeepAsIs(item) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.review_action_skip))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryDescription(description: String?) {
    val text = if (description.isNullOrBlank()) {
        stringResource(R.string.review_entry_no_description)
    } else {
        description
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StartedAt(startIso: String) {
    val time = remember(startIso) { formatClock(startIso) }
    if (time != null) {
        Text(
            text = stringResource(R.string.review_started_at, time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimeRange(startIso: String?, endIso: String?) {
    val start = remember(startIso) { startIso?.let { formatClock(it) } }
    val end = remember(endIso) { endIso?.let { formatClock(it) } }
    if (start != null && end != null) {
        Text(
            text = stringResource(R.string.review_time_range, start, end),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompletionCard(title: String, body: String, onReviewAgain: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (onReviewAgain != null) {
                OutlinedButton(onClick = onReviewAgain, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.review_again))
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(icon: @Composable () -> Unit, text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun MessageBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        stringResource(R.string.review_duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.review_duration_minutes, minutes)
    }
}

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Format an ISO instant string as a local wall-clock time, or null if it cannot be parsed. */
private fun formatClock(iso: String): String? = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalTime().format(CLOCK_FORMAT)
}.recoverCatching {
    java.time.Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalTime().format(CLOCK_FORMAT)
}.getOrNull()

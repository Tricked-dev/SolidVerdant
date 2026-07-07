package dev.tricked.solidverdant.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.domain.inbox.InboxIssue
import dev.tricked.solidverdant.domain.inbox.InboxIssueType
import dev.tricked.solidverdant.domain.inbox.MissingField
import dev.tricked.solidverdant.ui.components.EditTimeEntryDialog
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Time Inbox (gap analysis #16/#17). Renders the deterministic review checks derived by
 * [dev.tricked.solidverdant.domain.inbox.InboxAnalyzer] as a triage list of cards, each with a
 * one-tap quick-fix (the shared edit/create dialog) and swipe-to-dismiss persisted in
 * `inbox_dismissals`. Handles loading, "all caught up", no-data, offline/stale and action-error
 * states — not just the happy path.
 */
private data class InboxEditTarget(val entry: TimeEntry, val isGap: Boolean)

@Composable
fun InboxPane() {
    val viewModel: InboxViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var editTarget by remember { mutableStateOf<InboxEditTarget?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // Undo snackbar for the most recent dismissal.
    val dismissedMessage = stringResource(R.string.inbox_dismissed_snackbar)
    val undoLabel = stringResource(R.string.inbox_undo)
    androidx.compose.runtime.LaunchedEffect(state.pendingUndoKey) {
        val key = state.pendingUndoKey ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message = dismissedMessage, actionLabel = undoLabel)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDismiss(key) else viewModel.consumeUndo()
    }

    // One-shot action failures.
    val errorRefresh = stringResource(R.string.inbox_error_refresh)
    val errorCreate = stringResource(R.string.inbox_error_create)
    val errorResolve = stringResource(R.string.inbox_error_resolve)
    androidx.compose.runtime.LaunchedEffect(state.actionError) {
        val message = when (state.actionError) {
            InboxActionError.REFRESH_FAILED -> errorRefresh
            InboxActionError.CREATE_FAILED -> errorCreate
            InboxActionError.RESOLVE_FAILED -> errorResolve
            null -> return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(message)
        viewModel.consumeActionError()
    }

    val zone = remember { ZoneId.systemDefault() }
    val projectsById = remember(state.projects) { state.projects.associateBy { it.id } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            InboxHeader(
                issueCount = state.issues.size,
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                onOpenSettings = { showSettings = true },
            )

            if (state.refreshError) {
                StaleBanner(onRetry = viewModel::refresh, onDismiss = viewModel::consumeRefreshError)
            }

            when {
                state.isLoading -> LoadingState()
                state.isCaughtUp && state.hasEntries -> CaughtUpState()
                state.isCaughtUp -> NoDataState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = state.issues, key = { it.key }) { issue ->
                        DismissibleIssue(
                            issue = issue,
                            onDismiss = { viewModel.dismiss(issue) },
                        ) {
                            InboxIssueCard(
                                issue = issue,
                                preventOverlap = state.preventOverlap,
                                projectsById = projectsById,
                                zone = zone,
                                onQuickFix = {
                                    editTarget = when (issue.type) {
                                        InboxIssueType.GAP -> {
                                            val entry = viewModel.blankEntryFor(
                                                startIso = isoOf(issue.startMs, zone),
                                                endIso = isoOf(issue.endMs, zone),
                                            )
                                            entry?.let { InboxEditTarget(it, isGap = true) }
                                        }
                                        else -> issue.primaryEntry?.let { InboxEditTarget(it, isGap = false) }
                                    }
                                },
                                onDismiss = { viewModel.dismiss(issue) },
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    editTarget?.let { target ->
        EditTimeEntryDialog(
            entry = target.entry,
            projects = state.projects,
            tasks = state.tasks,
            tags = state.tags,
            preventOverlap = state.preventOverlap,
            onDismiss = { editTarget = null },
            onSave = { description, projectId, taskId, tags, billable, start, end ->
                if (target.isGap) {
                    viewModel.fillGap(description, projectId, taskId, tags, billable, start, end)
                } else {
                    viewModel.resolveEntryEdit(target.entry, description, projectId, taskId, tags, billable, start, end)
                }
                editTarget = null
            },
        )
    }

    if (showSettings) {
        InboxSettingsSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
internal fun InboxHeader(
    issueCount: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (issueCount == 0) {
                stringResource(R.string.inbox_summary_none)
            } else {
                pluralStringResource(R.plurals.inbox_summary_count, issueCount, issueCount)
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (isRefreshing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.inbox_refresh_action))
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.inbox_settings_action))
        }
    }
}

@Composable
private fun StaleBanner(onRetry: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.inbox_stale_banner),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.inbox_retry)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.inbox_dismiss)) }
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
private fun CaughtUpState() {
    CenteredMessage(
        icon = Icons.Filled.CheckCircle,
        title = stringResource(R.string.inbox_caught_up_title),
        body = stringResource(R.string.inbox_caught_up_body),
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun NoDataState() {
    CenteredMessage(
        icon = Icons.Outlined.Inbox,
        title = stringResource(R.string.inbox_empty_title),
        body = stringResource(R.string.inbox_empty_body),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CenteredMessage(icon: ImageVector, title: String, body: String, tint: androidx.compose.ui.graphics.Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleIssue(
    issue: InboxIssue,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        },
    )
    val dismissCd = stringResource(R.string.inbox_swipe_dismiss_cd)
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = dismissCd)
            }
        },
        content = { content() },
    )
}

@Composable
internal fun InboxIssueCard(
    issue: InboxIssue,
    preventOverlap: Boolean,
    projectsById: Map<String, Project>,
    zone: ZoneId,
    onQuickFix: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title: String
    val body: String
    val actionLabel: String
    when (issue.type) {
        InboxIssueType.OVERLAP -> {
            title = stringResource(R.string.inbox_issue_overlap_title)
            body = if (preventOverlap) {
                stringResource(R.string.inbox_issue_overlap_body_policy)
            } else {
                stringResource(R.string.inbox_issue_overlap_body)
            }
            actionLabel = stringResource(R.string.inbox_action_adjust)
        }
        InboxIssueType.GAP -> {
            title = stringResource(R.string.inbox_issue_gap_title)
            body = stringResource(R.string.inbox_issue_gap_body)
            actionLabel = stringResource(R.string.inbox_action_add_entry)
        }
        InboxIssueType.MISSING_METADATA -> {
            title = stringResource(R.string.inbox_issue_missing_title)
            body = stringResource(R.string.inbox_issue_missing_body, missingFieldsText(issue.missingFields))
            actionLabel = stringResource(R.string.inbox_action_add_details)
        }
        InboxIssueType.LONG_DURATION -> {
            title = stringResource(R.string.inbox_issue_long_title)
            body = stringResource(R.string.inbox_issue_long_body, durationText(issue.endMs - issue.startMs))
            actionLabel = stringResource(R.string.inbox_action_review)
        }
    }

    val subject = when (issue.type) {
        InboxIssueType.GAP -> null
        InboxIssueType.OVERLAP -> issue.primaryEntry?.let { entrySubject(it, projectsById) }
        else -> issue.primaryEntry?.let { entrySubject(it, projectsById) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = timeRangeText(issue.startMs, issue.endMs, zone),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subject != null) {
                Text(
                    text = subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.inbox_action_dismiss)) }
                FilledTonalButton(onClick = onQuickFix) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun missingFieldsText(fields: Set<MissingField>): String {
    val labels = fields.sortedBy { it.ordinal }.map {
        when (it) {
            MissingField.PROJECT -> stringResource(R.string.inbox_field_project)
            MissingField.TASK -> stringResource(R.string.inbox_field_task)
            MissingField.DESCRIPTION -> stringResource(R.string.inbox_field_description)
            MissingField.TAGS -> stringResource(R.string.inbox_field_tags)
        }
    }
    return labels.joinToString(", ")
}

@Composable
private fun entrySubject(entry: TimeEntry, projectsById: Map<String, Project>): String {
    val description = entry.description?.takeIf { it.isNotBlank() }
    val project = entry.projectId?.let { projectsById[it]?.name }
    return description ?: project ?: stringResource(R.string.inbox_entry_untitled)
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

private fun timeRangeText(startMs: Long, endMs: Long, zone: ZoneId): String {
    val start = OffsetDateTime.ofInstant(Instant.ofEpochMilli(startMs), zone)
    val end = OffsetDateTime.ofInstant(Instant.ofEpochMilli(endMs), zone)
    val startText = start.format(DATE_TIME_FORMAT)
    val endText = if (start.toLocalDate() == end.toLocalDate()) end.format(TIME_FORMAT) else end.format(DATE_TIME_FORMAT)
    return "$startText – $endText"
}

@Composable
private fun durationText(durationMs: Long): String {
    val totalMinutes = (durationMs / 60000L).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> stringResource(R.string.inbox_duration_hm, hours, minutes)
        hours > 0 -> stringResource(R.string.inbox_duration_h, hours)
        else -> stringResource(R.string.inbox_duration_m, minutes)
    }
}

private fun isoOf(epochMs: Long, zone: ZoneId): String =
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

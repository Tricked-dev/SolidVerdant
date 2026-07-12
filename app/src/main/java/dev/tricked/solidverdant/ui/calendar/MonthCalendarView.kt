/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.ui.components.EmptyState
import dev.tricked.solidverdant.ui.components.EntryBlock
import dev.tricked.solidverdant.ui.components.LoadingState
import dev.tricked.solidverdant.ui.statistics.hexToColor
import dev.tricked.solidverdant.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthCalendarView(
    state: CalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEntryClick: (TimeEntry) -> Unit,
    projects: List<Project> = emptyList(),
    tasks: List<Task> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var monthExpanded by remember { mutableStateOf(true) }
    val timelineInitialScroll = with(LocalDensity.current) { (CalendarHourHeight * INITIAL_SCROLL_HOURS).roundToPx() }
    val timelineScrollState = rememberScrollState(initial = timelineInitialScroll)
    Column(modifier = modifier.fillMaxWidth().padding(Dimens.Space12)) {
        if (!monthExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { monthExpanded = true }
                    .padding(horizontal = Dimens.Space4, vertical = Dimens.Space8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = stringResource(R.string.calendar_show_month),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        AnimatedVisibility(
            visible = monthExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onPreviousMonth) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.calendar_previous_month),
                        )
                    }
                    Text(
                        text = "${state.visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${state.visibleMonth.year}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = onNextMonth) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.calendar_next_month),
                        )
                    }
                }

                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Space4),
                    )
                }

                // Grid
                val weeks = monthGridWeeks(state.visibleMonth, state.weekStart)
                val maxSeconds = state.bucketsByDate.values.maxOfOrNull { it.totalSeconds } ?: 1L
                weeks.forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            val bucket = state.bucketsByDate[day]
                            val inMonth = java.time.YearMonth.from(day) == state.visibleMonth
                            val selected = day == state.selectedDate
                            val intensity = ((bucket?.totalSeconds ?: 0L).toFloat() / maxSeconds).coerceIn(0f, 1f)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(Dimens.Space2)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f + 0.55f * intensity)
                                        },
                                    )
                                    .clickable {
                                        onSelectDate(day)
                                        monthExpanded = false
                                    }
                                    .testTag("day-cell-$day"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = day.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (inMonth) {
                                        Color.Unspecified
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    },
                                )
                                if (bucket != null) {
                                    Text(
                                        text = formatDuration(bucket.totalSeconds),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected day entries
        val entries = state.bucketsByDate[state.selectedDate]?.entries ?: emptyList()
        if (monthExpanded) {
            Text(
                text = state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Dimens.Space16, bottom = Dimens.Space8),
            )
        }
        if (entries.isEmpty()) {
            if (state.isLoading) {
                LoadingState(label = stringResource(R.string.calendar_loading_entries))
            } else {
                EmptyState(text = stringResource(R.string.calendar_no_entries))
            }
        } else if (!monthExpanded) {
            DayTimeline(
                day = state.selectedDate,
                entries = entries,
                projects = projects,
                tasks = tasks,
                zone = state.zone,
                scrollState = timelineScrollState,
                fillViewport = true,
                onEntryClick = onEntryClick,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item(key = state.selectedDate) {
                    DayTimeline(
                        day = state.selectedDate,
                        entries = entries,
                        projects = projects,
                        tasks = tasks,
                        zone = state.zone,
                        scrollState = timelineScrollState,
                        onEntryClick = onEntryClick,
                    )
                }
            }
        }
    }
}

private const val INITIAL_SCROLL_HOURS = 8

/**
 * Single-day vertical timeline for the selected day. Shares the week grid's hour gutter/gridlines
 * ([HourGridlines]), entry treatment ([EntryBlock]) and current-time marker ([CurrentTimeMarker])
 * so a day rendered here is visually identical to the same day inside the week grid.
 */
@Composable
fun DayTimeline(
    day: LocalDate,
    entries: List<TimeEntry>,
    projects: List<Project>,
    tasks: List<Task>,
    zone: ZoneId,
    onEntryClick: (TimeEntry) -> Unit,
    scrollState: ScrollState? = null,
    fillViewport: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val now = remember { Instant.now() }
    val today = remember(zone) { LocalDate.now(zone) }
    val noDescription = stringResource(R.string.calendar_entry_untitled)
    val initialScroll = with(LocalDensity.current) { (CalendarHourHeight * INITIAL_SCROLL_HOURS).roundToPx() }
    val effectiveScrollState = scrollState ?: rememberScrollState(initial = initialScroll)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillViewport) Modifier.fillMaxHeight() else Modifier.height(520.dp))
            .verticalScroll(effectiveScrollState),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(CalendarTotalHeight)) {
            HourGridlines()

            entries.forEach { entry ->
                val project = projects.find { it.id == entry.projectId }
                val task = tasks.find { it.id == entry.taskId }
                val (top, height) = timelineOffsets(entry, day, now, zone)
                val blockColor = project?.color?.let { hexToColor(it) }
                    ?: MaterialTheme.colorScheme.primary
                val label = entry.description?.ifBlank { null } ?: noDescription
                val subtitle = listOfNotNull(project?.name, task?.name)
                    .joinToString(" · ")
                    .ifBlank { null }
                EntryBlock(
                    color = blockColor,
                    title = label,
                    subtitle = subtitle,
                    time = formatDuration(entryDurationSeconds(entry, now)),
                    modifier = Modifier
                        .padding(start = CalendarGutterWidth, end = Dimens.Space2)
                        .offset(y = CalendarTotalHeight * top)
                        .height((CalendarTotalHeight * height).coerceAtLeast(Dimens.EntryMinHeight))
                        .clickable { onEntryClick(entry) }
                        .testTag("entry-row-${entry.id}"),
                )
            }

            if (day == today) {
                CurrentTimeMarker(
                    now = now,
                    day = day,
                    zone = zone,
                    modifier = Modifier.padding(start = CalendarGutterWidth),
                )
            }
        }
    }
}

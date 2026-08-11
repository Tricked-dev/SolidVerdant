/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.ui.components.EntryBlock
import dev.tricked.solidverdant.ui.components.LoadingState
import dev.tricked.solidverdant.ui.statistics.hexToColor
import dev.tricked.solidverdant.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
fun MonthCalendarView(
    state: CalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEntryClick: (TimeEntry) -> Unit,
    onEntryLongPress: (TimeEntry) -> Unit = {},
    onMoveEntry: (TimeEntry, String, String) -> Unit = { _, _, _ -> },
    onCreateRange: (CalendarTimeRange) -> Unit = {},
    modifier: Modifier = Modifier,
    projects: List<Project> = emptyList(),
    tasks: List<Task> = emptyList(),
) {
    var monthExpanded by remember { mutableStateOf(true) }
    val locale = LocalLocale.current.platformLocale
    val initialScrollHours = (INITIAL_SCROLL_HOURS - state.calendarSettings.startHour)
        .coerceIn(0, (state.calendarSettings.endHour - state.calendarSettings.startHour - 1).coerceAtLeast(0))
    val timelineInitialScroll = with(LocalDensity.current) {
        (calendarHourHeight(state.calendarSettings) * initialScrollHours).roundToPx()
    }
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
            MonthCalendarGrid(
                state = state,
                locale = locale,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onSelectDate = onSelectDate,
                onCollapse = { monthExpanded = false },
            )
        }

        SelectedDayEntries(
            state = state,
            monthExpanded = monthExpanded,
            projects = projects,
            tasks = tasks,
            scrollState = timelineScrollState,
            onEntryClick = onEntryClick,
            onEntryLongPress = onEntryLongPress,
            onMoveEntry = onMoveEntry,
            onCreateRange = onCreateRange,
            settings = state.calendarSettings,
        )
    }
}

@Composable
private fun MonthCalendarGrid(
    state: CalendarUiState,
    locale: java.util.Locale,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onCollapse: () -> Unit,
) {
    Column {
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
                text = "${state.visibleMonth.month.getDisplayName(TextStyle.FULL, locale)} ${state.visibleMonth.year}",
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
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Space4))
        }
        MonthCalendarGridWeeks(state, onSelectDate, onCollapse)
    }
}

@Composable
private fun MonthCalendarGridWeeks(state: CalendarUiState, onSelectDate: (LocalDate) -> Unit, onCollapse: () -> Unit) {
    val weeks = monthGridWeeks(state.visibleMonth, state.weekStart)
    val maxSeconds = state.bucketsByDate.values.maxOfOrNull { it.totalSeconds } ?: 1L
    weeks.forEach { week ->
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { day ->
                MonthCalendarDay(
                    day = day,
                    state = state,
                    maxSeconds = maxSeconds,
                    onSelectDate = onSelectDate,
                    onCollapse = onCollapse,
                )
            }
        }
    }
}

@Composable
private fun RowScope.MonthCalendarDay(
    day: LocalDate,
    state: CalendarUiState,
    maxSeconds: Long,
    onSelectDate: (LocalDate) -> Unit,
    onCollapse: () -> Unit,
) {
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
                onCollapse()
            }
            .testTag("day-cell-$day"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = calendarDayContentColor(
                selected = selected,
                isToday = false,
                primary = MaterialTheme.colorScheme.primary,
                onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                default = if (inMonth) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            ),
        )
        bucket?.let {
            Text(
                text = formatDuration(it.totalSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = calendarDayContentColor(
                    selected = selected,
                    isToday = false,
                    primary = MaterialTheme.colorScheme.primary,
                    onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                    default = Color.Unspecified,
                ),
            )
        }
    }
}

@Composable
private fun ColumnScope.SelectedDayEntries(
    state: CalendarUiState,
    monthExpanded: Boolean,
    projects: List<Project>,
    tasks: List<Task>,
    scrollState: ScrollState,
    onEntryClick: (TimeEntry) -> Unit,
    onEntryLongPress: (TimeEntry) -> Unit,
    onMoveEntry: (TimeEntry, String, String) -> Unit,
    onCreateRange: (CalendarTimeRange) -> Unit,
    settings: CalendarGridSettings = CalendarGridSettings(),
) {
    val entries = state.bucketsByDate[state.selectedDate]?.entries.orEmpty()
    if (monthExpanded) {
        Text(
            text = state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = Dimens.Space16, bottom = Dimens.Space8),
        )
    }
    when {
        entries.isEmpty() && state.isLoading -> LoadingState(label = stringResource(R.string.calendar_loading_entries))
        entries.isEmpty() -> DayTimeline(
            day = state.selectedDate,
            entries = emptyList(),
            projects = projects,
            tasks = tasks,
            zone = state.zone,
            settings = settings,
            scrollState = scrollState,
            fillViewport = true,
            onEntryClick = onEntryClick,
            onEntryLongPress = onEntryLongPress,
            onMoveEntry = onMoveEntry,
            onCreateRange = onCreateRange,
            modifier = Modifier.weight(1f),
        )
        !monthExpanded -> DayTimeline(
            day = state.selectedDate,
            entries = entries,
            projects = projects,
            tasks = tasks,
            zone = state.zone,
            settings = settings,
            scrollState = scrollState,
            fillViewport = true,
            onEntryClick = onEntryClick,
            onEntryLongPress = onEntryLongPress,
            onMoveEntry = onMoveEntry,
            onCreateRange = onCreateRange,
            modifier = Modifier.weight(1f),
        )
        else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item(key = state.selectedDate) {
                DayTimeline(
                    day = state.selectedDate,
                    entries = entries,
                    projects = projects,
                    tasks = tasks,
                    zone = state.zone,
                    settings = settings,
                    scrollState = scrollState,
                    onEntryClick = onEntryClick,
                    onEntryLongPress = onEntryLongPress,
                    onMoveEntry = onMoveEntry,
                    onCreateRange = onCreateRange,
                )
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
@OptIn(ExperimentalFoundationApi::class)
fun DayTimeline(
    day: LocalDate,
    entries: List<TimeEntry>,
    projects: List<Project>,
    tasks: List<Task>,
    zone: ZoneId,
    settings: CalendarGridSettings = CalendarGridSettings(),
    onEntryClick: (TimeEntry) -> Unit,
    onEntryLongPress: (TimeEntry) -> Unit = {},
    onMoveEntry: (TimeEntry, String, String) -> Unit = { _, _, _ -> },
    onCreateRange: (CalendarTimeRange) -> Unit = {},
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    fillViewport: Boolean = false,
) {
    val now = remember { Instant.now() }
    val today = remember(zone) { LocalDate.now(zone) }
    val noDescription = stringResource(R.string.calendar_entry_untitled)
    val initialScrollHours = (INITIAL_SCROLL_HOURS - settings.startHour)
        .coerceIn(0, (settings.endHour - settings.startHour - 1).coerceAtLeast(0))
    val initialScroll = with(LocalDensity.current) {
        (calendarHourHeight(settings) * initialScrollHours).roundToPx()
    }
    val effectiveScrollState = scrollState ?: rememberScrollState(initial = initialScroll)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillViewport) Modifier.fillMaxHeight() else Modifier.height(520.dp))
            .verticalScroll(effectiveScrollState),
    ) {
        val totalHeight = calendarTotalHeight(settings)
        Box(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
            HourGridlines(settings = settings)
            CalendarTimeSelectionLayer(
                day = day,
                zone = zone,
                settings = settings,
                onSelectionComplete = onCreateRange,
                modifier = Modifier.padding(start = CalendarGutterWidth),
            )

            layoutTrackedEntries(entries, day, now, zone, settings).forEach { block ->
                val entry = block.entry
                val project = projects.find { it.id == entry.projectId }
                val task = tasks.find { it.id == entry.taskId }
                val top = block.startFraction
                val height = block.heightFraction
                val blockColor = project?.color?.let { hexToColor(it) }
                    ?: MaterialTheme.colorScheme.primary
                val label = entry.description?.ifBlank { null } ?: noDescription
                val subtitle = listOfNotNull(project?.name, task?.name)
                    .joinToString(" · ")
                    .ifBlank { null }
                val entryModifier = calendarEntryDragModifier(
                    modifier = Modifier
                        .padding(start = CalendarGutterWidth, end = Dimens.Space2)
                        .offset(y = totalHeight * top),
                    entry = entry,
                    day = day,
                    zone = zone,
                    dayIndex = 0,
                    dayCount = 1,
                    blockStartFraction = top,
                    blockHeightPx = with(LocalDensity.current) {
                        (totalHeight * height).coerceAtLeast(Dimens.EntryMinHeight).toPx()
                    },
                    gridHeightPx = with(LocalDensity.current) { totalHeight.toPx() },
                    columnWidthPx = with(LocalDensity.current) { totalHeight.toPx() },
                    settings = settings,
                    onMoveEntry = onMoveEntry,
                )
                EntryBlock(
                    color = blockColor,
                    title = label,
                    subtitle = subtitle,
                    time = formatDuration(entryDurationSecondsOnDay(entry, day, zone, now)),
                    modifier = entryModifier
                        .combinedClickable(
                            onClick = { onEntryClick(entry) },
                            onLongClick = { onEntryLongPress(entry) },
                        )
                        .testTag("entry-row-${entry.id}"),
                )
            }

            if (day == today) {
                CurrentTimeMarker(
                    now = now,
                    day = day,
                    zone = zone,
                    settings = settings,
                    modifier = Modifier.padding(start = CalendarGutterWidth),
                )
            }
        }
    }
}

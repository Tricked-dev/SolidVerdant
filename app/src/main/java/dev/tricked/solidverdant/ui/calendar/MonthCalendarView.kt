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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.TimeEntryType
import dev.tricked.solidverdant.data.repository.TimeEntryRepository.EntrySyncStatus
import dev.tricked.solidverdant.domain.time.isRunningTimeEntry
import dev.tricked.solidverdant.ui.components.EntryBlock
import dev.tricked.solidverdant.ui.components.LoadingState
import dev.tricked.solidverdant.ui.localization.appLocale
import dev.tricked.solidverdant.ui.statistics.hexToColor
import dev.tricked.solidverdant.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    clients: List<Client> = emptyList(),
    syncStatusByEntryId: Map<String, EntrySyncStatus> = emptyMap(),
) {
    var monthExpanded by remember { mutableStateOf(true) }
    val locale = appLocale()
    val selectedEntries = state.bucketsByDate[state.selectedDate]?.entries.orEmpty()
    val now = rememberCalendarNow(secondPrecision = selectedEntries.any(::isRunningTimeEntry))
    val initialScrollHours = calendarInitialScrollHours(now, state.zone, state.calendarSettings).toFloat()
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
                    state.selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
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
            clients = clients,
            scrollState = timelineScrollState,
            onEntryClick = onEntryClick,
            onEntryLongPress = onEntryLongPress,
            syncStatusByEntryId = syncStatusByEntryId,
            onMoveEntry = onMoveEntry,
            onCreateRange = onCreateRange,
            now = now,
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
                text = stringResource(
                    R.string.calendar_month_header,
                    state.visibleMonth.month.getDisplayName(TextStyle.FULL, locale),
                    state.visibleMonth.year,
                ),
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
    clients: List<Client>,
    scrollState: ScrollState,
    onEntryClick: (TimeEntry) -> Unit,
    onEntryLongPress: (TimeEntry) -> Unit,
    syncStatusByEntryId: Map<String, EntrySyncStatus>,
    onMoveEntry: (TimeEntry, String, String) -> Unit,
    onCreateRange: (CalendarTimeRange) -> Unit,
    now: Instant,
    settings: CalendarGridSettings = CalendarGridSettings(),
) {
    val entries = state.bucketsByDate[state.selectedDate]?.entries.orEmpty()
    if (monthExpanded) {
        Text(
            text = state.selectedDate.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(
                    appLocale(),
                ),
            ),
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
            clients = clients,
            zone = state.zone,
            settings = settings,
            scrollState = scrollState,
            fillViewport = true,
            onEntryClick = onEntryClick,
            onEntryLongPress = onEntryLongPress,
            syncStatusByEntryId = syncStatusByEntryId,
            onMoveEntry = onMoveEntry,
            onCreateRange = onCreateRange,
            now = now,
            modifier = Modifier.weight(1f),
        )
        !monthExpanded -> DayTimeline(
            day = state.selectedDate,
            entries = entries,
            projects = projects,
            tasks = tasks,
            clients = clients,
            zone = state.zone,
            settings = settings,
            scrollState = scrollState,
            fillViewport = true,
            onEntryClick = onEntryClick,
            onEntryLongPress = onEntryLongPress,
            syncStatusByEntryId = syncStatusByEntryId,
            onMoveEntry = onMoveEntry,
            onCreateRange = onCreateRange,
            now = now,
            modifier = Modifier.weight(1f),
        )
        else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item(key = state.selectedDate) {
                DayTimeline(
                    day = state.selectedDate,
                    entries = entries,
                    projects = projects,
                    tasks = tasks,
                    clients = clients,
                    zone = state.zone,
                    settings = settings,
                    scrollState = scrollState,
                    onEntryClick = onEntryClick,
                    onEntryLongPress = onEntryLongPress,
                    syncStatusByEntryId = syncStatusByEntryId,
                    onMoveEntry = onMoveEntry,
                    onCreateRange = onCreateRange,
                    now = now,
                )
            }
        }
    }
}

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
    clients: List<Client> = emptyList(),
    zone: ZoneId,
    now: Instant,
    settings: CalendarGridSettings = CalendarGridSettings(),
    onEntryClick: (TimeEntry) -> Unit,
    onEntryLongPress: (TimeEntry) -> Unit = {},
    syncStatusByEntryId: Map<String, EntrySyncStatus> = emptyMap(),
    onMoveEntry: (TimeEntry, String, String) -> Unit = { _, _, _ -> },
    onCreateRange: (CalendarTimeRange) -> Unit = {},
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    fillViewport: Boolean = false,
) {
    val today = now.atZone(zone).toLocalDate()
    val noDescription = stringResource(R.string.calendar_entry_untitled)
    val initialScrollHours = calendarInitialScrollHours(now, zone, settings).toFloat()
    val initialScroll = with(LocalDensity.current) {
        (calendarHourHeight(settings) * initialScrollHours).roundToPx()
    }
    val effectiveScrollState = scrollState ?: rememberScrollState(initial = initialScroll)
    val projectsById = remember(projects) { projects.associateBy { it.id } }
    val tasksById = remember(tasks) { tasks.associateBy { it.id } }
    val clientsById = remember(clients) { clients.associateBy { it.id } }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillViewport) Modifier.fillMaxHeight() else Modifier.height(520.dp))
            .verticalScroll(effectiveScrollState),
    ) {
        val totalHeight = calendarTotalHeight(settings)
        val entryAreaWidth = maxWidth - CalendarGutterWidth - Dimens.Space2
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
                val project = projectsById[entry.projectId]
                val task = tasksById[entry.taskId]
                val client = project?.clientId?.let(clientsById::get)
                val top = block.startFraction
                val height = block.heightFraction
                val slotWidth = entryAreaWidth / block.columnCount.coerceAtLeast(1)
                val blockColor = if (entry.type == TimeEntryType.BREAK) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    project?.color?.let { hexToColor(it) } ?: MaterialTheme.colorScheme.primary
                }
                val metadata = calendarEntryMetadata(
                    entry = entry,
                    projectName = project?.name,
                    taskName = task?.name,
                    clientName = client?.name,
                )
                val label = if (entry.type == TimeEntryType.BREAK) {
                    entry.description?.ifBlank { null }?.let { stringResource(R.string.calendar_break_with_description, it) }
                        ?: stringResource(R.string.calendar_break_entry)
                } else {
                    metadata.title ?: noDescription
                }
                val subtitle = if (entry.type == TimeEntryType.BREAK) {
                    null
                } else {
                    metadata.subtitle
                }
                val duration = metadata.durationSeconds?.let(::formatDuration)
                    ?: formatDuration(entryDurationSecondsOnDay(entry, day, zone, now))
                val details = listOfNotNull(subtitle, duration).joinToString(", ")
                val a11y = if (details.isBlank()) {
                    stringResource(R.string.calendar_entry_a11y, label)
                } else {
                    stringResource(R.string.calendar_entry_a11y_details, label, details)
                }
                val entryModifier = calendarEntryDragModifier(
                    modifier = Modifier
                        .offset(
                            x = CalendarGutterWidth + (slotWidth * block.column),
                            y = totalHeight * top,
                        )
                        .width(slotWidth)
                        .padding(end = Dimens.Space1),
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
                    columnWidthPx = with(LocalDensity.current) { entryAreaWidth.toPx() },
                    settings = settings,
                    onMoveEntry = onMoveEntry,
                )
                EntryBlock(
                    color = blockColor,
                    title = label,
                    subtitle = subtitle,
                    time = duration,
                    modifier = entryModifier
                        .height((totalHeight * height).coerceAtLeast(Dimens.EntryMinHeight))
                        .combinedClickable(
                            onClick = { onEntryClick(entry) },
                            onLongClick = { onEntryLongPress(entry) },
                        )
                        .testTag("entry-row-${entry.id}")
                        .semantics { contentDescription = a11y },
                    syncStatus = syncStatusByEntryId[entry.id],
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

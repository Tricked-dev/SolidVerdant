/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.calendar.DeviceCalendarEvent
import dev.tricked.solidverdant.data.model.Project
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

/**
 * Google-Calendar-style multi-day time grid. Renders [CalendarUiState.visibleDays] as columns with
 * device-calendar events drawn as faded, read-only background blocks behind the tracked time
 * entries. Overlap packing, midnight-spanning clipping, and all-day handling come from
 * [WeekCalendarLayout]; this file is presentation only.
 *
 * The hour gutter, gridlines, hour height and the tracked-entry blocks come from the shared grid
 * primitives ([HourGridlines], [CalendarHourHeight], [EntryBlock]) so the week grid and the month
 * day-timeline read as one product.
 */
@Composable
fun WeekCalendarView(
    state: CalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
    onEntryClick: (TimeEntry) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    projects: List<Project>,
    modifier: Modifier = Modifier,
) {
    val days = state.visibleDays
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }
    val now = remember { Instant.now() }
    val locale = Locale.getDefault()

    // Precompute the per-day layouts once per data change rather than inside the render loop.
    val timedByDay = remember(state.overlayEvents, days) {
        days.associateWith { layoutTimedEvents(state.overlayEvents, it, zone) }
    }
    val allDayByDay = remember(state.overlayEvents, days) {
        days.associateWith { allDayEventsForDay(state.overlayEvents, it) }
    }
    val hasAnyAllDay = allDayByDay.values.any { it.isNotEmpty() }
    val hasTrackedEntries = remember(state.bucketsByDate, days) {
        days.any { state.bucketsByDate[it]?.entries?.isNotEmpty() == true }
    }
    val hasContent = hasTrackedEntries || state.overlayEvents.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        WeekNavHeader(days = days, viewMode = state.viewMode, locale = locale,
            onPrevious = onPrevious, onNext = onNext, onToday = onToday)

        // Subtle top-line refresh only when content is already on screen; a first, empty load uses
        // the full-content LoadingState below instead.
        if (state.isLoading && hasContent) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Day-of-week / date header aligned with the grid gutter.
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(CalendarGutterWidth))
            days.forEach { day ->
                DayHeaderCell(
                    day = day,
                    selected = day == state.selectedDate,
                    isToday = day == today,
                    locale = locale,
                    onSelect = { onSelectDate(day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HairLine()

        if (hasAnyAllDay) {
            AllDayRow(days = days, allDayByDay = allDayByDay)
            HairLine()
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading && !hasContent ->
                    LoadingState(
                        modifier = Modifier.align(Alignment.Center),
                        label = stringResource(R.string.calendar_loading_entries),
                    )

                !hasContent ->
                    EmptyState(
                        text = stringResource(R.string.calendar_no_entries),
                        modifier = Modifier.align(Alignment.Center),
                    )

                else -> WeekGrid(
                    days = days,
                    today = today,
                    now = now,
                    zone = zone,
                    timedByDay = timedByDay,
                    state = state,
                    projects = projects,
                    onEntryClick = onEntryClick,
                )
            }
        }
    }
}

@Composable
private fun WeekGrid(
    days: List<LocalDate>,
    today: LocalDate,
    now: Instant,
    zone: ZoneId,
    timedByDay: Map<LocalDate, List<EventBlock>>,
    state: CalendarUiState,
    projects: List<Project>,
    onEntryClick: (TimeEntry) -> Unit,
) {
    val initialScroll = with(LocalDensity.current) { (CalendarHourHeight * 7).roundToPx() }
    val scrollState = rememberScrollState(initial = initialScroll)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(CalendarTotalHeight)) {
            // Shared hour gutter + gridlines.
            HourGridlines()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CalendarTotalHeight)
                    .padding(start = CalendarGutterWidth),
            ) {
                days.forEach { day ->
                    DayColumn(
                        day = day,
                        isToday = day == today,
                        now = now,
                        zone = zone,
                        eventBlocks = timedByDay[day].orEmpty(),
                        entries = state.bucketsByDate[day]?.entries.orEmpty(),
                        projects = projects,
                        onEntryClick = onEntryClick,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekNavHeader(
    days: List<LocalDate>,
    viewMode: CalendarViewMode,
    locale: Locale,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.calendar_show_previous),
            )
        }
        Text(
            text = rangeLabel(days, viewMode, locale),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onToday) { Text(stringResource(R.string.calendar_today)) }
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.calendar_show_next),
            )
        }
    }
}

@Composable
private fun DayHeaderCell(
    day: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    locale: Locale,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekday = day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    val bg = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Unspecified
    }
    Column(
        modifier = modifier
            .heightIn(min = Dimens.MinTouchTarget)
            .padding(Dimens.Space2)
            .clip(MaterialTheme.shapes.small)
            .then(if (bg != Color.Unspecified) Modifier.background(bg) else Modifier)
            .clickable(onClick = onSelect)
            .padding(vertical = Dimens.Space4)
            .testTag("week-day-header-$day"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = weekday,
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.primary else Color.Unspecified,
        )
    }
}

@Composable
private fun AllDayRow(
    days: List<LocalDate>,
    allDayByDay: Map<LocalDate, List<DeviceCalendarEvent>>,
) {
    val untitled = stringResource(R.string.calendar_overlay_event_untitled)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .padding(vertical = Dimens.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.calendar_all_day),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(CalendarGutterWidth).padding(end = Dimens.Space4),
            textAlign = TextAlign.End,
        )
        days.forEach { day ->
            Column(modifier = Modifier.weight(1f).padding(horizontal = 1.dp)) {
                allDayByDay[day].orEmpty().take(3).forEach { event ->
                    val label = event.title?.ifBlank { null } ?: untitled
                    val base = event.eventColor()
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(base.copy(alpha = 0.16f))
                            .padding(horizontal = Dimens.Space4, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    isToday: Boolean,
    now: Instant,
    zone: ZoneId,
    eventBlocks: List<EventBlock>,
    entries: List<TimeEntry>,
    projects: List<Project>,
    onEntryClick: (TimeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val untitled = stringResource(R.string.calendar_overlay_event_untitled)
    val noDescription = stringResource(R.string.calendar_entry_untitled)
    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 1.dp)
            .testTag("week-day-column-$day"),
    ) {
        val colWidth = maxWidth

        // Faded, read-only device-calendar events behind the tracked entries.
        eventBlocks.forEach { block ->
            val slotWidth = colWidth / block.columnCount.coerceAtLeast(1)
            val base = block.event.eventColor()
            val label = block.event.title?.ifBlank { null } ?: untitled
            val a11y = stringResource(R.string.calendar_overlay_event_a11y, label)
            Box(
                modifier = Modifier
                    .offset(
                        x = slotWidth * block.column,
                        y = CalendarTotalHeight * block.startFraction,
                    )
                    .width(slotWidth)
                    .height((CalendarTotalHeight * block.heightFraction).coerceAtLeast(14.dp))
                    .padding(0.5.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(base.copy(alpha = 0.14f))
                    .border(1.dp, base.copy(alpha = 0.5f), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 3.dp, vertical = 1.dp)
                    .semantics { contentDescription = a11y },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Tracked time entries drawn on top with the shared EntryBlock treatment.
        entries.forEach { entry ->
            val (top, height) = timelineOffsets(entry, day, now, zone)
            val base = projects.firstOrNull { it.id == entry.projectId }?.color
                ?.let { hexToColor(it) }
                ?: MaterialTheme.colorScheme.primary
            val label = entry.description?.ifBlank { null } ?: noDescription
            val a11y = stringResource(R.string.calendar_entry_a11y, label)
            EntryBlock(
                color = base,
                title = label,
                modifier = Modifier
                    .offset(y = CalendarTotalHeight * top)
                    .padding(horizontal = 0.5.dp)
                    .height((CalendarTotalHeight * height).coerceAtLeast(Dimens.EntryMinHeight))
                    .clickable(onClick = { onEntryClick(entry) })
                    .testTag("week-entry-${entry.id}")
                    .semantics { contentDescription = a11y },
            )
        }

        // Current-time indicator on today's column.
        if (isToday) {
            CurrentTimeMarker(now = now, day = day, zone = zone)
        }
    }
}

/**
 * Shared hour gutter + horizontal gridlines for the day/week time grid. Draws 24 right-aligned
 * hour labels in a [CalendarGutterWidth] gutter and a per-hour hairline (via `outlineVariant`)
 * across the full width. Used by both the week grid and the month day-timeline.
 */
@Composable
internal fun HourGridlines(modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    Box(modifier = modifier.fillMaxWidth().height(CalendarTotalHeight)) {
        for (hour in 0..23) {
            Text(
                text = "%02d:00".format(hour),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .offset(y = CalendarHourHeight * hour)
                    .width(CalendarGutterWidth)
                    .padding(end = Dimens.Space4),
                textAlign = TextAlign.End,
            )
            Spacer(
                modifier = Modifier
                    .offset(x = CalendarGutterWidth, y = CalendarHourHeight * hour)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(lineColor),
            )
        }
    }
}

/**
 * The red "now" line for [day], positioned by the current instant within the 24h column. Renders
 * nothing when [now] falls outside [day]. Pass a start padding via [modifier] to align it past the
 * hour gutter in single-column layouts.
 */
@Composable
internal fun CurrentTimeMarker(
    now: Instant,
    day: LocalDate,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val fraction = (now.epochSecond - day.atStartOfDay(zone).toInstant().epochSecond)
        .toFloat() / SECONDS_PER_DAY
    if (fraction in 0f..1f) {
        Box(
            modifier = modifier
                .offset(y = CalendarTotalHeight * fraction)
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.error)
                .clearAndSetSemantics { },
        )
    }
}

@Composable
private fun HairLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/** Compose color for a device event, falling back to the theme secondary when none is supplied. */
@Composable
private fun DeviceCalendarEvent.eventColor(): Color =
    colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.secondary

private fun rangeLabel(days: List<LocalDate>, viewMode: CalendarViewMode, locale: Locale): String {
    if (days.isEmpty()) return ""
    val first = days.first()
    val last = days.last()
    if (viewMode == CalendarViewMode.DAY || first == last) {
        return first.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale))
    }
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    val startLabel = if (first.month == last.month) first.dayOfMonth.toString() else first.format(dayMonth)
    val endLabel = last.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    return "$startLabel – $endLabel"
}

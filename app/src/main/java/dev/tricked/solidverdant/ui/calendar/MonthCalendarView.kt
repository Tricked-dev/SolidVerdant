package dev.tricked.solidverdant.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.Tag
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.LocalDate
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
    tags: List<Tag> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var monthExpanded by remember { mutableStateOf(true) }
    val timelineInitialScroll = with(LocalDensity.current) { (48.dp * 8).roundToPx() }
    val timelineScrollState = rememberScrollState(initial = timelineInitialScroll)
    Column(modifier = modifier.fillMaxWidth().padding(12.dp)) {
        if (!monthExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { monthExpanded = true }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
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
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_previous_month))
            }
            Text(
                text = "${state.visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${state.visibleMonth.year}",
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next_month))
            }
            }

            if (state.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
            }

        // Grid
            val weeks = monthGridWeeks(state.visibleMonth)
            val maxSeconds = state.bucketsByDate.values.maxOfOrNull { it.totalSeconds } ?: 1L
            weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val bucket = state.bucketsByDate[day]
                    val inMonth = day.month == state.visibleMonth.month
                    val selected = day == state.selectedDate
                    val intensity = ((bucket?.totalSeconds ?: 0L).toFloat() / maxSeconds).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f + 0.55f * intensity)
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
                            color = if (inMonth) Color.Unspecified
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
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
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
        }
        if (entries.isEmpty()) {
            Text(
                if (state.isLoading) "Loading entries…" else "No entries",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (!monthExpanded) {
            DayTimeline(
                day = state.selectedDate,
                entries = entries,
                projects = projects,
                tasks = tasks,
                tags = tags,
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
                        tags = tags,
                        scrollState = timelineScrollState,
                        onEntryClick = onEntryClick,
                    )
                }
            }
        }
    }
}

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.Tag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val MIN_HEIGHT_FRACTION = 0.02f
private val HOUR_HEIGHT = 48.dp

fun timelineOffsets(
    entry: TimeEntry,
    day: LocalDate,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<Float, Float> {
    val dayStart = day.atStartOfDay(zone).toInstant()
    val secondsInDay = 86_400f
    val start = try {
        ZonedDateTime.parse(entry.start, DateTimeFormatter.ISO_DATE_TIME).toInstant()
    } catch (_: Exception) { dayStart }
    val end = entry.end?.let {
        try { ZonedDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME).toInstant() } catch (_: Exception) { now }
    } ?: now
    val topSec = (start.epochSecond - dayStart.epochSecond).coerceIn(0, 86_400)
    val endSec = (end.epochSecond - dayStart.epochSecond).coerceIn(0, 86_400)
    val top = topSec / secondsInDay
    val height = ((endSec - topSec) / secondsInDay).coerceAtLeast(MIN_HEIGHT_FRACTION)
    return top to height
}

@Composable
fun DayTimeline(
    day: LocalDate,
    entries: List<TimeEntry>,
    projects: List<Project>,
    tasks: List<Task>,
    tags: List<Tag>,
    onEntryClick: (TimeEntry) -> Unit,
    scrollState: ScrollState? = null,
    fillViewport: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val totalHeight = HOUR_HEIGHT * 24
    val now = Instant.now()
    val initialScroll = with(LocalDensity.current) { (HOUR_HEIGHT * 8).roundToPx() }
    val effectiveScrollState = scrollState ?: rememberScrollState(initial = initialScroll)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillViewport) Modifier.fillMaxHeight() else Modifier.height(520.dp))
            .verticalScroll(effectiveScrollState)
    ) {
      Box(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
        for (hour in 0 until 24) {
            Text(
                text = "%02d:00".format(hour),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(y = HOUR_HEIGHT * hour).width(48.dp),
            )
            Spacer(
                modifier = Modifier
                    .offset(x = 52.dp, y = HOUR_HEIGHT * hour + 8.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            )
        }
        entries.forEach { entry ->
            val project = projects.find { it.id == entry.projectId }
            val task = tasks.find { it.id == entry.taskId }
            val tagNames = entry.tags.mapNotNull { entryTag ->
                tags.find { it.id == entryTag.id }?.name?.ifBlank { null }
                    ?: entryTag.name.ifBlank { null }
            }
            val (top, height) = timelineOffsets(entry, day, now)
            val blockHeight = (totalHeight * height).coerceAtLeast(34.dp)
            val blockColor = project?.color?.let {
                runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
            } ?: MaterialTheme.colorScheme.primary
            Row(
                modifier = Modifier
                    .padding(start = 56.dp, end = 2.dp)
                    .fillMaxWidth()
                    .offset(y = totalHeight * top)
                    .height(blockHeight)
                    .clip(MaterialTheme.shapes.small)
                    .background(blockColor.copy(alpha = 0.22f))
                    .clickable { onEntryClick(entry) }
                    .testTag("entry-row-${entry.id}")
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(4.dp).height(24.dp).background(blockColor))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.description?.ifBlank { "No description" } ?: "No description",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (project != null || task != null) {
                        Text(
                            listOfNotNull(project?.name, task?.name).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (blockHeight >= 72.dp && tagNames.isNotEmpty()) {
                        Text(
                            tagNames.joinToString("  #", prefix = "#"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    formatDuration(entryDurationSeconds(entry, now)),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
      }
    }
}

@Composable
fun TimelineCalendarView(
    state: CalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
    onEntryClick: (TimeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val day = state.selectedDate
    val entries = state.bucketsByDate[day]?.entries ?: emptyList()
    val now = Instant.now()
    val totalHeight = 1440.dp // 1dp per minute of a 24h day
    Box(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .padding(start = 48.dp),
        ) {
            // hour gridlines + labels
            for (hour in 0..24) {
                Text(
                    text = "%02d:00".format(hour % 24),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.offset(x = (-48).dp, y = (hour * 60).dp),
                )
            }
            entries.forEach { entry ->
                val (top, height) = timelineOffsets(entry, day, now)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = totalHeight * top)
                        .height(totalHeight * height)
                        .padding(end = 4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onEntryClick(entry) }
                        .testTag("timeline-entry-${entry.id}")
                        .padding(6.dp),
                ) {
                    Text(
                        entry.description?.ifBlank { "(no description)" } ?: "(no description)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

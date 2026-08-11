/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.ui.tracking.EntryTrustRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Adds one direct move gesture to a rendered entry block. The completed entry follows the pointer
 * while dragging; the drop position is snapped to the calendar grid and the complete interval is
 * preserved through the caller's Room/outbox mutation path. Taps remain available to the caller's
 * normal click modifier.
 */
@Composable
internal fun calendarEntryDragModifier(
    modifier: Modifier,
    entry: TimeEntry,
    day: LocalDate,
    zone: ZoneId,
    settings: CalendarGridSettings = CalendarGridSettings(),
    dayIndex: Int,
    dayCount: Int,
    blockStartFraction: Float,
    blockHeightPx: Float,
    gridHeightPx: Float,
    columnWidthPx: Float,
    onMoveEntry: (TimeEntry, String, String) -> Unit,
): Modifier {
    val canMove = entry.end != null && entryStartDate(entry, zone) == day
    if (!canMove || columnWidthPx <= 0f || gridHeightPx <= 0f) return modifier

    val onMoveEntryState by rememberUpdatedState(onMoveEntry)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val baseTopPx = blockStartFraction * gridHeightPx
    var dragOffset by remember(entry.id, day) { mutableStateOf(Offset.Zero) }
    var isDragging by remember(entry.id, day) { mutableStateOf(false) }

    return modifier
        .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
        .height(with(density) { blockHeightPx.toDp() })
        .zIndex(if (isDragging) DRAGGED_ENTRY_Z_INDEX else 0f)
        .graphicsLayer { alpha = if (isDragging) DRAGGED_ENTRY_ALPHA else 1f }
        .pointerInput(entry.id, day, dayIndex, dayCount, gridHeightPx, columnWidthPx, blockHeightPx, settings) {
            var totalDrag = Offset.Zero
            fun reset() {
                totalDrag = Offset.Zero
                dragOffset = Offset.Zero
                isDragging = false
            }
            fun dispatchMove() {
                val targetDayIndex = (dayIndex + (totalDrag.x / columnWidthPx).roundToInt())
                    .coerceIn(0, (dayCount - 1).coerceAtLeast(0))
                val targetDay = day.plusDays((targetDayIndex - dayIndex).toLong())
                val targetStart = calendarTimeAtGridPosition(
                    day = targetDay,
                    y = baseTopPx + totalDrag.y,
                    gridHeightPx = gridHeightPx,
                    zone = zone,
                    settings = settings,
                )
                calendarEntryRangeAt(entry, targetStart)?.let { range ->
                    dispatchIfChanged(entry, range.start, range.end, onMoveEntryState)
                }
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val displacement = change.position - down.position
                    if (displacement.getDistance() > viewConfiguration.touchSlop) {
                        isDragging = true
                    }
                    if (displacement != Offset.Zero) {
                        change.consume()
                    }
                    if (isDragging) {
                        totalDrag = displacement
                        dragOffset = displacement
                    }
                    if (!change.pressed) {
                        if (isDragging) dispatchMove()
                        reset()
                        break
                    }
                }
            }
        }
}

/**
 * Local overlap warning for a moved entry. This is deliberately advisory: Solidtime remains the
 * authority for whether overlapping tracked time is allowed, but a drag should surface the same
 * useful warning as the editor before the optimistic Room/outbox mutation is sent.
 */
internal fun calendarMoveOverlapsExisting(
    entry: TimeEntry,
    start: String,
    end: String,
    existingEntries: Iterable<TimeEntry>,
    now: Instant = Instant.now(),
): Boolean {
    val moved = entry.copy(start = start, end = end)
    return existingEntries.any { candidate -> EntryTrustRules.overlaps(moved, candidate, now) }
}

private fun dispatchIfChanged(
    entry: TimeEntry,
    start: java.time.ZonedDateTime,
    end: java.time.ZonedDateTime,
    callback: (TimeEntry, String, String) -> Unit,
) {
    val formattedStart = start.format(ENTRY_TIME_FORMATTER)
    val formattedEnd = end.format(ENTRY_TIME_FORMATTER)
    if (formattedStart != entry.start || formattedEnd != entry.end) callback(entry, formattedStart, formattedEnd)
}

private fun entryStartDate(entry: TimeEntry, zone: ZoneId): LocalDate? =
    runCatching { java.time.ZonedDateTime.parse(entry.start).withZoneSameInstant(zone).toLocalDate() }.getOrNull()

private val ENTRY_TIME_FORMATTER = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
private const val DRAGGED_ENTRY_ALPHA = 0.72f
private const val DRAGGED_ENTRY_Z_INDEX = 2f

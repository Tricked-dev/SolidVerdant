/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.foundation.gestures.detectDragGestures
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
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Adds direct manipulation to a rendered entry block. A completed entry follows the pointer while
 * dragging; the drop position is snapped to the calendar grid and the full duration is preserved.
 * Taps remain available to the caller's normal click modifier.
 */
@Composable
internal fun calendarEntryDragModifier(
    modifier: Modifier,
    entry: TimeEntry,
    day: LocalDate,
    zone: ZoneId,
    dayIndex: Int,
    dayCount: Int,
    blockStartFraction: Float,
    gridHeightPx: Float,
    columnWidthPx: Float,
    onMoveEntry: (TimeEntry, String, String) -> Unit,
): Modifier {
    val canMove = entry.end != null && entryStartDate(entry, zone) == day
    if (!canMove || columnWidthPx <= 0f || gridHeightPx <= 0f) return modifier

    val onMoveEntryState by rememberUpdatedState(onMoveEntry)
    var dragOffset by remember(entry.id, day) { mutableStateOf(Offset.Zero) }
    var isDragging by remember(entry.id, day) { mutableStateOf(false) }
    val baseTopPx = blockStartFraction * gridHeightPx

    return modifier
        .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
        .zIndex(if (isDragging) DRAGGED_ENTRY_Z_INDEX else 0f)
        .graphicsLayer { alpha = if (isDragging) DRAGGED_ENTRY_ALPHA else 1f }
        .pointerInput(entry.id, day, dayIndex, dayCount, gridHeightPx, columnWidthPx) {
            var totalDrag = Offset.Zero
            detectDragGestures(
                onDragStart = {
                    totalDrag = Offset.Zero
                    dragOffset = Offset.Zero
                    isDragging = true
                },
                onDrag = { change, amount ->
                    change.consume()
                    totalDrag += amount
                    dragOffset = totalDrag
                },
                onDragEnd = {
                    val targetDayIndex = (dayIndex + (totalDrag.x / columnWidthPx).roundToInt())
                        .coerceIn(0, (dayCount - 1).coerceAtLeast(0))
                    val targetDay = day.plusDays((targetDayIndex - dayIndex).toLong())
                    val targetStart = calendarTimeAtGridPosition(
                        day = targetDay,
                        y = baseTopPx + totalDrag.y,
                        gridHeightPx = gridHeightPx,
                        zone = zone,
                    )
                    calendarEntryRangeAt(entry, targetStart)?.let { range ->
                        val start = range.start.format(ENTRY_TIME_FORMATTER)
                        val end = range.end.format(ENTRY_TIME_FORMATTER)
                        if (start != entry.start || end != entry.end) {
                            onMoveEntryState(entry, start, end)
                        }
                    }
                    totalDrag = Offset.Zero
                    dragOffset = Offset.Zero
                    isDragging = false
                },
                onDragCancel = {
                    totalDrag = Offset.Zero
                    dragOffset = Offset.Zero
                    isDragging = false
                },
            )
        }
}

private fun entryStartDate(entry: TimeEntry, zone: ZoneId): LocalDate? =
    runCatching { java.time.ZonedDateTime.parse(entry.start).withZoneSameInstant(zone).toLocalDate() }.getOrNull()

private val ENTRY_TIME_FORMATTER = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
private const val DRAGGED_ENTRY_ALPHA = 0.72f
private const val DRAGGED_ENTRY_Z_INDEX = 2f

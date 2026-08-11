/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.foundation.gestures.detectDragGestures
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
import dev.tricked.solidverdant.ui.theme.Dimens
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Adds direct move and edge-resize gestures to a rendered entry block. The completed entry follows
 * the pointer while dragging; the drop position is snapped to the calendar grid and the complete
 * interval is preserved or resized through the caller's Room/outbox mutation path. Taps remain
 * available to the caller's normal click modifier.
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
    onResizeEntry: (TimeEntry, String, String) -> Unit = onMoveEntry,
): Modifier {
    val canMove = entry.end != null && entryStartDate(entry, zone) == day
    val canResizeStart = entry.end != null && entryStartDate(entry, zone) == day
    val canResizeEnd = entry.end != null && entryEndDate(entry, zone) == day
    if ((!canMove && !canResizeStart && !canResizeEnd) || columnWidthPx <= 0f || gridHeightPx <= 0f) return modifier

    val onMoveEntryState by rememberUpdatedState(onMoveEntry)
    val onResizeEntryState by rememberUpdatedState(onResizeEntry)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val resizeHandleHeightPx = with(density) { Dimens.Space16.toPx() }
    val gridSeconds = calendarGridBounds(day, zone, settings).seconds
    val minimumHeightPx = max(
        with(density) { Dimens.EntryMinHeight.toPx() },
        gridHeightPx * (settings.normalized().snapMinutes * SECONDS_PER_MINUTE).toFloat() / gridSeconds,
    )
    val baseTopPx = blockStartFraction * gridHeightPx
    var dragOffset by remember(entry.id, day) { mutableStateOf(Offset.Zero) }
    var resizeBoundaryDelta by remember(entry.id, day) { mutableStateOf(0f) }
    var manipulationMode by remember(entry.id, day) { mutableStateOf(ManipulationMode.NONE) }
    var isDragging by remember(entry.id, day) { mutableStateOf(false) }

    val renderedOffsetY = when (manipulationMode) {
        ManipulationMode.RESIZE_START -> resizeBoundaryDelta
        else -> dragOffset.y
    }
    val renderedHeightPx = when (manipulationMode) {
        ManipulationMode.RESIZE_START -> blockHeightPx - resizeBoundaryDelta
        ManipulationMode.RESIZE_END -> blockHeightPx + resizeBoundaryDelta
        else -> blockHeightPx
    }.coerceAtLeast(minimumHeightPx)

    return modifier
        .offset { IntOffset(dragOffset.x.roundToInt(), renderedOffsetY.roundToInt()) }
        .height(with(density) { renderedHeightPx.toDp() })
        .zIndex(if (isDragging) DRAGGED_ENTRY_Z_INDEX else 0f)
        .graphicsLayer { alpha = if (isDragging) DRAGGED_ENTRY_ALPHA else 1f }
        .pointerInput(entry.id, day, dayIndex, dayCount, gridHeightPx, columnWidthPx, blockHeightPx, settings) {
            var totalDrag = Offset.Zero
            fun reset() {
                totalDrag = Offset.Zero
                dragOffset = Offset.Zero
                resizeBoundaryDelta = 0f
                manipulationMode = ManipulationMode.NONE
                isDragging = false
            }
            detectDragGestures(
                onDragStart = { offset ->
                    totalDrag = Offset.Zero
                    dragOffset = Offset.Zero
                    resizeBoundaryDelta = 0f
                    manipulationMode = when {
                        canResizeStart && isNearTop(offset.y, blockHeightPx, resizeHandleHeightPx) ->
                            ManipulationMode.RESIZE_START
                        canResizeEnd && isNearBottom(offset.y, blockHeightPx, resizeHandleHeightPx) ->
                            ManipulationMode.RESIZE_END
                        canMove -> ManipulationMode.MOVE
                        else -> ManipulationMode.NONE
                    }
                    isDragging = manipulationMode != ManipulationMode.NONE
                },
                onDrag = { change, amount ->
                    if (manipulationMode == ManipulationMode.NONE) return@detectDragGestures
                    change.consume()
                    totalDrag += amount
                    dragOffset = totalDrag
                    resizeBoundaryDelta = when (manipulationMode) {
                        ManipulationMode.RESIZE_START -> totalDrag.y.coerceAtMost(blockHeightPx - minimumHeightPx)
                        ManipulationMode.RESIZE_END -> totalDrag.y.coerceAtLeast(minimumHeightPx - blockHeightPx)
                        ManipulationMode.MOVE, ManipulationMode.NONE -> 0f
                    }
                },
                onDragEnd = {
                    val mode = manipulationMode
                    if (mode != ManipulationMode.NONE) {
                        val targetDayIndex = (dayIndex + (totalDrag.x / columnWidthPx).roundToInt())
                            .coerceIn(0, (dayCount - 1).coerceAtLeast(0))
                        val targetDay = day.plusDays((targetDayIndex - dayIndex).toLong())
                        when (mode) {
                            ManipulationMode.MOVE -> {
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

                            ManipulationMode.RESIZE_START,
                            ManipulationMode.RESIZE_END,
                            -> {
                                val targetBoundary = calendarTimeAtGridPosition(
                                    day = targetDay,
                                    y = if (mode == ManipulationMode.RESIZE_START) {
                                        baseTopPx + resizeBoundaryDelta
                                    } else {
                                        baseTopPx + blockHeightPx + resizeBoundaryDelta
                                    },
                                    gridHeightPx = gridHeightPx,
                                    zone = zone,
                                    allowDayEnd = mode == ManipulationMode.RESIZE_END,
                                    settings = settings,
                                )
                                calendarEntryResizeRangeAt(
                                    entry = entry,
                                    edge = if (mode == ManipulationMode.RESIZE_START) {
                                        CalendarResizeEdge.START
                                    } else {
                                        CalendarResizeEdge.END
                                    },
                                    boundary = targetBoundary,
                                    settings = settings,
                                )?.let { range ->
                                    dispatchIfChanged(entry, range.start, range.end, onResizeEntryState)
                                }
                            }

                            ManipulationMode.NONE -> Unit
                        }
                    }
                    reset()
                },
                onDragCancel = { reset() },
            )
        }
}

private enum class ManipulationMode { NONE, MOVE, RESIZE_START, RESIZE_END }

private fun isNearTop(y: Float, height: Float, handleHeight: Float): Boolean = y <= minOf(handleHeight, height / 2f)

private fun isNearBottom(y: Float, height: Float, handleHeight: Float): Boolean = y >= maxOf(height - handleHeight, height / 2f)

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

private fun entryEndDate(entry: TimeEntry, zone: ZoneId): LocalDate? =
    entry.end?.let { runCatching { java.time.ZonedDateTime.parse(it).withZoneSameInstant(zone).toLocalDate() }.getOrNull() }

private val ENTRY_TIME_FORMATTER = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
private const val DRAGGED_ENTRY_ALPHA = 0.72f
private const val DRAGGED_ENTRY_Z_INDEX = 2f
private const val SECONDS_PER_MINUTE = 60L

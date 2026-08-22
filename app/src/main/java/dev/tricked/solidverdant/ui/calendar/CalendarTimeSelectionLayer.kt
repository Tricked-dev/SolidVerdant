/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.theme.Dimens
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Transparent input layer for selecting a new entry by long-pressing and dragging in an empty
 * time grid. An ordinary vertical drag remains available to the parent calendar scroller. It is
 * placed behind tracked entry blocks, so tapping an existing entry still opens its editor.
 */
@Composable
internal fun CalendarTimeSelectionLayer(
    day: LocalDate,
    zone: ZoneId,
    settings: CalendarGridSettings = CalendarGridSettings(),
    onSelectionComplete: (CalendarTimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val gridHeightPx = with(density) { calendarTotalHeight(settings).toPx() }
    val onSelectionCompleteState by rememberUpdatedState(onSelectionComplete)
    var dragStartY by remember(day, zone) { mutableStateOf<Float?>(null) }
    var dragCurrentY by remember(day, zone) { mutableStateOf<Float?>(null) }
    val dragStart = dragStartY
    val dragCurrent = dragCurrentY
    val selectionColor = MaterialTheme.colorScheme.primary
    val dragHint = stringResource(R.string.calendar_drag_to_create)

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(CalendarTestTags.selection(day))
            .semantics { contentDescription = dragHint }
            .pointerInput(day, zone, gridHeightPx, settings) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        dragStartY = offset.y
                        dragCurrentY = offset.y
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragCurrentY = (dragCurrentY ?: dragStartY ?: 0f) + dragAmount.y
                    },
                    onDragEnd = {
                        val start = dragStartY
                        val current = dragCurrentY
                        if (start != null && current != null) {
                            onSelectionCompleteState(
                                calendarTimeRangeForDrag(
                                    day = day,
                                    startY = start,
                                    endY = current,
                                    gridHeightPx = gridHeightPx,
                                    zone = zone,
                                    settings = settings,
                                ),
                            )
                        }
                        dragStartY = null
                        dragCurrentY = null
                    },
                    onDragCancel = {
                        dragStartY = null
                        dragCurrentY = null
                    },
                )
            }
            .pointerInput(day, zone, gridHeightPx, settings) {
                detectTapGestures(
                    onTap = { offset ->
                        onSelectionCompleteState(
                            calendarTimeRangeForDrag(
                                day = day,
                                startY = offset.y,
                                endY = offset.y,
                                gridHeightPx = gridHeightPx,
                                zone = zone,
                                settings = settings,
                            ),
                        )
                    },
                )
            },
    ) {
        if (dragStart != null && dragCurrent != null) {
            val top = min(dragStart, dragCurrent)
            val height = max(abs(dragCurrent - dragStart), with(density) { Dimens.EntryMinHeight.toPx() })
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = with(density) { top.toDp() })
                    .height(with(density) { height.toDp() })
                    .background(selectionColor.copy(alpha = SELECTION_ALPHA))
                    .border(
                        width = Dimens.Space1,
                        color = selectionColor,
                    ),
            )
        }
    }
}

private const val SELECTION_ALPHA = 0.24f

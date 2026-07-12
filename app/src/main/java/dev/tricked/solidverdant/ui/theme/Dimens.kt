/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Shared dp scale and layout constants for the SolidVerdant UI kit.
 *
 * The values are measured from the canonical screens rather than invented:
 *  - Statistics KPI / donut / trend cards use 16.dp content padding and 8.dp
 *    inner vertical gaps ([CardContentPadding], [CardContentGap]).
 *  - The month/day calendar entry blocks clip to [MaterialTheme.shapes.small]
 *    (8.dp), draw a 4.dp coloured side bar with an 8.dp gap, and are inset with
 *    8.dp horizontal / 4.dp vertical padding
 *    ([CornerRadius], [EntryBarWidth], [EntryBarGap], [EntryPaddingHorizontal],
 *    [EntryPaddingVertical]).
 *  - Calendar day entries coerce to a minimum block height of 34.dp
 *    ([EntryMinHeight]).
 */
object Dimens {
    // --- Core dp spacing scale ---
    val Space1 = 1.dp
    val Space2 = 2.dp
    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space24 = 24.dp
    val Space32 = 32.dp

    // --- Corner radius (matches MaterialTheme.shapes.small used by calendar blocks) ---
    val CornerRadius = 8.dp

    // --- Card content metrics (measured from KPI / donut / trend cards) ---
    val CardContentPadding = 16.dp
    val CardContentGap = 8.dp

    // --- Calendar entry-block metrics (measured from Day/Month calendar) ---
    val EntryMinHeight = 34.dp
    val EntryBarWidth = 4.dp
    val EntryBarHeight = 24.dp
    val EntryBarGap = 8.dp
    val EntryPaddingHorizontal = 8.dp
    val EntryPaddingVertical = 4.dp

    // --- Accessibility ---
    val MinTouchTarget = 48.dp
    val NarrowCalendarWidth = 600.dp
}

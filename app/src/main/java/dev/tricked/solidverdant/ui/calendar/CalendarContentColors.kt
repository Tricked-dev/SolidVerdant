/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.ui.graphics.Color

internal fun calendarDayContentColor(
    selected: Boolean,
    isToday: Boolean,
    primary: Color,
    onPrimaryContainer: Color,
    default: Color,
): Color = when {
    selected -> onPrimaryContainer
    isToday -> primary
    else -> default
}

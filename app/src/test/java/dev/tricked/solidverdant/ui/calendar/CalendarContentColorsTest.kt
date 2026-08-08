/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarContentColorsTest {
    @Test
    fun `selected day uses the content color paired with its container`() {
        val foreground = calendarDayContentColor(
            selected = true,
            isToday = false,
            primary = Color.Blue,
            onPrimaryContainer = Color.Black,
            default = Color.LightGray,
        )

        assertEquals(Color.Black, foreground)
    }

    @Test
    fun `selected today also prioritizes the selected container content color`() {
        val foreground = calendarDayContentColor(
            selected = true,
            isToday = true,
            primary = Color.Blue,
            onPrimaryContainer = Color.Black,
            default = Color.LightGray,
        )

        assertEquals(Color.Black, foreground)
    }
}

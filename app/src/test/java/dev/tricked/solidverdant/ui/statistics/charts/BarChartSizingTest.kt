/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarChartSizingTest {

    @Test
    fun smallCountLeavesPositiveBarsAndGaps() {
        val s = barChartSizing(width = 1000f, count = 7)
        assertTrue(s.gap > 0f)
        assertTrue(s.barWidth > 0f)
    }

    @Test
    fun largeCountNeverProducesNegativeOrZeroBarWidth() {
        // WEEK granularity over a multi-year custom range can yield hundreds of bars. The old
        // formula (gap = width * 0.02; barWidth = (width - gap*(n+1))/n) went negative for n >= 49.
        listOf(50, 100, 200, 520, 2000).forEach { n ->
            val s = barChartSizing(width = 1080f, count = n)
            assertTrue("barWidth must stay positive for n=$n but was ${s.barWidth}", s.barWidth > 0f)
            assertTrue("gap must stay non-negative for n=$n", s.gap >= 0f)
        }
    }

    @Test
    fun barsKeepAtLeastHalfTheWidth() {
        // The gap budget is capped so the bars themselves always retain at least half the canvas.
        val width = 1000f
        val n = 300
        val s = barChartSizing(width, n)
        val totalGap = s.gap * (n + 1)
        assertTrue("gaps should not exceed half the width", totalGap <= width * 0.5f + 0.001f)
    }

    @Test
    fun degenerateInputsReturnZeroSizing() {
        assertEquals(BarChartSizing(0f, 0f), barChartSizing(width = 0f, count = 5))
        assertEquals(BarChartSizing(0f, 0f), barChartSizing(width = 100f, count = 0))
    }
}

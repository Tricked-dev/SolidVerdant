/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatisticsFetchPlanTest {
    @Test
    fun `server query does not exclude entries that started before the selected range`() {
        val range = LocalDate.of(2026, 7, 6)..LocalDate.of(2026, 7, 12)

        assertNull(statisticsFetchStart(range, ZoneOffset.UTC))
    }
}

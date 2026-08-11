/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import org.junit.Assert.assertNull
import org.junit.Test

class StatisticsFetchPlanTest {
    @Test
    fun `server query does not exclude entries that started before the selected range`() {
        assertNull(statisticsFetchStart)
    }
}

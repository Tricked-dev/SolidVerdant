/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatisticsRangeSelectorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rangeOptionsUseOneSelectorAndUpdateItsVisibleValue() {
        val selected = AtomicReference<StatRange>(StatRange.Today)
        composeRule.setContent {
            var range by remember { mutableStateOf<StatRange>(StatRange.Today) }
            MaterialTheme {
                RangeSelector(
                    current = range,
                    onSelect = {
                        range = it
                        selected.set(it)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("stats_range_selector").performClick()
        composeRule.onNodeWithTag("stats_range_option_ThisMonth").performClick()

        assertEquals(StatRange.ThisMonth, selected.get())
    }
}

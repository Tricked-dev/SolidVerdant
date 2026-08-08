/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SearchFilterE2eTest {
    @get:Rule
    val e2e = E2eRule(this)

    @Test
    fun wideSearchControlExpandsInlineAndClosePreservesTheQuery() {
        e2e.requireMockBackend().presetLoggedInWorld()
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory()

        assertTrue("Collapsed search control should span most of history", robot.historyFilterOpenWidthRatio() >= 0.8f)
        robot.openHistoryFilters()
            .enterHistorySearch("Seeded")
            .closeHistoryFilters()
            .openHistoryFilters()
            .assertHistorySearch("Seeded")
    }
}

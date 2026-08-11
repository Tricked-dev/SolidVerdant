/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.flows

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarPrefetchE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @Test
    fun opening_calendar_fetches_the_visible_and_adjacent_periods() {
        val server = e2e.requireMockBackend()
        e2e.prepare(E2eFixture.Empty)
        e2e.launchApp()

        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntil(WAIT_MS) { calendarMonthRequests(server).size >= EXPECTED_MONTH_REQUESTS }

        val monthEnds = calendarMonthRequests(server).mapNotNull { call ->
            END_QUERY.find(call.path)?.groupValues?.get(1)
        }.toSet()
        assertTrue(
            "Expected distinct visible and adjacent month requests, saw: ${calendarMonthRequests(server).map { it.path }}",
            monthEnds.size >= EXPECTED_MONTH_REQUESTS,
        )
    }

    private fun calendarMonthRequests(server: dev.tricked.solidverdant.e2e.mock.MockSolidtimeServer) =
        server.callsMatching("GET", "/time-entries").filter { END_QUERY.containsMatchIn(it.path) }

    companion object {
        private const val EXPECTED_MONTH_REQUESTS = 3
        private const val WAIT_MS = 15_000L
        private val END_QUERY = Regex("""(?:\?|&)end=([^&]+)""")
    }
}

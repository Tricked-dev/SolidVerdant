/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import org.junit.Rule
import org.junit.Test

class MainNavHostTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun tappingCalendarTabShowsCalendarContent() {
        composeRule.setContent {
            val nav = rememberNavController()
            MainNavHost(
                navController = nav,
                trackContent = { Text("TRACK_CONTENT") },
                calendarContent = { Text("CALENDAR_CONTENT") },
                statsContent = { Text("STATS_CONTENT") },
            )
        }
        composeRule.onNodeWithText("TRACK_CONTENT").assertIsDisplayed()
        composeRule.onNodeWithText("Calendar").performClick()
        composeRule.onNodeWithText("CALENDAR_CONTENT").assertIsDisplayed()
    }
}

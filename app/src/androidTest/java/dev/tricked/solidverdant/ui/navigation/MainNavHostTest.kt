/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
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

    @Test
    fun tappingTrackFromSyncCenterShowsTrackContent() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            MainNavHost(
                navController = navController,
                trackContent = { Text("TRACK_CONTENT") },
                calendarContent = { Text("CALENDAR_CONTENT") },
                statsContent = { Text("STATS_CONTENT") },
                syncCenterContent = { Text("SYNC_CENTER_CONTENT") },
            )
        }
        composeRule.runOnIdle {
            navController.navigate(SyncRoutes.SYNC_CENTER)
        }
        composeRule.onNodeWithText("SYNC_CENTER_CONTENT").assertIsDisplayed()
        composeRule.onNode(hasTestTag("main_nav_track")).assertIsNotSelected()

        composeRule.onNode(hasTestTag("main_nav_track")).performClick()

        composeRule.onNodeWithText("TRACK_CONTENT").assertIsDisplayed()
        composeRule.onNodeWithText("SYNC_CENTER_CONTENT").assertDoesNotExist()
    }
}

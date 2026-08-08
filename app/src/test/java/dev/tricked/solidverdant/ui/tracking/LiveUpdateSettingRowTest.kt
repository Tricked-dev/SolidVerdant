/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveUpdateSettingRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enabled_setting_explains_android_fallback_and_opens_system_settings() {
        var systemSettingsOpened = false
        composeRule.setContent {
            MaterialTheme {
                LiveUpdateSettingRow(
                    enabled = true,
                    systemEnabled = false,
                    onEnabledChange = {},
                    onOpenSystemSettings = { systemSettingsOpened = true },
                )
            }
        }

        composeRule.onNodeWithText("Live timer updates").assertExists()
        composeRule.onNodeWithText("Android Live Updates are disabled for this app. The timer will still use a regular notification.")
            .assertExists()
        composeRule.onNodeWithText("Open Android Live Update settings").performClick()
        assertTrue(systemSettingsOpened)
    }

    @Test
    fun disabled_setting_hides_system_fallback_action() {
        composeRule.setContent {
            MaterialTheme {
                LiveUpdateSettingRow(
                    enabled = false,
                    systemEnabled = false,
                    onEnabledChange = {},
                    onOpenSystemSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Open Android Live Update settings").assertDoesNotExist()
        composeRule.onNodeWithTag(TrackingTestTags.LIVE_UPDATE_SWITCH).assertExists()
    }
}

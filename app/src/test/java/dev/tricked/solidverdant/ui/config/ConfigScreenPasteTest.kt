/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.config

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.Lifecycle
import dev.tricked.solidverdant.ui.auth.OAuthConfigState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** B6: the server sheet never overwrites what was typed with the clipboard; pasting is a tap. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigScreenPasteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var saved: Pair<String, String>? = null
    private var dismissed = 0

    private fun showSheetWithClipboard(text: String) {
        val clipboard = composeRule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("config", text))
        composeRule.setContent {
            MaterialTheme {
                ConfigScreen(
                    configState = OAuthConfigState(endpoint = "https://saved.example", clientId = "saved-client"),
                    onSave = { endpoint, clientId -> saved = endpoint to clientId },
                    onReset = {},
                    onTestConnection = { _, _ -> },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    @Test
    fun opening_and_resuming_keep_the_typed_endpoint() {
        showSheetWithClipboard("https://clip.example:$CLIP_CLIENT")

        composeRule.onNodeWithTag(ConfigTestTags.ENDPOINT_FIELD).assert(hasText("https://saved.example"))
        composeRule.onNodeWithTag(ConfigTestTags.ENDPOINT_FIELD).performTextReplacement("https://typed.example")

        // Leaving for another app (to copy the config) and coming back resumes the sheet.
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ConfigTestTags.ENDPOINT_FIELD).assert(hasText("https://typed.example"))
        composeRule.onNodeWithTag(ConfigTestTags.CLIENT_ID_FIELD).assert(hasText("saved-client"))
    }

    @Test
    fun paste_fills_both_fields_from_a_copied_config() {
        showSheetWithClipboard("https://clip.example:$CLIP_CLIENT")

        composeRule.onNodeWithTag(ConfigTestTags.PASTE).performScrollTo().performClick()

        composeRule.onNodeWithTag(ConfigTestTags.ENDPOINT_FIELD).assert(hasText("https://clip.example"))
        composeRule.onNodeWithTag(ConfigTestTags.CLIENT_ID_FIELD).assert(hasText(CLIP_CLIENT))
        composeRule.onNodeWithTag(ConfigTestTags.SAVE).performClick()
        assertEquals("https://clip.example" to CLIP_CLIENT, saved)
    }

    @Test
    fun pasting_unrelated_text_changes_nothing() {
        showSheetWithClipboard("shopping list")

        composeRule.onNodeWithTag(ConfigTestTags.PASTE).performScrollTo().performClick()

        composeRule.onNodeWithTag(ConfigTestTags.ENDPOINT_FIELD).assert(hasText("https://saved.example"))
        composeRule.onNodeWithTag(ConfigTestTags.CLIENT_ID_FIELD).assert(hasText("saved-client"))
    }

    @Test
    fun swiping_down_inside_the_form_does_not_dismiss_the_sheet() {
        showSheetWithClipboard("https://clip.example:$CLIP_CLIENT")

        composeRule.onNodeWithTag(ConfigTestTags.ENDPOINT_FIELD).performTouchInput { swipeDown(durationMillis = 100) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ConfigTestTags.ENDPOINT_FIELD).assertExists()
        assertEquals(0, dismissed)
    }

    private companion object {
        const val CLIP_CLIENT = "9f3c2a71-5d1e-4c9b-a0f2-1b7e6d4c8a90"
    }
}

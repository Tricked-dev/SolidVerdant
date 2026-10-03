/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A modal sheet dismisses itself from scroll and fling deltas its content leaves unconsumed. The
 * edit form must swallow those so only the drag handle can pull the sheet down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SheetSwipeDismissTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class SheetRecorder : NestedScrollConnection {
        var scrollY = 0f
        var flingY = 0f

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            scrollY += available.y
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            flingY += available.y
            return Velocity.Zero
        }
    }

    private fun swipeDownForm(consumeOverscroll: Boolean): SheetRecorder {
        val sheet = SheetRecorder()
        composeRule.setContent {
            Box(Modifier.fillMaxSize().nestedScroll(sheet)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .testTag(FORM)
                        .then(if (consumeOverscroll) Modifier.nestedScroll(ConsumeVerticalOverscroll) else Modifier)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text("Form", Modifier.height(800.dp))
                }
            }
        }
        composeRule.onNodeWithTag(FORM).performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        return sheet
    }

    @Test
    fun swipeDownAtTopOfFormWouldDragSheetWithoutGuard() {
        val sheet = swipeDownForm(consumeOverscroll = false)

        assertTrue("control: the sheet sees the form's overscroll", sheet.scrollY > 0f)
    }

    @Test
    fun swipeDownAtTopOfFormNeverReachesSheet() {
        val sheet = swipeDownForm(consumeOverscroll = true)

        assertEquals(0f, sheet.scrollY)
        assertEquals(0f, sheet.flingY)
    }

    private companion object {
        const val FORM = "sheet_form"
    }

    @Test
    fun swiping_down_inside_an_app_sheet_does_not_dismiss_it() {
        var dismissed = 0
        composeRule.setContent {
            MaterialTheme {
                AppSheet(title = "Options", onDismiss = { dismissed++ }) {
                    Text("Body", Modifier.fillMaxWidth().height(200.dp).testTag("body"))
                }
            }
        }

        composeRule.onNodeWithTag("body").performTouchInput { swipeDown(durationMillis = 100) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body").assertExists()
        assertEquals(0, dismissed)
    }
}

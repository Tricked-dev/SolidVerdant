/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchableSingleSelectDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeCatalogueCanBeSearchedAndSelectedWithoutScrolling() {
        val selected = AtomicReference<String?>(null)
        composeRule.setContent {
            MaterialTheme {
                SearchableSingleSelectDialog(
                    title = "Project",
                    searchPlaceholder = "Search project",
                    allLabel = "All projects",
                    options = (1..100).map { index -> "project-$index" to "Project $index" },
                    selectedId = null,
                    onSelect = selected::set,
                    onDismiss = {},
                    searchTestTag = "catalogue_search",
                    optionTestTag = { "catalogue_option_$it" },
                )
            }
        }

        composeRule.onNodeWithTag("catalogue_search").performTextInput("Project 87")
        composeRule.onNodeWithTag("catalogue_option_project-1").assertDoesNotExist()
        composeRule.onNodeWithTag("catalogue_option_project-87").performClick()

        assertEquals("project-87", selected.get())
    }
}

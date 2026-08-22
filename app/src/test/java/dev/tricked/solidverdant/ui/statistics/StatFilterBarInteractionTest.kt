/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.tricked.solidverdant.data.model.Project
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatFilterBarInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun projectSearchIsImmediatelyAvailableAndFiltersOptions() {
        val selected = AtomicReference(StatFilters())
        composeRule.setContent {
            MaterialTheme {
                StatFilterBar(
                    filters = selected.get(),
                    catalog = StatCatalog(
                        projects = listOf(
                            Project(id = "matching", name = "Precision milling", color = "#336699"),
                            Project(id = "other", name = "Internal work", color = "#663399"),
                        ),
                    ),
                    onFiltersChange = { selected.set(it) },
                    onClearFilters = { selected.set(StatFilters()) },
                )
            }
        }

        composeRule.onNodeWithTag(StatisticsFilterTestTags.OPEN).performClick()
        composeRule.onNodeWithTag(StatisticsFilterTestTags.PROJECT_SEARCH).performTextInput("precision")

        composeRule.onNodeWithTag(StatisticsFilterTestTags.projectOption("other")).assertDoesNotExist()
        composeRule.onNodeWithTag(StatisticsFilterTestTags.projectOption("matching")).performClick()
        assertEquals(setOf("matching"), selected.get().projectIds)
    }
}

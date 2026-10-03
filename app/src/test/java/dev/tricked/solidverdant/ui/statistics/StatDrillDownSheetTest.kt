/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatDrillDownSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val target = DrillDownTarget.ProjectSlice(projectId = "p1", projectName = "Alpha", colorHex = "#FF0000")

    private fun row(index: Int) = DrillDownRow(
        entryId = "e$index",
        description = "Entry $index",
        projectId = "p1",
        projectName = "Alpha",
        colorHex = "#FF0000",
        taskName = null,
        startDate = LocalDate.parse("2026-07-06"),
        seconds = 600,
        billable = index % 2 == 0,
    )

    private var dismissed = 0

    private fun show(state: DrillDownUiState) {
        composeRule.setContent {
            MaterialTheme { StatDrillDownSheet(state = state, onDismiss = { dismissed++ }) }
        }
    }

    @Test
    fun aBusySliceComposesOnlyTheVisibleRowsAndScrollsToTheRest() {
        val rows = (0 until 300).map(::row)
        show(DrillDownUiState(target = target, isLoading = false, rows = rows, totalSeconds = rows.sumOf { it.seconds }))

        composeRule.onNodeWithText("Alpha").assertExists()
        composeRule.onNodeWithTag(StatDrillDownTestTags.row("e0")).assertExists()
        composeRule.onNodeWithTag(StatDrillDownTestTags.row("e299")).assertDoesNotExist()

        composeRule.onNodeWithTag(StatDrillDownTestTags.LIST).performScrollToNode(hasTestTag(StatDrillDownTestTags.row("e299")))
        composeRule.onNodeWithTag(StatDrillDownTestTags.row("e299")).assertExists()
    }

    @Test
    fun emptySliceShowsTheEmptyLine() {
        show(DrillDownUiState(target = target, isLoading = false))

        composeRule.onNodeWithText("No entries in this selection").assertExists()
    }

    @Test
    fun loadingSliceShowsNoRows() {
        show(DrillDownUiState(target = target, isLoading = true, rows = listOf(row(0))))

        composeRule.onNodeWithTag(StatDrillDownTestTags.row("e0")).assertDoesNotExist()
    }

    @Test
    fun aProjectHistoryNamesWhoLoggedEachEntryAndDatesOlderYears() {
        val history = DrillDownTarget.ProjectHistory(projectId = "p1", projectName = "Job 42", colorHex = "#FF0000")
        val lastYear = LocalDate.now().minusYears(1).withMonth(3).withDayOfMonth(4)
        val rows = listOf(row(0).copy(memberName = "Sylvain", startDate = lastYear))
        show(DrillDownUiState(target = history, isLoading = false, rows = rows, totalSeconds = 600, isRefreshing = true))

        composeRule.onNodeWithText("Job 42").assertExists()
        composeRule.onNodeWithText("Sylvain", substring = true).assertExists()
        composeRule.onNodeWithText(lastYear.year.toString(), substring = true).assertExists()
        composeRule.onNodeWithTag(StatDrillDownTestTags.REFRESHING).assertExists()
    }

    @Test
    fun anUnreachableServerOffersRetryAndOwnOnlyListsSayWhy() {
        var retries = 0
        composeRule.setContent {
            MaterialTheme {
                StatDrillDownSheet(
                    state = DrillDownUiState(
                        target = target,
                        isLoading = false,
                        rows = listOf(row(0)),
                        loadFailed = true,
                        ownEntriesOnly = true,
                    ),
                    onDismiss = {},
                    onRetry = { retries++ },
                )
            }
        }

        composeRule.onNodeWithTag(StatDrillDownTestTags.OWN_ONLY).assertExists()
        composeRule.onNodeWithTag(StatDrillDownTestTags.LOAD_FAILED).assertExists()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        composeRule.onNodeWithTag(StatDrillDownTestTags.row("e0")).assertExists()
    }

    @Test
    fun swipingDownInsideTheListDoesNotDismissTheSheet() {
        show(DrillDownUiState(target = target, isLoading = false, rows = listOf(row(0)), totalSeconds = 600))

        composeRule.onNodeWithTag(StatDrillDownTestTags.LIST).performTouchInput { swipeDown(durationMillis = 100) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(StatDrillDownTestTags.SHEET).assertExists()
        assertEquals(0, dismissed)
    }
}

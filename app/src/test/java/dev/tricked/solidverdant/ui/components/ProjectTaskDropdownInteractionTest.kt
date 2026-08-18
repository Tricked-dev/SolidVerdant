/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectTaskDropdownInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val machining = Project(id = "p1", name = "Machining", color = "#336699")
    private val internal = Project(id = "p2", name = "Internal work", color = "#663399")
    private val turning = task("t1", "Turning", machining.id)
    private val setup = task("t2", "Setup", machining.id)
    private val planning = task("t3", "Planning", internal.id)

    @Test
    fun project_then_task_are_separate_searchable_selections() {
        setContent()

        composeRule.onNodeWithTag(EditTimeEntryTestTags.TASK_SELECTOR).assertIsNotEnabled()
        composeRule.onNodeWithTag(EditTimeEntryTestTags.PROJECT_SELECTOR).performClick()
        composeRule.onNodeWithTag(EditTimeEntryTestTags.PROJECT_SEARCH).performTextInput("  internal  ")
        composeRule.onNodeWithText(internal.name).performClick()

        composeRule.onNodeWithTag(EditTimeEntryTestTags.PROJECT_SELECTOR).assertTextContains(internal.name)
        composeRule.onNodeWithTag(EditTimeEntryTestTags.TASK_SELECTOR).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(EditTimeEntryTestTags.TASK_SEARCH).performTextInput("PLAN")
        composeRule.onNodeWithText(planning.name).performClick()

        composeRule.onNodeWithTag(EditTimeEntryTestTags.TASK_SELECTOR).assertTextContains(planning.name)
    }

    @Test
    fun task_picker_only_composes_tasks_for_the_selected_project() {
        setContent(initialProjectId = machining.id)

        composeRule.onNodeWithTag(EditTimeEntryTestTags.TASK_SELECTOR).performClick()

        composeRule.onNodeWithText(turning.name).assertExists()
        composeRule.onNodeWithText(setup.name).assertExists()
        composeRule.onNodeWithText(planning.name).assertDoesNotExist()
    }

    @Test
    fun changing_project_clears_a_task_from_the_previous_project() {
        setContent(initialProjectId = machining.id, initialTaskId = turning.id)

        composeRule.onNodeWithTag(EditTimeEntryTestTags.PROJECT_SELECTOR).performClick()
        composeRule.onNodeWithText(internal.name).performClick()

        composeRule.onNodeWithTag(EditTimeEntryTestTags.TASK_SELECTOR).assertTextContains("No task")
    }

    private fun setContent(initialProjectId: String? = null, initialTaskId: String? = null) {
        composeRule.setContent {
            var projectId by remember { mutableStateOf(initialProjectId) }
            var taskId by remember { mutableStateOf(initialTaskId) }
            MaterialTheme {
                ProjectTaskDropdown(
                    projects = listOf(machining, internal),
                    tasks = listOf(turning, setup, planning),
                    selectedProjectId = projectId,
                    selectedTaskId = taskId,
                    onSelectionChanged = { selectedProjectId, selectedTaskId ->
                        projectId = selectedProjectId
                        taskId = selectedTaskId
                    },
                    showProjectColors = true,
                    rounded = true,
                )
            }
        }
    }

    private fun task(id: String, name: String, projectId: String) = Task(
        id = id,
        name = name,
        projectId = projectId,
        createdAt = "2026-08-18T00:00:00Z",
        updatedAt = "2026-08-18T00:00:00Z",
    )
}

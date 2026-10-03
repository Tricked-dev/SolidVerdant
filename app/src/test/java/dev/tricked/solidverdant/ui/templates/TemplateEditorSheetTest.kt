/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.templates

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.repository.EntryTemplate
import dev.tricked.solidverdant.ui.components.EditTimeEntryTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TemplateEditorSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tags = listOf(Tag("t1", "Focus"), Tag("t2", "Meeting"))
    private val existing = EntryTemplate("tm1", "org1", "Deep work", null, null, "Focus block", listOf("t1"), false, false, 0, 0L)

    private var saved: TemplateDraft? = null
    private var updated: EntryTemplate? = null
    private var deleted = 0
    private var dismissed = 0

    private fun show(template: EntryTemplate?, deletable: Boolean = true) {
        composeRule.setContent {
            MaterialTheme {
                TemplateEditorSheet(
                    existing = template,
                    projects = emptyList(),
                    tasks = emptyList(),
                    tags = tags,
                    onDismiss = { dismissed++ },
                    onSave = { saved = it },
                    onUpdate = { updated = it },
                    onDelete = if (deletable) ({ deleted++ }) else null,
                )
            }
        }
    }

    @Test
    fun newTemplateSavesNameTagsAndSwitches() {
        show(template = null)

        composeRule.onNodeWithTag(TemplateEditorTestTags.SAVE).assertIsNotEnabled()
        composeRule.onNodeWithTag(TemplateEditorTestTags.DELETE).assertDoesNotExist()
        composeRule.onNodeWithTag(TemplateEditorTestTags.NAME).performTextInput("  Stand-up ")
        composeRule.onNodeWithTag(EditTimeEntryTestTags.TAGS_SELECTOR).performScrollTo().performClick()
        composeRule.onNodeWithTag(EditTimeEntryTestTags.tagChip("t2")).performClick()
        composeRule.onNodeWithTag(EditTimeEntryTestTags.TAGS_CLOSE).performClick()
        composeRule.onNodeWithTag(TemplateEditorTestTags.BILLABLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(TemplateEditorTestTags.FAVORITE).performScrollTo().performClick()
        composeRule.onNodeWithTag(TemplateEditorTestTags.SAVE).performScrollTo().assertIsEnabled().performClick()

        assertEquals(
            TemplateDraft(
                name = "Stand-up",
                projectId = null,
                taskId = null,
                description = null,
                tagIds = listOf("t2"),
                billable = true,
                isFavorite = true,
            ),
            saved,
        )
    }

    @Test
    fun editingKeepsTheTemplateAndUpdatesIt() {
        show(existing)

        composeRule.onNodeWithTag(TemplateEditorTestTags.BILLABLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(TemplateEditorTestTags.SAVE).performScrollTo().performClick()

        assertEquals(existing.copy(billable = true), updated)
        assertNull(saved)
    }

    @Test
    fun deleteAsksForConfirmationFirst() {
        show(existing)

        composeRule.onNodeWithTag(TemplateEditorTestTags.DELETE).performScrollTo().performClick()
        assertEquals(0, deleted)
        composeRule.onNodeWithTag(TemplateEditorTestTags.DELETE_CONFIRM).performClick()

        assertEquals(1, deleted)
    }

    @Test
    fun deleteIsHiddenWithoutAHandler() {
        show(existing, deletable = false)

        composeRule.onNodeWithTag(TemplateEditorTestTags.DELETE).assertDoesNotExist()
    }

    @Test
    fun swipingDownInsideTheFormDoesNotDismissTheSheet() {
        show(template = existing)

        composeRule.onNodeWithTag(TemplateEditorTestTags.NAME).performTouchInput { swipeDown(durationMillis = 100) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TemplateEditorTestTags.SHEET).assertExists()
        assertEquals(0, dismissed)
    }
}

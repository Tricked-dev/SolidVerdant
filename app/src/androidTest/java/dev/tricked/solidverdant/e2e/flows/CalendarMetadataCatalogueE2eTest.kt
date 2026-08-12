/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.flows

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarMetadataCatalogueE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @BackendPortable
    @Test
    fun calendarShowsMetadataAndInlineCatalogueCreationSyncs() {
        e2e.prepare(E2eFixture.Empty)
        val existing = e2e.catalogFixture()
        val metadataEntry = e2e.completedFixtureEntry(
            logicalId = "calendar-metadata-entry",
            description = "Calendar metadata",
            durationSeconds = 3_600,
        ).copy(
            projectId = existing.project.id,
            taskId = existing.task.id,
            tags = listOf(existing.tag),
        )
        val metadataHandle = e2e.createOnServer(metadataEntry)
        val metadataServerId = requireNotNull(metadataHandle.serverId)
        e2e.launchApp()
        openCalendar(e2e.composeRule)

        val entryTag = "week-entry-$metadataServerId"
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(entryTag), WAIT_MS)
        val metadataText = listOfNotNull(existing.client?.name, existing.project.name, existing.task.name)
            .joinToString(" · ")
        e2e.composeRule.onNodeWithTag(entryTag, useUnmergedTree = true).assertIsDisplayed()
        e2e.composeRule.waitUntilAtLeastOneExists(hasText(metadataText, substring = true), WAIT_MS)
        e2e.composeRule.onNodeWithText(metadataText, substring = true, useUnmergedTree = true).assertIsDisplayed()
        e2e.composeRule.onNodeWithText("1h 00m", substring = true, useUnmergedTree = true).assertIsDisplayed()
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_ADD_ENTRY, useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.ENTRY_SAVE), WAIT_MS)

        // The real disposable account keeps its catalogue between individual test invocations;
        // use a unique suffix so a failed/retried run cannot collide with an earlier client.
        val suffix = UUID.randomUUID().toString()
        val projectName = "Calendar project $suffix"
        val clientName = "Calendar client $suffix"
        val taskName = "Calendar task $suffix"
        val tagName = "Calendar tag $suffix"
        val description = "Calendar created $suffix"

        createProjectWithClient(e2e.composeRule, projectName, clientName)
        createTask(e2e.composeRule, taskName)
        createTag(e2e.composeRule, tagName)
        e2e.composeRule.onNodeWithTag(TestTags.ENTRY_DESCRIPTION, useUnmergedTree = true)
            .performScrollTo()
            .performTextInput(description)
        e2e.composeRule.onNodeWithTag(TestTags.ENTRY_SAVE, useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        e2e.composeRule.waitUntilDoesNotExist(hasTestTag(TestTags.ENTRY_SAVE), WAIT_MS)

        val catalog = e2e.catalogSnapshot()
        assertTrue(catalog.projects.any { it.name == projectName })
        assertTrue(catalog.clients.any { it.name == clientName })
        assertTrue(catalog.tasks.any { it.name == taskName })
        assertTrue(catalog.tags.any { it.name == tagName })
        val project = catalog.projects.first { it.name == projectName }
        val task = catalog.tasks.first { it.name == taskName }
        val tag = catalog.tags.first { it.name == tagName }
        val created = e2e.awaitServer(WAIT_MS, driveSync = true) { snapshot ->
            snapshot.entries.any { entry ->
                entry.description == description &&
                    entry.projectId == project.id &&
                    entry.taskId == task.id &&
                    entry.tags.any { it.id == tag.id }
            }
        }
        assertTrue(
            "Calendar entry with inline catalogue metadata was not persisted",
            created.entries.any { it.description == description && it.projectId == project.id && it.taskId == task.id },
        )
    }

    @Test
    fun failedInlineCatalogueCreationStaysOpenAndCanBeRetried() {
        e2e.prepare(E2eFixture.Empty)
        val server = e2e.requireMockBackend()
        server.setCatalogueWritesFailing(true)
        e2e.launchApp()
        openCalendar(e2e.composeRule)
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_ADD_ENTRY, useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.ENTRY_SAVE), WAIT_MS)

        val projectName = "Calendar retry project ${e2e.testClock.nowMs()}"
        openProjectSearch(e2e.composeRule, projectName)
        e2e.composeRule.onNodeWithTag(TestTags.CATALOGUE_CREATE_PROJECT, useUnmergedTree = true).performClick()
        e2e.composeRule.onNodeWithTag(TestTags.CATALOGUE_NAME, useUnmergedTree = true).performTextClearance()
        e2e.composeRule.onNodeWithTag(TestTags.CATALOGUE_NAME, useUnmergedTree = true).performTextInput(projectName)
        e2e.composeRule.onNodeWithTag(TestTags.CATALOGUE_CREATE_CONFIRM, useUnmergedTree = true).performClick()

        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CATALOGUE_CREATE_ERROR), WAIT_MS)
        e2e.composeRule.onNodeWithTag(TestTags.CATALOGUE_CREATE_CONFIRM, useUnmergedTree = true).assertIsEnabled()
        assertEquals(1, server.callsMatching("POST", "/projects").size)

        server.setCatalogueWritesFailing(false)
        e2e.composeRule.onNodeWithTag(TestTags.CATALOGUE_CREATE_CONFIRM, useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilDoesNotExist(hasTestTag(TestTags.CATALOGUE_NAME), WAIT_MS)
        assertTrue(server.projects.any { it.name == projectName })
        assertEquals(2, server.callsMatching("POST", "/projects").size)
    }

    private fun createProjectWithClient(composeRule: ComposeTestRule, projectName: String, clientName: String) {
        openProjectSearch(composeRule, projectName)
        composeRule.onNodeWithTag(TestTags.CATALOGUE_CREATE_PROJECT, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(TestTags.CATALOGUE_NAME, useUnmergedTree = true).performTextClearance()
        composeRule.onNodeWithTag(TestTags.CATALOGUE_NAME, useUnmergedTree = true).performTextInput(projectName)
        composeRule.onNodeWithTag(TestTags.CATALOGUE_CLIENT_PICKER, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(TestTags.CATALOGUE_CREATE_CLIENT, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(TestTags.CATALOGUE_NAME, useUnmergedTree = true).performTextInput(clientName)
        composeRule.onNodeWithTag(TestTags.CATALOGUE_CREATE_CONFIRM, useUnmergedTree = true).performClick()
        composeRule.waitUntil(WAIT_MS) {
            runCatching {
                composeRule.onNodeWithTag(TestTags.CATALOGUE_NAME, useUnmergedTree = true)
                    .assertTextContains(projectName, substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag(TestTags.CATALOGUE_CREATE_CONFIRM, useUnmergedTree = true).performClick()
        composeRule.waitUntilDoesNotExist(hasTestTag(TestTags.CATALOGUE_NAME), WAIT_MS)
    }

    private fun createTask(composeRule: ComposeTestRule, taskName: String) {
        openProjectSearch(composeRule, taskName)
        composeRule.onNodeWithTag(TestTags.CATALOGUE_CREATE_TASK, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(TestTags.CATALOGUE_CREATE_CONFIRM, useUnmergedTree = true).performClick()
        composeRule.waitUntilDoesNotExist(hasTestTag(TestTags.CATALOGUE_NAME), WAIT_MS)
    }

    private fun createTag(composeRule: ComposeTestRule, tagName: String) {
        composeRule.onNodeWithTag(TestTags.CATALOGUE_CREATE_TAG, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag(TestTags.CATALOGUE_NAME, useUnmergedTree = true).performTextInput(tagName)
        composeRule.onNodeWithTag(TestTags.CATALOGUE_CREATE_CONFIRM, useUnmergedTree = true).performClick()
        composeRule.waitUntilDoesNotExist(hasTestTag(TestTags.CATALOGUE_NAME), WAIT_MS)
    }

    private fun openProjectSearch(composeRule: ComposeTestRule, query: String) {
        composeRule.onNodeWithTag(TestTags.ENTRY_PROJECT_TASK_SELECTOR, useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CATALOGUE_PROJECT_TASK_SEARCH), WAIT_MS)
        composeRule.onNodeWithTag(TestTags.CATALOGUE_PROJECT_TASK_SEARCH, useUnmergedTree = true).performTextInput(query)
    }

    private fun openCalendar(composeRule: ComposeTestRule) {
        composeRule.waitUntilAtLeastOneExists(hasTestTag("main_nav_calendar"), WAIT_MS)
        composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_WEEK_GRID), WAIT_MS)
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

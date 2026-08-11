/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tile

import dev.tricked.solidverdant.data.local.AppThemeMode
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Organization
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.ProjectsResponse
import dev.tricked.solidverdant.data.model.TasksResponse
import dev.tricked.solidverdant.data.remote.ApiClientFactory
import dev.tricked.solidverdant.data.remote.SolidtimeApi
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectSelectionViewModelRaceTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun late_catalog_response_from_a_superseded_load_cannot_replace_the_newer_result() = runTest(dispatcher.scheduler) {
        val api = mockk<SolidtimeApi>()
        val factory = mockk<ApiClientFactory> {
            every { createApi("https://example.test") } returns api
        }
        val firstResponseGate = CompletableDeferred<Unit>()
        val requestCount = AtomicInteger()
        coEvery { api.getProjects("org-1") } coAnswers {
            if (requestCount.getAndIncrement() == 0) {
                withContext(NonCancellable) { firstResponseGate.await() }
                ProjectsResponse(listOf(project("old")))
            } else {
                ProjectsResponse(listOf(project("new")))
            }
        }
        coEvery { api.getTasks("org-1") } returns TasksResponse(emptyList())
        val viewModel = viewModel(factory)

        viewModel.loadProjects(forceRefresh = true)
        viewModel.loadProjects(forceRefresh = true)

        assertEquals(listOf("new"), viewModel.uiState.value.projects.map { it.id })

        // The first fake request deliberately ignores cancellation to model a late HTTP
        // callback from a client that has already handed its response to the caller.
        firstResponseGate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("new"), viewModel.uiState.value.projects.map { it.id })
        assertTrue(requestCount.get() >= 2)
    }

    @Test
    fun an_intermittent_catalog_failure_clears_loading_and_can_be_retried() = runTest(dispatcher.scheduler) {
        val api = mockk<SolidtimeApi>()
        val factory = mockk<ApiClientFactory> {
            every { createApi("https://example.test") } returns api
        }
        val requestCount = AtomicInteger()
        coEvery { api.getProjects("org-1") } coAnswers {
            if (requestCount.getAndIncrement() == 0) {
                throw IOException("network unavailable")
            }
            ProjectsResponse(listOf(project("recovered")))
        }
        coEvery { api.getTasks("org-1") } returns TasksResponse(emptyList())
        val viewModel = viewModel(factory)

        viewModel.loadProjects(forceRefresh = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals("network unavailable", viewModel.uiState.value.error)

        viewModel.loadProjects(forceRefresh = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("recovered"), viewModel.uiState.value.projects.map { it.id })
        assertEquals(null, viewModel.uiState.value.error)
    }

    private fun viewModel(factory: ApiClientFactory): ProjectSelectionViewModel {
        val authRepository = mockk<AuthRepository> {
            every { endpoint } returns flowOf("https://example.test")
            coEvery { getCurrentMembership() } returns Membership(
                id = "member-1",
                role = "member",
                organization = Organization("org-1", "Example", "USD"),
            )
        }
        val timeEntryRepository = mockk<TimeEntryRepository> {
            every { observeProjects("org-1") } returns flowOf(emptyList())
            every { observeTasks("org-1") } returns flowOf(emptyList())
        }
        val settings = mockk<SettingsDataStore> {
            every { appTheme } returns flowOf(AppThemeMode.SYSTEM)
        }
        return ProjectSelectionViewModel(factory, authRepository, timeEntryRepository, settings)
    }

    private fun project(id: String) = Project(id = id, name = id, color = "#123456")
}

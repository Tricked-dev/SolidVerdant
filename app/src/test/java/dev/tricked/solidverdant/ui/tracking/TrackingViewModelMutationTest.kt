/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.domain.time.TemporalPolicyProvider
import dev.tricked.solidverdant.sync.SyncTrigger
import dev.tricked.solidverdant.util.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrackingViewModelMutationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var dispatcher: TestDispatcher
    private lateinit var settings: SettingsDataStore
    private val viewModels = mutableListOf<TrackingViewModel>()
    private val clock = object : Clock {
        override fun nowMs() = 1_000L
    }

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        settings = SettingsDataStore(context)
    }

    @After
    fun tearDown() {
        val jobs = viewModels.mapNotNull { it.cancelScopeForTest() }
        viewModels.clear()
        dispatcher.scheduler.runCurrent()
        kotlinx.coroutines.runBlocking { jobs.forEach { it.join() } }
        shadowOf(Looper.getMainLooper()).idle()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun intermittent_start_failure_clears_mutation_loading_state() = runTest(dispatcher.scheduler) {
        val repository = mockk<TimeEntryRepository>(relaxed = true)
        coEvery {
            repository.startEntry(any(), any(), any(), any(), any(), any(), any())
        } throws IOException("network disappeared")
        val viewModel = viewModel(repository)

        viewModel.startTimeEntry("org", "member", "user")
        dispatcher.scheduler.runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("network disappeared", viewModel.uiState.value.error)
    }

    @Test
    fun intermittent_stop_failure_clears_mutation_loading_state() = runTest(dispatcher.scheduler) {
        val active = TimeEntry(
            id = "active",
            userId = "user",
            organizationId = "org",
            start = "2026-08-10T08:00:00Z",
            description = "work",
        )
        val repository = mockk<TimeEntryRepository>(relaxed = true)
        coEvery { repository.stopEntry(any(), any()) } throws IOException("network disappeared")
        settings.cacheTrackingState(
            SettingsDataStore.CachedTrackingState(
                organizationId = "org",
                timeEntries = listOf(active),
                projects = emptyList(),
                clients = emptyList(),
                tasks = emptyList(),
                tags = emptyList(),
                activeEntry = active,
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.stopTimeEntry()
        dispatcher.scheduler.runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("network disappeared", viewModel.uiState.value.error)
    }

    @Test
    fun repeated_start_is_ignored_while_the_first_mutation_is_in_flight() = runTest(dispatcher.scheduler) {
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val entry = TimeEntry(
            id = "local-start",
            userId = "user",
            organizationId = "org",
            start = "2026-08-10T08:00:00Z",
        )
        val repository = mockk<TimeEntryRepository>(relaxed = true)
        coEvery { repository.startEntry(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            started.complete(Unit)
            release.await()
            entry
        }
        val viewModel = viewModel(repository)

        viewModel.startTimeEntry("org", "member", "user")
        assertTrue(started.isCompleted)
        viewModel.startTimeEntry("org", "member", "user")

        coVerify(exactly = 1) { repository.startEntry(any(), any(), any(), any(), any(), any(), any()) }
        release.complete(Unit)
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun repeated_stop_is_ignored_while_the_first_mutation_is_in_flight() = runTest(dispatcher.scheduler) {
        val release = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val active = TimeEntry(
            id = "active",
            userId = "user",
            organizationId = "org",
            start = "2026-08-10T08:00:00Z",
            description = "work",
        )
        val repository = mockk<TimeEntryRepository>(relaxed = true)
        coEvery { repository.stopEntry(any(), any()) } coAnswers {
            stopped.complete(Unit)
            release.await()
        }
        settings.cacheTrackingState(
            SettingsDataStore.CachedTrackingState(
                organizationId = "org",
                timeEntries = listOf(active),
                projects = emptyList(),
                clients = emptyList(),
                tasks = emptyList(),
                tags = emptyList(),
                activeEntry = active,
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.stopTimeEntry()
        assertTrue(stopped.isCompleted)
        viewModel.stopTimeEntry()

        coVerify(exactly = 1) { repository.stopEntry(any(), any()) }
        release.complete(Unit)
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun editing_a_running_entry_changes_its_start_without_stopping_it() = runTest(dispatcher.scheduler) {
        val active = TimeEntry(
            id = "active",
            userId = "user",
            organizationId = "org",
            start = "2026-08-10T08:00:00Z",
            description = "work",
        )
        val repository = mockk<TimeEntryRepository>(relaxed = true)
        settings.cacheTrackingState(
            SettingsDataStore.CachedTrackingState(
                organizationId = "org",
                timeEntries = listOf(active),
                projects = emptyList(),
                clients = emptyList(),
                tasks = emptyList(),
                tags = emptyList(),
                activeEntry = active,
            ),
        )
        val viewModel = viewModel(repository)
        val newStart = "2026-08-10T07:30:00Z"

        viewModel.updatePastTimeEntry(
            timeEntry = active,
            description = active.description,
            projectId = active.projectId,
            taskId = active.taskId,
            tags = emptyList(),
            billable = active.billable,
            start = newStart,
            end = null,
        )
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) {
            repository.updateEntry(match { it.start == newStart && it.end == null }, emptyList())
        }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private fun viewModel(repository: TimeEntryRepository): TrackingViewModel = TrackingViewModel(
        authRepository = mockk<AuthRepository>(relaxed = true),
        settingsDataStore = settings,
        timeEntryRepository = repository,
        syncTrigger = SyncTrigger {},
        temporalPolicyProvider = TemporalPolicyProvider(settings),
        context = context,
        clock = clock,
    ).also { viewModels += it }
}

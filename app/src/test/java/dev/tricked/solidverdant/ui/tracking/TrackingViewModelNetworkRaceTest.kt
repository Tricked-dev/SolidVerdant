/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.toEntity
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.TimeEntriesMeta
import dev.tricked.solidverdant.data.model.TimeEntriesResponse
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.remote.FakeRemoteDataSource
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.domain.time.TemporalPolicyProvider
import dev.tricked.solidverdant.sync.SyncTrigger
import dev.tricked.solidverdant.util.Clock
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrackingViewModelNetworkRaceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var dispatcher: TestDispatcher
    private val viewModels = mutableListOf<TrackingViewModel>()
    private lateinit var db: AppDatabase

    private val clock = object : Clock {
        override fun nowMs() = 1_000L
    }

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        context.getSharedPreferences(IMMEDIATE_CACHE, Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        val testViewModels = viewModels.toList()
        viewModels.clear()
        val scopeJobs = testViewModels.mapNotNull { it.cancelScopeForTest() }
        dispatcher.scheduler.advanceUntilIdle()
        kotlinx.coroutines.runBlocking {
            scopeJobs.forEach { it.join() }
        }
        shadowOf(Looper.getMainLooper()).idle()
        dispatcher.scheduler.advanceUntilIdle()
        db.close()
        context.getSharedPreferences(IMMEDIATE_CACHE, Context.MODE_PRIVATE).edit().clear().commit()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun intermittent_active_poll_failure_keeps_the_cached_running_timer_visible() = runTest(dispatcher.scheduler) {
        val active = entry("cached-active")
        val settings = SettingsDataStore(context)
        settings.cacheTrackingState(
            SettingsDataStore.CachedTrackingState(
                organizationId = ORG,
                timeEntries = listOf(active),
                projects = emptyList(),
                tasks = emptyList(),
                tags = emptyList(),
                activeEntry = active,
            ),
        )
        val authRepository = mockk<AuthRepository>(relaxed = true)
        coEvery { authRepository.getActiveTimeEntry() } returns Result.failure(IOException("offline"))
        val viewModel = viewModel(authRepository, settings)

        viewModel.onAppForegrounded(ORG, MEMBER, refreshAll = false)
        dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isTracking)
        assertEquals(active.id, viewModel.uiState.value.currentTimeEntry?.id)
        assertTrue(viewModel.uiState.value.error?.contains("offline") == true)
        viewModel.cancelScopeForTest()
    }

    @Test
    fun late_active_response_after_backgrounding_cannot_clear_the_cached_timer() = runTest(dispatcher.scheduler) {
        val active = entry("cached-active")
        val settings = SettingsDataStore(context)
        settings.cacheTrackingState(
            SettingsDataStore.CachedTrackingState(
                organizationId = ORG,
                timeEntries = listOf(active),
                projects = emptyList(),
                tasks = emptyList(),
                tags = emptyList(),
                activeEntry = active,
            ),
        )
        val response = CompletableDeferred<Result<TimeEntry?>>()
        val authRepository = mockk<AuthRepository>(relaxed = true)
        coEvery { authRepository.getActiveTimeEntry() } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        val viewModel = viewModel(authRepository, settings)

        viewModel.onAppForegrounded(ORG, MEMBER, refreshAll = false)
        viewModel.onAppBackgrounded()
        response.complete(Result.success(null))
        dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isTracking)
        assertEquals(active.id, viewModel.uiState.value.currentTimeEntry?.id)
        viewModel.cancelScopeForTest()
    }

    @Test
    fun externally_polled_timer_survives_a_later_room_emission() = runTest(dispatcher.scheduler) {
        val active = entry("other-active", organizationId = OTHER_ORG)
        val authRepository = mockk<AuthRepository>(relaxed = true)
        coEvery { authRepository.getActiveTimeEntry() } returns Result.success(active)
        val viewModel = viewModel(
            authRepository = authRepository,
            settings = SettingsDataStore(context),
        )

        viewModel.loadAllData(OTHER_ORG, MEMBER)
        viewModel.uiState.first { it.hasLoadedTimeEntries }
        viewModel.onAppForegrounded(OTHER_ORG, MEMBER, refreshAll = false)
        dispatcher.scheduler.runCurrent()
        assertEquals(active.id, viewModel.uiState.value.currentTimeEntry?.id)

        // A catalog emission is enough to re-run the combined Room collector while its active
        // query still has the old empty snapshot.
        db.catalogDao().upsertProjects(
            listOf(
                Project(id = "project-refresh", name = "Refresh", color = "#123456").toEntity(OTHER_ORG),
            ),
        )
        dispatcher.scheduler.runCurrent()

        assertEquals(active.id, viewModel.uiState.value.currentTimeEntry?.id)
        viewModel.cancelScopeForTest()
    }

    @Test
    fun late_active_response_cannot_overwrite_a_newer_poll_result() = runTest(dispatcher.scheduler) {
        val firstResponse = CompletableDeferred<Result<TimeEntry?>>()
        val secondResponse = CompletableDeferred<Result<TimeEntry?>>()
        val calls = AtomicInteger(0)
        val authRepository = mockk<AuthRepository>(relaxed = true)
        coEvery { authRepository.getActiveTimeEntry() } coAnswers {
            if (calls.incrementAndGet() == 1) firstResponse.await() else secondResponse.await()
        }
        val viewModel = viewModel(authRepository, SettingsDataStore(context))

        viewModel.onAppForegrounded(ORG, MEMBER, refreshAll = false)
        viewModel.onAppForegrounded(ORG, MEMBER, refreshAll = false)
        assertEquals(2, calls.get())

        val newer = entry("newer-active", description = "newer")
        secondResponse.complete(Result.success<TimeEntry?>(newer))
        dispatcher.scheduler.runCurrent()
        assertEquals(newer.id, viewModel.uiState.value.currentTimeEntry?.id)

        val older = entry("older-active", description = "older")
        firstResponse.complete(Result.success<TimeEntry?>(older))
        dispatcher.scheduler.runCurrent()

        assertEquals(newer.id, viewModel.uiState.value.currentTimeEntry?.id)
        viewModel.cancelScopeForTest()
    }

    @Test
    fun duplicate_scroll_callbacks_issue_only_one_history_request() = runTest(dispatcher.scheduler) {
        val remote = FakeRemoteDataSource(
            entries = (0 until HISTORY_LIMIT).map { index ->
                entry(
                    "history-$index",
                    start = "2026-07-07T${(index / 60).toString().padStart(2, '0')}:${(index % 60).toString().padStart(2, '0')}:00Z",
                )
            },
        )
        val pageResponse = CompletableDeferred<Result<TimeEntriesResponse>>()
        var pageCalls = 0
        val authRepository = mockk<AuthRepository>(relaxed = true)
        coEvery { authRepository.getActiveTimeEntry() } returns Result.success(null)
        coEvery { authRepository.getTimeEntries(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            pageCalls += 1
            pageResponse.await()
        }
        val viewModel = viewModel(authRepository, SettingsDataStore(context), remote)

        viewModel.loadAllData(ORG, MEMBER)
        viewModel.uiState.first { it.hasMoreTimeEntries }

        viewModel.loadMoreTimeEntries()
        viewModel.loadMoreTimeEntries()
        dispatcher.scheduler.runCurrent()

        assertEquals(1, pageCalls)
        pageResponse.complete(
            Result.success(
                TimeEntriesResponse(
                    data = emptyList(),
                    meta = TimeEntriesMeta(total = HISTORY_LIMIT),
                ),
            ),
        )
        dispatcher.scheduler.runCurrent()
        viewModel.cancelScopeForTest()
    }

    @Test
    fun late_history_page_after_switching_organization_is_ignored() = runTest(dispatcher.scheduler) {
        val remote = FakeRemoteDataSource(
            entries = (0 until HISTORY_LIMIT).map { index ->
                entry(
                    "cached-$index",
                    start = "2026-07-07T${(index / 60).toString().padStart(2, '0')}:${(index % 60).toString().padStart(2, '0')}:00Z",
                )
            },
        )
        val pageResponse = CompletableDeferred<Result<TimeEntriesResponse>>()
        var pageCalls = 0
        val authRepository = mockk<AuthRepository>(relaxed = true)
        coEvery { authRepository.getActiveTimeEntry() } returns Result.success(null)
        coEvery { authRepository.getTimeEntries(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            pageCalls += 1
            pageResponse.await()
        }
        val viewModel = viewModel(authRepository, SettingsDataStore(context), remote)

        viewModel.loadAllData(ORG, MEMBER)
        viewModel.uiState.first { it.hasMoreTimeEntries }
        viewModel.loadMoreTimeEntries()
        dispatcher.scheduler.runCurrent()
        assertEquals(1, pageCalls)

        viewModel.loadAllData(OTHER_ORG, MEMBER)
        pageResponse.complete(
            Result.success(
                TimeEntriesResponse(
                    data = listOf(entry("stale-page")),
                    meta = TimeEntriesMeta(total = HISTORY_LIMIT + 1),
                ),
            ),
        )
        dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.timeEntries.none { it.id == "stale-page" })
        assertFalse(viewModel.uiState.value.isLoadingMoreTimeEntries)
        viewModel.cancelScopeForTest()
    }

    @Test
    fun failed_history_page_can_retry_after_network_returns() = runTest(dispatcher.scheduler) {
        val remote = FakeRemoteDataSource(
            entries = (0 until HISTORY_LIMIT).map { index ->
                entry(
                    "cached-$index",
                    start = "2026-07-07T${(index / 60).toString().padStart(2, '0')}:${(index % 60).toString().padStart(2, '0')}:00Z",
                )
            },
        )
        val authRepository = mockk<AuthRepository>(relaxed = true)
        coEvery { authRepository.getActiveTimeEntry() } returns Result.success(null)
        coEvery { authRepository.getTimeEntries(any(), any(), any(), any(), any(), any(), any()) } returnsMany listOf(
            Result.failure(IOException("network disappeared")),
            Result.success(
                TimeEntriesResponse(
                    data = listOf(entry("retried-page")),
                    meta = TimeEntriesMeta(total = HISTORY_LIMIT + 1),
                ),
            ),
        )
        val viewModel = viewModel(authRepository, SettingsDataStore(context), remote)

        viewModel.loadAllData(ORG, MEMBER)
        viewModel.uiState.first { it.hasMoreTimeEntries }

        viewModel.loadMoreTimeEntries()
        dispatcher.scheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isLoadingMoreTimeEntries)
        assertTrue(viewModel.uiState.value.error?.contains("network disappeared") == true)

        viewModel.loadMoreTimeEntries()
        viewModel.uiState.first { state -> state.timeEntries.any { it.id == "retried-page" } }

        assertTrue(viewModel.uiState.value.timeEntries.any { it.id == "retried-page" })
        assertNull(viewModel.uiState.value.error)
        viewModel.cancelScopeForTest()
    }

    private fun viewModel(
        authRepository: AuthRepository,
        settings: SettingsDataStore,
        remote: FakeRemoteDataSource = FakeRemoteDataSource(),
    ): TrackingViewModel {
        val repository = TimeEntryRepository(
            db.timeEntryDao(),
            db.catalogDao(),
            db.outboxDao(),
            db.syncMetaDao(),
            remote,
            clock,
            Json { encodeDefaults = true },
            db,
        )
        return TrackingViewModel(
            authRepository = authRepository,
            settingsDataStore = settings,
            timeEntryRepository = repository,
            syncTrigger = SyncTrigger {},
            temporalPolicyProvider = TemporalPolicyProvider(settings),
            context = context,
            clock = clock,
        ).also { viewModels += it }
    }

    private fun entry(id: String, description: String = "work", start: String = "2026-07-07T08:00:00Z", organizationId: String = ORG) =
        TimeEntry(
            id = id,
            userId = "user-1",
            organizationId = organizationId,
            start = start,
            end = null,
            description = description,
        )

    private companion object {
        const val ORG = "org1"
        const val OTHER_ORG = "org2"
        const val MEMBER = "member1"
        const val HISTORY_LIMIT = 250
        const val IMMEDIATE_CACHE = "immediate_ui_cache"
    }
}

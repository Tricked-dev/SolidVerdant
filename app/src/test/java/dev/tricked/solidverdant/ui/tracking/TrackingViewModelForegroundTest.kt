/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.OutboxEntity
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.remote.ApiClientFactory
import dev.tricked.solidverdant.data.remote.FakeRemoteDataSource
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.domain.time.TemporalPolicyProvider
import dev.tricked.solidverdant.sync.SyncTrigger
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrackingViewModelForegroundTest {

    private lateinit var db: AppDatabase
    private lateinit var dispatcher: TestDispatcher
    private val viewModels = mutableListOf<TrackingViewModel>()
    private var syncRequests = 0
    private var now = 0L

    private val syncTrigger = SyncTrigger { syncRequests++ }
    private val clock = object : Clock {
        override fun nowMs() = now
    }

    @Before
    fun setup() {
        dispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
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
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun viewModel(): TrackingViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = SettingsDataStore(context)
        val repository = TimeEntryRepository(
            db.timeEntryDao(),
            db.catalogDao(),
            db.outboxDao(),
            db.syncMetaDao(),
            FakeRemoteDataSource(),
            clock,
            Json { encodeDefaults = true },
            db,
        )
        val authRepository = AuthRepository(
            AuthDataStore(context),
            ApiClientFactory(OkHttpClient(), Json { ignoreUnknownKeys = true }),
        )
        return TrackingViewModel(
            authRepository = authRepository,
            settingsDataStore = settings,
            timeEntryRepository = repository,
            syncTrigger = syncTrigger,
            temporalPolicyProvider = TemporalPolicyProvider(settings),
            context = context,
            clock = clock,
        ).also { viewModels += it }
    }

    @Test
    fun `two foregrounds within debounce window trigger one refresh`() = runTest(dispatcher.scheduler) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        syncRequests = 0

        now = 100_000L
        vm.onAppForegrounded(ORG, MEMBER, refreshAll = true)
        dispatcher.scheduler.advanceUntilIdle()

        now = 102_000L // within the debounce window
        vm.onAppForegrounded(ORG, MEMBER, refreshAll = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, syncRequests)
    }

    @Test
    fun `foregrounds outside debounce window trigger two refreshes`() = runTest(dispatcher.scheduler) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        syncRequests = 0

        now = 100_000L
        vm.onAppForegrounded(ORG, MEMBER, refreshAll = true)
        dispatcher.scheduler.advanceUntilIdle()

        now = 100_000L + FOREGROUND_REFRESH_DEBOUNCE_MS + 1 // past the debounce window
        vm.onAppForegrounded(ORG, MEMBER, refreshAll = true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, syncRequests)
    }

    @Test
    fun `track aggregate retry revives failed operations before requesting sync`() = runTest(dispatcher.scheduler) {
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.STOP,
                organizationId = ORG,
                timeEntryId = "failed-entry",
                payloadJson = "{}",
                createdAtMs = 1L,
                attemptCount = 5,
                lastError = "Server rejected this change",
                deadLettered = true,
            ),
        )
        val vm = viewModel()
        syncRequests = 0

        vm.retryAllSync(ORG).join()
        dispatcher.scheduler.advanceUntilIdle()

        val revived = db.outboxDao().peekAll().single()
        assertFalse(revived.deadLettered)
        assertEquals(0, revived.attemptCount)
        assertNull(revived.lastError)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `routine pending sync stays hidden until it is slow`() = runTest(dispatcher.scheduler) {
        val operation = TimeEntryRepository.SyncOperation(
            entryId = "pending-entry",
            type = OutboxOpType.UPDATE,
            status = TimeEntryRepository.EntrySyncStatus.PENDING,
            attemptCount = 0,
            error = null,
        )
        val vm = viewModel()
        vm.acceptSyncOperationsForTest(listOf(operation))
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.uiState.value.syncStatusVisible)

        dispatcher.scheduler.advanceTimeBy(SYNC_STATUS_REVEAL_DELAY_MS - 1)
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.uiState.value.syncStatusVisible)

        // Subscribe before crossing the boundary. Reading StateFlow.value repeatedly is racy here:
        // a later Room emission can replace the state between the assertion message and condition.
        val visibleState = async { vm.uiState.first { it.syncStatusVisible } }
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()
        val revealed = visibleState.await()
        assertEquals(
            listOf(TimeEntryRepository.EntrySyncStatus.PENDING),
            revealed.syncOperations.map { it.status },
        )
        assertTrue(vm.uiState.value.syncStatusVisible)
    }

    @Test
    fun `failed sync is visible immediately`() = runTest(dispatcher.scheduler) {
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = ORG,
                timeEntryId = "failed-entry",
                payloadJson = "{}",
                createdAtMs = 1L,
                attemptCount = 1,
                lastError = "Server rejected this change",
                deadLettered = true,
            ),
        )
        val vm = viewModel()
        vm.loadAllData(ORG, MEMBER)

        vm.uiState.first { it.syncOperations.isNotEmpty() }
        assertTrue(vm.uiState.value.syncStatusVisible)
    }

    private companion object {
        const val ORG = "org1"
        const val MEMBER = "m1"
        const val FOREGROUND_REFRESH_DEBOUNCE_MS = 5_000L
    }
}

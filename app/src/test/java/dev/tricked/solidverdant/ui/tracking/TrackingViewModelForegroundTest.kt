/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.remote.ApiClientFactory
import dev.tricked.solidverdant.data.remote.FakeRemoteDataSource
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.domain.time.TemporalPolicyProvider
import dev.tricked.solidverdant.sync.SyncTrigger
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrackingViewModelForegroundTest {

    private lateinit var db: AppDatabase
    private val dispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<TrackingViewModel>()
    private var syncRequests = 0
    private var now = 0L

    private val syncTrigger = SyncTrigger { syncRequests++ }
    private val clock = object : Clock {
        override fun nowMs() = now
    }

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        viewModels.forEach { it.cancelScopeForTest() }
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

    private companion object {
        const val ORG = "org1"
        const val MEMBER = "m1"
        const val FOREGROUND_REFRESH_DEBOUNCE_MS = 5_000L
    }
}

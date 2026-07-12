/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.OutboxEntity
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.local.db.SyncMetaEntity
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Organization
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.data.remote.FakeRemoteDataSource
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.sync.SyncTrigger
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SyncCenterViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsDataStore
    private lateinit var repository: TimeEntryRepository
    private val dispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<SyncCenterViewModel>()
    private var syncRequests = 0

    private val syncTrigger = SyncTrigger { syncRequests++ }
    private val clock = object : Clock {
        override fun nowMs() = NOW_MS
    }

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsDataStore(context)
        repository = TimeEntryRepository(
            db.timeEntryDao(),
            db.catalogDao(),
            db.outboxDao(),
            db.syncMetaDao(),
            FakeRemoteDataSource(),
            clock,
            Json { encodeDefaults = true },
            db,
        )
    }

    @After
    fun teardown() {
        viewModels.forEach { it.cancelScopeForTest() }
        // Drain the requested cancellations (including the WhileSubscribed sharing job) before
        // resetMain, so no Main-bound continuation straggles into "Main is used concurrently".
        dispatcher.scheduler.advanceUntilIdle()
        db.close()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun viewModel() = SyncCenterViewModel(
        repository = repository,
        settingsDataStore = settings,
        syncTrigger = syncTrigger,
        clock = clock,
    ).also { viewModels += it }

    private fun seedOrg(orgId: String = ORG, memberId: String = MEMBER) {
        val user = User(id = "u1", name = "Ada", email = "ada@example.com")
        val membership = Membership(
            id = memberId,
            role = "member",
            organization = Organization(id = orgId, name = "Acme", currency = "USD"),
        )
        settings.cacheAuth(user, listOf(membership), memberId)
    }

    private suspend fun seedOp(entryId: String, opType: OutboxOpType, deadLettered: Boolean, error: String? = null, attempts: Int = 0) {
        db.outboxDao().insert(
            OutboxEntity(
                opType = opType,
                organizationId = ORG,
                timeEntryId = entryId,
                payloadJson = "{}",
                createdAtMs = 1L,
                attemptCount = attempts,
                lastError = error,
                deadLettered = deadLettered,
            ),
        )
    }

    @Test
    fun `partitions pending and failed operations with counts`() = runTest(dispatcher.scheduler) {
        seedOrg()
        seedOp("e-pending", OutboxOpType.UPDATE, deadLettered = false)
        seedOp("e-failed", OutboxOpType.CREATE, deadLettered = true, error = "HTTP 500 Internal Server Error", attempts = 5)
        db.syncMetaDao().upsert(SyncMetaEntity(organizationId = ORG, lastFullSyncAtMs = NOW_MS - 60_000L, lastPushAtMs = NOW_MS - 120_000L))

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.first { it.organizationId == ORG && it.failed.isNotEmpty() && it.pending.isNotEmpty() }
        assertEquals(listOf("e-pending"), state.pending.map { it.entryId })
        assertEquals(listOf("e-failed"), state.failed.map { it.entryId })
        assertEquals(1, state.pendingCount)
        assertEquals(1, state.failedCount)
        assertEquals(SyncCenterUiState.TopLine.FAILURES, state.topLine)
    }

    @Test
    fun `all caught up when there are no operations`() = runTest(dispatcher.scheduler) {
        seedOrg()
        db.syncMetaDao().upsert(SyncMetaEntity(organizationId = ORG, lastFullSyncAtMs = NOW_MS, lastPushAtMs = null))

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.first { it.organizationId == ORG }
        assertTrue(state.pending.isEmpty())
        assertTrue(state.failed.isEmpty())
        assertEquals(SyncCenterUiState.TopLine.SYNCED, state.topLine)
    }

    @Test
    fun `pending without failures reports waiting top line`() = runTest(dispatcher.scheduler) {
        seedOrg()
        seedOp("e1", OutboxOpType.UPDATE, deadLettered = false)

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.first { it.organizationId == ORG && it.pending.isNotEmpty() }
        assertEquals(SyncCenterUiState.TopLine.PENDING, state.topLine)
    }

    @Test
    fun `freshness reflects sync meta and sentinel zero pull becomes null`() = runTest(dispatcher.scheduler) {
        seedOrg()
        db.syncMetaDao().upsert(SyncMetaEntity(organizationId = ORG, lastFullSyncAtMs = 0L, lastPushAtMs = null))

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.first { it.organizationId == ORG }
        assertNull(state.lastFullSyncAtMs)
        assertNull(state.lastPushAtMs)
    }

    @Test
    fun `retryAll requests a sync`() = runTest(dispatcher.scheduler) {
        seedOrg()
        seedOp("e-failed", OutboxOpType.CREATE, deadLettered = true, error = "boom", attempts = 5)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { it.organizationId == ORG }

        val before = syncRequests
        vm.retryAll()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(syncRequests > before)
    }

    @Test
    fun `retry resets the failed op and requests a sync`() = runTest(dispatcher.scheduler) {
        seedOrg()
        seedOp("e-failed", OutboxOpType.CREATE, deadLettered = true, error = "boom", attempts = 5)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { it.organizationId == ORG && it.failed.isNotEmpty() }

        val before = syncRequests
        vm.retry("e-failed")
        dispatcher.scheduler.advanceUntilIdle()

        // Op is no longer dead-lettered, so it leaves the failed list and re-appears as pending.
        val state = vm.uiState.first { it.organizationId == ORG && it.failed.isEmpty() && it.pending.isNotEmpty() }
        assertTrue(state.pending.any { it.entryId == "e-failed" })
        assertTrue(syncRequests > before)
    }

    @Test
    fun `discard removes the failed op`() = runTest(dispatcher.scheduler) {
        seedOrg()
        seedOp("e-failed", OutboxOpType.CREATE, deadLettered = true, error = "boom", attempts = 5)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { it.organizationId == ORG && it.failed.isNotEmpty() }

        vm.discard("e-failed")
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.first { it.organizationId == ORG && it.failed.isEmpty() }
        assertTrue(state.pending.isEmpty())
    }

    private companion object {
        const val ORG = "org1"
        const val MEMBER = "m1"
        const val NOW_MS = 1_000_000_000_000L
    }
}

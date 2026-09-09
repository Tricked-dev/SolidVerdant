/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.review

import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Organization
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.domain.time.TemporalPolicy
import dev.tricked.solidverdant.domain.time.TemporalPolicyProvider
import dev.tricked.solidverdant.sync.SyncScheduler
import dev.tricked.solidverdant.util.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId

/**
 * The sync worker rekeys an optimistic `local-` row to its server id while the review is open. The
 * review step must survive that: a completed step stays completed and the "x of n" total must not
 * grow. Also covers the honesty rule that a retry the outbox refused does not advance the wizard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewDayViewModelTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val entries = MutableStateFlow<List<TimeEntry>>(emptyList())
    private val syncOps = MutableStateFlow<List<TimeEntryRepository.SyncOperation>>(emptyList())
    private val repository = mockk<TimeEntryRepository>(relaxed = true)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun buildViewModel(): ReviewDayViewModel {
        val org = Organization(id = "org1", name = "Org", currency = "USD")
        val membership = Membership(id = "m1", role = "member", organization = org)
        val user = User(id = "u1", name = "U", email = "u@e.co", timezone = "UTC", weekStart = "monday")

        val settings = mockk<SettingsDataStore>()
        every { settings.getCachedAuth() } returns SettingsDataStore.CachedAuth(user, listOf(membership), "m1")

        every { repository.observeTimeEntries(any()) } returns entries
        every { repository.observeProjects(any()) } returns flowOf(emptyList())
        every { repository.observeSyncOperations(any()) } returns syncOps

        val policy = TemporalPolicy(zone, DayOfWeek.MONDAY)
        val policyProvider = mockk<TemporalPolicyProvider>()
        every { policyProvider.policy } returns flowOf(policy)
        coEvery { policyProvider.current() } returns policy

        return ReviewDayViewModel(
            repository = repository,
            settings = settings,
            syncScheduler = mockk<SyncScheduler>(relaxed = true),
            clock = object : Clock {
                override fun nowMs(): Long = NOW_MS
            },
            temporalPolicyProvider = policyProvider,
        )
    }

    private fun CoroutineScope.keepStateHot(viewModel: ReviewDayViewModel) {
        launch { viewModel.uiState.collect {} }
    }

    private fun uncategorized(id: String, start: String = START_ISO) = TimeEntry(
        id = id,
        userId = "u1",
        organizationId = "org1",
        start = start,
        end = END_ISO,
    )

    @Test
    fun handledStepStaysHandledAfterTheEntryIsRekeyed() = runTest {
        entries.value = listOf(uncategorized("local-1"))
        val viewModel = buildViewModel()
        backgroundScope.keepStateHot(viewModel)

        val item = viewModel.uiState.first { it.currentItem != null }.currentItem!!
        viewModel.keepAsIs(item)
        assertNull("step is handled", viewModel.uiState.first { it.handledIds.isNotEmpty() }.currentItem)

        entries.value = listOf(uncategorized("server-1"))

        val rekeyed = viewModel.uiState.first { it.items.any { i -> i.entryId == "server-1" } }
        assertNull("the same entry under a new id must not re-ask the step", rekeyed.currentItem)
        assertTrue(rekeyed.progress.isComplete)
    }

    @Test
    fun progressTotalDoesNotGrowAfterARekey() = runTest {
        entries.value = listOf(uncategorized("local-1"))
        val viewModel = buildViewModel()
        backgroundScope.keepStateHot(viewModel)

        val item = viewModel.uiState.first { it.currentItem != null }.currentItem!!
        viewModel.keepAsIs(item)
        val before = viewModel.uiState.first { it.handledIds.isNotEmpty() }.progress
        assertEquals(1, before.total)

        entries.value = listOf(uncategorized("server-1"))

        val after = viewModel.uiState.first { it.items.any { i -> i.entryId == "server-1" } }.progress
        assertEquals("the retired id must not be counted alongside the new item", 1, after.total)
        assertEquals(1, after.completed)
    }

    @Test
    fun assignProjectResolvesTheRekeyedEntryFromASnapshotItem() = runTest {
        entries.value = listOf(uncategorized("local-1"))
        val viewModel = buildViewModel()
        backgroundScope.keepStateHot(viewModel)

        val staleItem = viewModel.uiState.first { it.currentItem != null }.currentItem!!
        entries.value = listOf(uncategorized("server-1"))
        viewModel.uiState.first { it.items.any { i -> i.entryId == "server-1" } }

        viewModel.assignProject(staleItem, "p1")

        coVerify { repository.updateEntry(uncategorized("server-1").copy(projectId = "p1"), emptyList()) }
        assertTrue(viewModel.uiState.value.progress.isComplete)
    }

    @Test
    fun aRefusedRetryDoesNotMarkTheStepHandled() = runTest {
        entries.value = listOf(uncategorized("local-1"))
        syncOps.value = listOf(
            TimeEntryRepository.SyncOperation(
                entryId = "local-1",
                type = OutboxOpType.UPDATE,
                status = TimeEntryRepository.EntrySyncStatus.FAILED,
                attemptCount = 3,
                error = "boom",
            ),
        )
        coEvery { repository.prepareRetry(any()) } returns false
        val viewModel = buildViewModel()
        backgroundScope.keepStateHot(viewModel)

        val item = viewModel.uiState.first { s -> s.items.any { it.type == ReviewItemType.FAILED_SYNC } }
            .items.first { it.type == ReviewItemType.FAILED_SYNC }

        viewModel.retryFailedSync(item)

        val state = viewModel.uiState.value
        assertTrue("a still-failing op must stay on screen", item.id !in state.handledIds)
        assertEquals(item.id, state.currentItem?.id)
    }

    private companion object {
        const val NOW_MS = 1_752_300_000_000L // 2025-07-12T06:00Z
        const val START_ISO = "2025-07-12T01:00:00Z"
        const val END_ISO = "2025-07-12T02:00:00Z"
    }
}

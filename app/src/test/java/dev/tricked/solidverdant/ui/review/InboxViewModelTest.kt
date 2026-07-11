/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.review

import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.db.InboxDismissalDao
import dev.tricked.solidverdant.data.local.db.InboxDismissalEntity
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Organization
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.domain.inbox.InboxSettingsDataStore
import dev.tricked.solidverdant.domain.time.TemporalPolicy
import dev.tricked.solidverdant.domain.time.TemporalPolicyProvider
import dev.tricked.solidverdant.sync.SyncTrigger
import dev.tricked.solidverdant.util.Clock
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/**
 * SV-005 T4.2: the ViewModel surfaces the horizon-chosen flag into UI state (so the pane knows to
 * show the first-run picker) and [InboxViewModel.chooseHorizon] persists the correct bound through
 * the real DataStore-backed store. Uses a Robolectric context for the store, mockk for the rest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InboxViewModelTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    // The preferences DataStore is file-backed and shared (per name) across test methods in the same
    // Robolectric context; clear it so each case starts from a fresh (unchosen) horizon.
    private suspend fun newStore() = InboxSettingsDataStore(ApplicationProvider.getApplicationContext())
        .also { it.clearForTest() }

    private fun buildViewModel(store: InboxSettingsDataStore): InboxViewModel {
        val org = Organization(id = "org1", name = "Org", currency = "USD")
        val membership = Membership(id = "m1", role = "member", organization = org)
        val user = User(id = "u1", name = "U", email = "u@e.co", timezone = "UTC", weekStart = "monday")

        val auth = mockk<AuthDataStore>()
        every { auth.currentMembershipId } returns flowOf("m1")

        val settings = mockk<SettingsDataStore>()
        every { settings.getCachedAuth() } returns
            SettingsDataStore.CachedAuth(user, listOf(membership), "m1")
        every { settings.longTimerHours } returns flowOf(8)

        val repo = mockk<TimeEntryRepository>(relaxed = true)
        every { repo.observeTimeEntries(any()) } returns flowOf(emptyList())
        every { repo.observeConflicts(any()) } returns flowOf(emptyList())
        every { repo.observeProjects(any()) } returns flowOf(emptyList())
        every { repo.observeTasks(any()) } returns flowOf(emptyList())
        every { repo.observeTags(any()) } returns flowOf(emptyList())
        coEvery { repo.refreshAll(any(), any()) } returns Result.success(Unit)

        val dismissalDao = mockk<InboxDismissalDao>(relaxed = true)
        every { dismissalDao.observeDismissals(any()) } returns flowOf(emptyList<InboxDismissalEntity>())

        val syncTrigger = mockk<SyncTrigger>(relaxed = true)
        val clock = object : Clock {
            override fun nowMs(): Long = NOW_MS
        }

        val policyProvider = mockk<TemporalPolicyProvider>()
        val policy = TemporalPolicy(zone, java.time.DayOfWeek.MONDAY)
        every { policyProvider.policy } returns flowOf(policy)
        coEvery { policyProvider.current() } returns policy

        return InboxViewModel(
            timeEntryRepository = repo,
            authDataStore = auth,
            settingsDataStore = settings,
            inboxSettingsDataStore = store,
            dismissalDao = dismissalDao,
            syncTrigger = syncTrigger,
            clock = clock,
            temporalPolicyProvider = policyProvider,
        )
    }

    @Test
    fun firstRun_horizonNotChosen_uiStateReportsUnchosen() = runTest {
        val store = newStore()
        val vm = buildViewModel(store)

        val state = vm.awaitState { !it.isLoading }
        assertFalse("fresh install has not chosen a horizon yet", state.horizonChosen)
    }

    @Test
    fun chooseHorizon_today_persistsStartOfTodayAndUnlocks() = runTest {
        val store = newStore()
        val vm = buildViewModel(store)
        vm.awaitState { !it.isLoading }

        vm.chooseHorizon(HorizonOption.TODAY)

        // Assert through uiState: the pipeline re-reads the store and unlocks the list, surfacing the
        // persisted bound. (The raw store round-trip is covered by InboxSettingsHorizonTest.)
        val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val state = vm.awaitState { it.horizonChosen }
        assertTrue("choosing unlocks the list", state.horizonChosen)
        assertEquals(startOfToday, state.horizonStartMs)
    }

    @Test
    fun chooseHorizon_everything_marksChosenWithNullBound() = runTest {
        val store = newStore()
        val vm = buildViewModel(store)
        vm.awaitState { !it.isLoading }

        vm.chooseHorizon(HorizonOption.EVERYTHING)

        val state = vm.awaitState { it.horizonChosen }
        assertTrue(state.horizonChosen)
        assertNull("Everything clears the stored bound", state.horizonStartMs)
    }

    private suspend fun InboxViewModel.awaitState(predicate: (InboxUiState) -> Boolean): InboxUiState = uiState.first(predicate)

    private companion object {
        const val NOW_MS = 1_752_300_000_000L
    }
}

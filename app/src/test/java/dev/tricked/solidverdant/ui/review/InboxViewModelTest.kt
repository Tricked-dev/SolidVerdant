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
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.domain.inbox.InboxIssueType
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
import kotlinx.coroutines.flow.MutableStateFlow
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

    /**
     * A minimal in-memory [InboxDismissalDao] whose observe flow re-emits on every write, so
     * `dismissDay`/`dismiss` actually re-drive `analyze()` in the collecting pipeline (a static
     * `flowOf` mock would swallow the write).
     */
    private class FakeDismissalDao : InboxDismissalDao {
        val rows = MutableStateFlow<List<InboxDismissalEntity>>(emptyList())
        override suspend fun upsert(dismissal: InboxDismissalEntity) {
            rows.value = rows.value.filterNot { it.issueKey == dismissal.issueKey } + dismissal
        }
        override suspend fun upsertAll(dismissals: List<InboxDismissalEntity>) {
            val keys = dismissals.map { it.issueKey }.toSet()
            rows.value = rows.value.filterNot { it.issueKey in keys } + dismissals
        }
        override fun observeDismissals(orgId: String) = rows
        override fun observeDismissedKeys(orgId: String) = MutableStateFlow(rows.value.map { it.issueKey })
        override fun observeDismissedCount(orgId: String) = MutableStateFlow(rows.value.size)
        override suspend fun deleteByKey(issueKey: String) {
            rows.value = rows.value.filterNot { it.issueKey == issueKey }
        }
        override suspend fun clearForOrg(orgId: String) {
            rows.value = emptyList()
        }
        override suspend fun clear() {
            rows.value = emptyList()
        }
    }

    private fun buildViewModel(
        store: InboxSettingsDataStore,
        entries: List<TimeEntry> = emptyList(),
        conflicts: List<TimeEntryRepository.SyncConflict> = emptyList(),
        dismissalDao: InboxDismissalDao = FakeDismissalDao(),
        nowMs: Long = NOW_MS,
    ): InboxViewModel {
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
        every { repo.observeTimeEntries(any()) } returns flowOf(entries)
        every { repo.observeConflicts(any()) } returns flowOf(conflicts)
        every { repo.observeProjects(any()) } returns flowOf(emptyList())
        every { repo.observeTasks(any()) } returns flowOf(emptyList())
        every { repo.observeTags(any()) } returns flowOf(emptyList())
        coEvery { repo.refreshAll(any(), any()) } returns Result.success(Unit)

        val syncTrigger = mockk<SyncTrigger>(relaxed = true)
        val clock = object : Clock {
            override fun nowMs(): Long = nowMs
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

    @Test
    fun dismissDay_persistsOnlyThatDaysDismissibleIssues_notConflictNorOtherDays() = runTest {
        val store = newStore().also { it.chooseEverythingForTest() }
        val dao = FakeDismissalDao()
        val vm = buildViewModel(
            store = store,
            entries = listOf(longEntry("A", DAY_A_START), longEntry("B", DAY_B_START)),
            conflicts = listOf(conflict(longEntry("C", DAY_A_START).copy(id = "conflictC"))),
            dismissalDao = dao,
        )

        val state = vm.awaitState { it.horizonChosen && it.issues.any { i -> i.type == InboxIssueType.LONG_DURATION } }
        val dayAIssues = state.issues.filter {
            java.time.Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() == DAY_A
        }
        val dayANonConflictKeys = dayAIssues
            .filterNot { it.type == InboxIssueType.CONFLICT }
            .map { it.key }
            .toSet()
        val conflictKey = state.issues.first { it.type == InboxIssueType.CONFLICT }.key
        val dayBKeys = state.issues
            .filter { java.time.Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() == DAY_B }
            .map { it.key }
            .toSet()

        // Pass the whole day-A group INCLUDING the conflict; the VM must drop the conflict.
        vm.dismissDay(dayAIssues)

        val dismissed = dao.rows.value.map { it.issueKey }.toSet()
        assertEquals("exactly day A's non-conflict keys are dismissed", dayANonConflictKeys, dismissed)
        assertFalse("conflict is never batch-dismissed", conflictKey in dismissed)
        dayBKeys.forEach { assertFalse("day B is untouched", it in dismissed) }
    }

    @Test
    fun dismissBefore_movesHorizon_soPreDayIssuesStayHiddenAcrossRetention() = runTest {
        val store = newStore().also { it.chooseEverythingForTest() }
        val entries = listOf(longEntry("A", DAY_A_START), longEntry("B", DAY_B_START))

        val vm = buildViewModel(store = store, entries = entries)
        vm.awaitState { it.issues.any { i -> i.type == InboxIssueType.LONG_DURATION } }

        // Move the horizon forward to the start of day B: day A's issue drops out immediately.
        vm.dismissBefore(DAY_B_START_OF_DAY)
        val afterMove = vm.awaitState { s ->
            s.issues.none { it.startMs < DAY_B_START_OF_DAY }
        }
        assertTrue("day A hidden after moving the horizon", afterMove.issues.none { it.startMs < DAY_B_START_OF_DAY })
        assertTrue("day B still present", afterMove.issues.any { it.startMs >= DAY_B_START_OF_DAY })

        // A fresh pipeline whose clock is far beyond the 45-day dismissal retention still hides day A:
        // the horizon (persisted in the store), not an expiring dismissal row, is what removes it.
        val vmFuture = buildViewModel(store = store, entries = entries, nowMs = FAR_FUTURE_NOW_MS)
        val futureState = vmFuture.awaitState { !it.isLoading && it.horizonChosen }
        assertTrue(
            "horizon durably hides pre-day-B issues even past dismissal retention",
            futureState.issues.none { it.startMs < DAY_B_START_OF_DAY },
        )
    }

    private fun longEntry(id: String, startMs: Long): TimeEntry {
        val start = java.time.Instant.ofEpochMilli(startMs)
        val end = start.plusSeconds(NINE_HOURS_SECONDS)
        return TimeEntry(
            id = id,
            userId = "u1",
            organizationId = "org1",
            start = start.atZone(zone).toOffsetDateTime().toString(),
            end = end.atZone(zone).toOffsetDateTime().toString(),
        )
    }

    private fun conflict(local: TimeEntry) =
        TimeEntryRepository.SyncConflict(local = local, server = null, serverDeleted = false, localDeleted = false)

    private suspend fun InboxSettingsDataStore.chooseEverythingForTest() = setHorizonStart(null)

    private suspend fun InboxViewModel.awaitState(predicate: (InboxUiState) -> Boolean): InboxUiState = uiState.first(predicate)

    private companion object {
        const val NOW_MS = 1_752_300_000_000L // 2025-07-12T06:00Z
        const val FAR_FUTURE_NOW_MS = NOW_MS + 60L * 24 * 3600 * 1000 // +60 days (> 45-day retention)
        const val NINE_HOURS_SECONDS = 9L * 3600
        const val DAY_A_START = 1_752_138_000_000L // 2025-07-10T09:00Z
        const val DAY_B_START = 1_752_224_400_000L // 2025-07-11T09:00Z
        const val DAY_B_START_OF_DAY = 1_752_192_000_000L // 2025-07-11T00:00Z
        val DAY_A: LocalDate = LocalDate.of(2025, 7, 10)
        val DAY_B: LocalDate = LocalDate.of(2025, 7, 11)
    }
}

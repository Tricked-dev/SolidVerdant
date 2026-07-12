/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.export.CsvExporter
import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.MembershipEntity
import dev.tricked.solidverdant.data.local.db.OrganizationEntity
import dev.tricked.solidverdant.data.local.db.SyncState
import dev.tricked.solidverdant.data.local.db.TimeEntryEntity
import dev.tricked.solidverdant.data.remote.FakeRemoteDataSource
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.domain.time.TemporalPolicyProvider
import dev.tricked.solidverdant.util.Clock
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/**
 * SV-009 regression: [StatisticsViewModel] must derive its current membership reactively from
 * Room (`catalogDao.observeMemberships()` + `authDataStore.currentMembershipId`) instead of the
 * previous one-shot `authRepository.getCurrentMembership()` network call. Opening Statistics
 * offline with cached catalogue/membership data must resolve immediately to that cached
 * membership rather than a false empty state, and the membership-derived scope must re-emit when
 * the persisted membership id changes (org switch) — mirroring
 * [dev.tricked.solidverdant.ui.templates.ManageTemplatesViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StatisticsMembershipTest {

    private lateinit var db: AppDatabase
    private lateinit var authDataStore: AuthDataStore
    private lateinit var authRepository: AuthRepository
    private lateinit var timeEntryRepository: TimeEntryRepository
    private lateinit var csvExporter: CsvExporter

    // No cached auth is seeded, so the provider yields its device-zone + Monday fallback — matching
    // the pre-policy ZoneId.systemDefault() behaviour these SV-009 assertions were written against.
    private lateinit var temporalPolicyProvider: TemporalPolicyProvider
    private val dispatcher = StandardTestDispatcher()

    // Every ViewModel built during a test is tracked so teardown can cancel its viewModelScope
    // before resetMain(). The VM launches coroutines on viewModelScope (Dispatchers.Main.immediate)
    // — the init{} policy collector and the uiState pipeline — that are NOT children of the runTest
    // scope, so they outlive the test body. That pipeline hops to real Dispatchers.IO/Default and
    // posts continuations back to Main; if one lands while @After runs resetMain(), the run fails
    // intermittently with "Dispatchers.Main is used concurrently with setting it". Cancelling the
    // scope first makes teardown deterministic.
    private val viewModels = mutableListOf<StatisticsViewModel>()

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        authDataStore = AuthDataStore(context)
        temporalPolicyProvider = TemporalPolicyProvider(
            dev.tricked.solidverdant.data.local.SettingsDataStore(context),
        )

        // AuthRepository is only used by StatisticsViewModel for the best-effort CSV org-name
        // lookup and the bounded server refresh of the selected range. Both are stubbed to fail,
        // as if the app were fully offline / the network call cannot succeed — this is exactly
        // the scenario SV-009 fixes: membership resolution must NOT depend on either succeeding.
        authRepository = mockk(relaxed = true)
        coEvery { authRepository.getCurrentMembership() } returns null
        coEvery { authRepository.getTimeEntries(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(java.io.IOException("offline"))

        timeEntryRepository = TimeEntryRepository(
            db.timeEntryDao(),
            db.catalogDao(),
            db.outboxDao(),
            db.syncMetaDao(),
            FakeRemoteDataSource(),
            object : Clock {
                override fun nowMs() = 1L
            },
            Json { encodeDefaults = true },
            db,
        )
        csvExporter = mockk(relaxed = true)
    }

    @After
    fun teardown() {
        // Cancel every VM's viewModelScope and drain the test scheduler so no Main-bound coroutine
        // (the init policy collector or a uiState continuation returning from Dispatchers.IO/Default)
        // is still active when resetMain() runs.
        viewModels.forEach { it.cancelScopeForTest() }
        dispatcher.scheduler.advanceUntilIdle()
        db.close()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun viewModel() = StatisticsViewModel(
        authRepository = authRepository,
        timeEntryRepository = timeEntryRepository,
        csvExporter = csvExporter,
        authDataStore = authDataStore,
        catalogDao = db.catalogDao(),
        temporalPolicyProvider = temporalPolicyProvider,
    ).also { viewModels += it }

    /** Seeds a cached membership plus one cached, already-synced time entry today (within [StatRange.ThisWeek]). */
    private suspend fun seedMembership(membershipId: String, orgId: String) {
        db.catalogDao().upsertOrganizations(
            listOf(OrganizationEntity(id = orgId, name = "Acme $orgId", currency = "USD")),
        )
        db.catalogDao().upsertMemberships(listOf(MembershipEntity(id = membershipId, role = "member", organizationId = orgId)))
        val now = LocalDate.now(ZoneId.systemDefault()).atTime(9, 0).atZone(ZoneId.systemDefault())
        db.timeEntryDao().upsert(
            TimeEntryEntity(
                id = "cached-$orgId",
                description = "cached work",
                userId = membershipId,
                start = now.toInstant().toString(),
                end = now.plusHours(1).toInstant().toString(),
                duration = 3600,
                taskId = null,
                projectId = null,
                billable = false,
                organizationId = orgId,
                updatedAt = 1L,
                syncState = SyncState.SYNCED,
                pendingDelete = false,
            ),
        )
    }

    @Test
    fun offline_membership_from_cache_resolves_scope_without_a_false_empty_state() = runTest(dispatcher.scheduler) {
        // Seed Room with exactly one cached membership and persist it as the selected membership,
        // simulating a previously-synced device that is now offline.
        seedMembership(membershipId = "m1", orgId = "org1")
        authDataStore.saveCurrentMembershipId("m1")

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.first { !it.isLoading }

        // Before SV-009 this depended on a one-shot network call (authRepository.getCurrentMembership(),
        // stubbed above to return null / fail) and would have surfaced isEmpty = true purely because
        // the network was unavailable, even though a membership was cached. The reactive combine()
        // must resolve the membership from Room instead.
        assertFalse("Statistics should not show the false-empty state when a membership is cached", state.isEmpty)
        assertEquals(false, state.isLoading)
        // rangeStart/rangeEnd resolve only once a membership scopes the flatMapLatest chain, so a
        // non-null range corroborates that the cached membership (not the null branch) was used.
        assertEquals(true, state.rangeStart != null && state.rangeEnd != null)
    }

    @Test
    fun switching_current_membership_id_re_scopes_statistics_without_recreating_the_view_model() = runTest(dispatcher.scheduler) {
        seedMembership(membershipId = "m1", orgId = "org1")
        seedMembership(membershipId = "m2", orgId = "org2")
        authDataStore.saveCurrentMembershipId("m1")

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val firstState = vm.uiState.first { !it.isLoading }
        assertFalse(firstState.isEmpty)

        // Switch the persisted membership id (e.g. the user switched organizations elsewhere in
        // the app) and confirm the same ViewModel instance re-derives its scope reactively.
        authDataStore.saveCurrentMembershipId("m2")
        dispatcher.scheduler.advanceUntilIdle()

        val secondState = vm.uiState.first { !it.isLoading }
        assertFalse("Re-scoped membership must still resolve to non-empty cached data", secondState.isEmpty)
    }

    @Test
    fun no_cached_membership_yields_explicit_empty_state_not_a_crash() = runTest(dispatcher.scheduler) {
        // No memberships seeded and no persisted id: membershipFlow emits null, and the ViewModel
        // must fall back to an explicit, deliberate empty state rather than hanging or crashing.
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(true, state.isEmpty)
    }
}

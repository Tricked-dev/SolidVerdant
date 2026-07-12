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
import dev.tricked.solidverdant.data.local.db.ProjectEntity
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Statistics UI state must surface project estimate budgets ([EstimateProgress]) derived
 * reactively from the cached project catalogue and the active filters — no new persistence, no
 * server call. Uses the server-authoritative project spentTime vs estimatedTime.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StatisticsEstimatesVmTest {

    private lateinit var db: AppDatabase
    private lateinit var authDataStore: AuthDataStore
    private lateinit var authRepository: AuthRepository
    private lateinit var timeEntryRepository: TimeEntryRepository
    private lateinit var csvExporter: CsvExporter
    private lateinit var temporalPolicyProvider: TemporalPolicyProvider
    private val dispatcher = StandardTestDispatcher()
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

    private suspend fun seed(orgId: String, memberId: String) {
        db.catalogDao().upsertOrganizations(
            listOf(OrganizationEntity(id = orgId, name = "Acme", currency = "USD")),
        )
        db.catalogDao().upsertMemberships(listOf(MembershipEntity(id = memberId, role = "member", organizationId = orgId)))
        db.catalogDao().upsertProjects(
            listOf(
                ProjectEntity(
                    id = "p-est",
                    name = "Estimated",
                    color = "#f00",
                    clientId = null,
                    isArchived = false,
                    billableRate = null,
                    isBillable = false,
                    estimatedTime = 8 * 3600,
                    spentTime = 2 * 3600,
                    isPublic = false,
                    organizationId = orgId,
                ),
                ProjectEntity(
                    id = "p-none",
                    name = "No estimate",
                    color = "#0f0",
                    clientId = null,
                    isArchived = false,
                    billableRate = null,
                    isBillable = false,
                    estimatedTime = null,
                    spentTime = 3600,
                    isPublic = false,
                    organizationId = orgId,
                ),
            ),
        )
    }

    @Test
    fun ui_state_exposes_only_estimated_projects_with_authoritative_spent() = runTest(dispatcher.scheduler) {
        seed(orgId = "org1", memberId = "m1")
        authDataStore.saveCurrentMembershipId("m1")

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.first { !it.isLoading && it.estimateProgress.isNotEmpty() }
        assertEquals(listOf("p-est"), state.estimateProgress.map { it.id })
        val ep = state.estimateProgress.first()
        assertEquals(8 * 3600, ep.estimatedSeconds)
        assertEquals(2 * 3600, ep.spentSeconds)
        assertTrue(ep.fraction in 0.24f..0.26f)
    }

    @Test
    fun project_filter_restricts_the_estimate_section() = runTest(dispatcher.scheduler) {
        seed(orgId = "org1", memberId = "m1")
        authDataStore.saveCurrentMembershipId("m1")

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { !it.isLoading && it.estimateProgress.isNotEmpty() }

        // Filter to a project that carries no estimate -> the estimate section empties out.
        vm.setFilters(StatFilters(projectIds = setOf("p-none")))
        dispatcher.scheduler.advanceUntilIdle()

        val filtered = vm.uiState.first { it.filters.projectIds == setOf("p-none") }
        assertTrue(filtered.estimateProgress.isEmpty())
    }
}

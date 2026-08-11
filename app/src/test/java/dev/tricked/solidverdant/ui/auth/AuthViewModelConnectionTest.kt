/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.UserCacheCleaner
import dev.tricked.solidverdant.data.remote.ConnectionTestCode
import dev.tricked.solidverdant.data.remote.ConnectionTestResult
import dev.tricked.solidverdant.data.remote.ConnectionTester
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.data.repository.TemplateRepository
import dev.tricked.solidverdant.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthViewModelConnectionTest {
    private lateinit var dispatcher: TestDispatcher

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun repeated_connection_test_taps_do_not_start_a_second_request() = runTest(dispatcher.scheduler) {
        val response = CompletableDeferred<ConnectionTestResult>()
        val connectionTester = mockk<ConnectionTester>()
        coEvery { connectionTester.test("https://example.test", "client") } coAnswers { response.await() }
        val viewModel = viewModel(connectionTester)

        viewModel.testConnection("https://example.test/", "client")
        viewModel.testConnection("https://example.test/", "client")
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) { connectionTester.test("https://example.test", "client") }
        assertTrue(viewModel.configState.value.isTesting)

        response.complete(ConnectionTestResult(ConnectionTestCode.READY, httpStatus = 200))
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.configState.value.isTesting)
        assertEquals(true, viewModel.configState.value.testSuccess)
    }

    @Test
    fun failed_connection_test_releases_the_guard_for_a_retry() = runTest(dispatcher.scheduler) {
        val connectionTester = mockk<ConnectionTester>()
        coEvery { connectionTester.test("https://example.test", "client") } returnsMany listOf(
            ConnectionTestResult(ConnectionTestCode.TEST_FAILED),
            ConnectionTestResult(ConnectionTestCode.READY, httpStatus = 200),
        )
        val viewModel = viewModel(connectionTester)

        viewModel.testConnection("https://example.test", "client")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.configState.value.testSuccess)
        assertFalse(viewModel.configState.value.isTesting)

        viewModel.testConnection("https://example.test", "client")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.configState.value.testSuccess)
        coVerify(exactly = 2) { connectionTester.test("https://example.test", "client") }
    }

    private fun viewModel(connectionTester: ConnectionTester): AuthViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authRepository = mockk<AuthRepository>(relaxed = true) {
            every { isLoggedIn } returns flowOf(false)
            every { endpoint } returns flowOf("https://default.example")
            every { clientId } returns flowOf("default-client")
        }
        return AuthViewModel(
            authRepository = authRepository,
            userCacheCleaner = mockk<UserCacheCleaner>(relaxed = true),
            settingsDataStore = mockk<SettingsDataStore>(relaxed = true),
            templateRepository = mockk<TemplateRepository>(relaxed = true),
            connectionTester = connectionTester,
            syncScheduler = mockk<SyncScheduler>(relaxed = true),
            context = context,
        )
    }
}

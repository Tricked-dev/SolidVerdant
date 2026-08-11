/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.auth

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.UserCacheCleaner
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Organization
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.data.remote.ConnectionTester
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.data.repository.TemplateRepository
import dev.tricked.solidverdant.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthViewModelLogoutTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        notificationManager.cancelAll()
        context.getSharedPreferences(NOTIFICATION_STATE_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun logout_clears_account_data_and_every_timer_surface_before_auth_state_changes() {
        val authRepository = mockk<AuthRepository>(relaxed = true) {
            every { isLoggedIn } returns flowOf(true)
            every { endpoint } returns flowOf("https://example.invalid")
            every { clientId } returns flowOf("client")
            coEvery { logout() } coAnswers {
                assertFalse(
                    "Paused notification state must be gone before credentials are cleared",
                    context.getSharedPreferences(NOTIFICATION_STATE_PREFERENCES, Context.MODE_PRIVATE)
                        .contains(PAUSED_START_PREFERENCE),
                )
                assertNull(notificationManager.getActiveNotifications().firstOrNull { it.id == TRACKING_NOTIFICATION_ID })
                Unit
            }
        }
        val cacheCleaner = mockk<UserCacheCleaner>(relaxed = true)
        val settings = mockk<SettingsDataStore>(relaxed = true)
        val syncScheduler = mockk<SyncScheduler>(relaxed = true)
        val viewModel = AuthViewModel(
            authRepository = authRepository,
            userCacheCleaner = cacheCleaner,
            settingsDataStore = settings,
            templateRepository = mockk<TemplateRepository>(relaxed = true),
            connectionTester = mockk<ConnectionTester>(relaxed = true),
            syncScheduler = syncScheduler,
            context = context,
        )
        context.getSharedPreferences(NOTIFICATION_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putLong(PAUSED_START_PREFERENCE, 123L)
            .commit()
        notificationManager.notify(
            TRACKING_NOTIFICATION_ID,
            NotificationCompat.Builder(context, "test")
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("private test surface")
                .build(),
        )
        notificationManager.notify(
            ERROR_NOTIFICATION_ID,
            NotificationCompat.Builder(context, "test")
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("private error surface")
                .build(),
        )

        viewModel.logout()
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { syncScheduler.cancelSync() }
        coVerify(exactly = 1) { cacheCleaner.clear() }
        coVerify(exactly = 1) { settings.clearLongTimerWarningDeadline() }
        coVerify(exactly = 1) { authRepository.logout() }
        assertFalse(
            context.getSharedPreferences(NOTIFICATION_STATE_PREFERENCES, Context.MODE_PRIVATE)
                .contains(PAUSED_START_PREFERENCE),
        )
        assertNull(notificationManager.getActiveNotifications().firstOrNull { it.id == TRACKING_NOTIFICATION_ID })
        assertNull(notificationManager.getActiveNotifications().firstOrNull { it.id == ERROR_NOTIFICATION_ID })
    }

    @Test
    fun logout_cancels_an_in_flight_user_load_without_repopulating_the_logged_out_state() = runTest(dispatcher.scheduler) {
        val user = User(id = "user-1", name = "User", email = "user@example.invalid")
        val membership = Membership(
            id = "membership-1",
            role = "member",
            organization = Organization("organization-1", "Organization", "EUR"),
        )
        val loadStarted = CompletableDeferred<Unit>()
        val releaseLoad = CompletableDeferred<Result<User>>()
        val authRepository = mockk<AuthRepository>(relaxed = true) {
            every { isLoggedIn } returns flowOf(true)
            every { endpoint } returns flowOf("https://example.invalid")
            every { clientId } returns flowOf("client")
            coEvery { getCurrentUser() } coAnswers {
                loadStarted.complete(Unit)
                kotlinx.coroutines.withContext(NonCancellable) { releaseLoad.await() }
            }
            coEvery { getMyMemberships() } returns Result.success(listOf(membership))
        }
        val viewModel = AuthViewModel(
            authRepository = authRepository,
            userCacheCleaner = mockk<UserCacheCleaner>(relaxed = true),
            settingsDataStore = mockk<SettingsDataStore>(relaxed = true),
            templateRepository = mockk<TemplateRepository>(relaxed = true),
            connectionTester = mockk<ConnectionTester>(relaxed = true),
            syncScheduler = mockk<SyncScheduler>(relaxed = true),
            context = context,
        )

        viewModel.loadUserData()
        loadStarted.await()
        viewModel.logout()
        dispatcher.scheduler.advanceUntilIdle()

        releaseLoad.complete(Result.success(user))
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.user)
        assertEquals(emptyList<Membership>(), viewModel.uiState.value.memberships)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private companion object {
        const val TRACKING_NOTIFICATION_ID = 1001
        const val ERROR_NOTIFICATION_ID = 1002
        const val NOTIFICATION_STATE_PREFERENCES = "time_tracking_notification_state"
        const val PAUSED_START_PREFERENCE = "paused_start_epoch_ms"
    }
}

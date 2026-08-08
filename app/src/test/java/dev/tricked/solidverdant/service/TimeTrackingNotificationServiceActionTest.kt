/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TimeTrackingNotificationServiceActionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun repeated_pause_actions_do_not_start_a_second_mutation_while_the_first_is_in_flight() {
        val authRepository = mockk<AuthRepository>()
        val lookupStarted = CompletableDeferred<Unit>()
        val releaseLookup = CompletableDeferred<Unit>()
        coEvery { authRepository.getActiveTimeEntry() } coAnswers {
            lookupStarted.complete(Unit)
            releaseLookup.await()
            Result.failure(RuntimeException("test cancellation"))
        }

        val service = Robolectric.buildService(TimeTrackingNotificationService::class.java)
            .create()
            .get()
            .also { it.authRepository = authRepository }

        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_PAUSE_TRACKING), 0, 1)
        assertTrue("The first pause action should reach the repository", lookupStarted.isCompleted)

        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_PAUSE_TRACKING), 0, 2)
        coVerify(exactly = 1) { authRepository.getActiveTimeEntry() }

        releaseLookup.complete(Unit)
    }

    @Test
    fun failed_pause_action_allows_a_subsequent_retry() {
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.getActiveTimeEntry() } returns Result.failure(
            RuntimeException("test network failure"),
        )

        val service = Robolectric.buildService(TimeTrackingNotificationService::class.java)
            .create()
            .get()
            .also { it.authRepository = authRepository }

        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_PAUSE_TRACKING), 0, 1)
        service.onStartCommand(actionIntent(TimeTrackingNotificationService.ACTION_PAUSE_TRACKING), 0, 2)

        coVerify(exactly = 2) { authRepository.getActiveTimeEntry() }
    }

    @Test
    fun failed_resume_action_allows_a_subsequent_retry() {
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.getCurrentUser() } returns Result.failure(
            RuntimeException("test network failure"),
        )

        val service = Robolectric.buildService(TimeTrackingNotificationService::class.java)
            .create()
            .get()
            .also { it.authRepository = authRepository }
        val resumeIntent = actionIntent(TimeTrackingNotificationService.ACTION_RESUME_TRACKING)

        service.onStartCommand(resumeIntent, 0, 1)
        service.onStartCommand(resumeIntent, 0, 2)

        coVerify(exactly = 2) { authRepository.getCurrentUser() }
    }

    private fun actionIntent(action: String) = Intent(
        context,
        TimeTrackingNotificationService::class.java,
    ).apply {
        this.action = action
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.SettingsDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LongTimerWarningSchedulingTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun repeated_start_for_same_entry_preserves_existing_warning_work() {
        val service = createService()
        val entryStart = System.currentTimeMillis()

        service.onStartCommand(startIntent(entryStart, description = "original"), 0, 1)
        val originalWorkId = currentWarningWorkId()

        service.onStartCommand(startIntent(entryStart, description = "refreshed metadata"), 0, 2)

        assertEquals(originalWorkId, currentWarningWorkId())
    }

    @Test
    fun metadata_refresh_does_not_rearm_warning_after_it_is_visible() {
        val service = createService()
        val entryStart = System.currentTimeMillis()
        service.onStartCommand(startIntent(entryStart), 0, 1)
        WorkManager.getInstance(context)
            .cancelUniqueWork(LongTimerWarningWorker.UNIQUE_WORK_NAME)
            .result
            .get()

        val showWarningIntent = Intent(context, TimeTrackingNotificationService::class.java).apply {
            action = TimeTrackingNotificationService.ACTION_SHOW_LONG_TIMER_WARNING
            putExtra(TimeTrackingNotificationService.EXTRA_ENTRY_START_EPOCH_MS, entryStart)
        }
        service.onStartCommand(showWarningIntent, 0, 2)
        service.onStartCommand(startIntent(entryStart, description = "refreshed metadata"), 0, 3)

        val activeWork = warningWork().filterNot { it.state.isFinished }
        assertTrue("A visible warning must not be rearmed by a metadata refresh", activeWork.isEmpty())
        assertTrue(
            shadowOf(service).lastForegroundNotification.actions.any {
                it.title == context.getString(R.string.keep_running)
            },
        )
    }

    @Test
    fun start_for_new_entry_replaces_existing_warning_work() {
        val service = createService()
        val firstEntryStart = System.currentTimeMillis()

        service.onStartCommand(startIntent(firstEntryStart), 0, 1)
        val originalWorkId = currentWarningWorkId()

        service.onStartCommand(startIntent(firstEntryStart + 1_000), 0, 2)

        assertNotEquals(originalWorkId, currentWarningWorkId())
    }

    @Test
    fun fresh_service_process_schedules_warning_for_same_entry() {
        val entryStart = System.currentTimeMillis()
        val firstController = Robolectric.buildService(TimeTrackingNotificationService::class.java).create()
        firstController.get().settingsDataStore = fakeSettingsDataStore()
        firstController.get().onStartCommand(startIntent(entryStart), 0, 1)
        val originalWorkId = currentWarningWorkId()
        firstController.destroy()

        val restoredService = createService()
        restoredService.onStartCommand(startIntent(entryStart), 0, 2)

        assertNotEquals(originalWorkId, currentWarningWorkId())
    }

    private fun startIntent(entryStart: Long, description: String = "work") = Intent(
        context,
        TimeTrackingNotificationService::class.java,
    ).apply {
        action = TimeTrackingNotificationService.ACTION_START_TRACKING
        putExtra(TimeTrackingNotificationService.EXTRA_START_TIME, entryStart)
        putExtra(TimeTrackingNotificationService.EXTRA_DESCRIPTION, description)
    }

    private fun createService() = Robolectric.buildService(TimeTrackingNotificationService::class.java)
        .create()
        .get()
        .also { it.settingsDataStore = fakeSettingsDataStore() }

    private fun fakeSettingsDataStore() = mockk<SettingsDataStore>(relaxed = true) {
        every { longTimerHours } returns flowOf(8)
    }

    private fun warningWork(): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(LongTimerWarningWorker.UNIQUE_WORK_NAME)
        .get()

    private fun currentWarningWorkId() = warningWork()
        .single()
        .id
}

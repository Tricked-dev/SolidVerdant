/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * SV-016: the forgotten-timer warning must be durable across process death. Rather than living in
 * an in-memory `delay()`, [TimeTrackingNotificationService] (via [dev.tricked.solidverdant.service.LongTimerWarningWorker])
 * persists the next warning deadline in [SettingsDataStore] so it survives the service process
 * being killed. This verifies that persistence round-trips through the real DataStore-backed
 * SettingsDataStore, using a Robolectric application context the same way other DataStore-backed
 * tests in this codebase do (SettingsDataStore only needs a Context - no Hilt component required).
 */
@RunWith(RobolectricTestRunner::class)
class SettingsDataStoreWarningDeadlineTest {

    private val settingsDataStore = SettingsDataStore(ApplicationProvider.getApplicationContext())

    // The DataStore is file-backed under a fixed name, so state persists across tests in this class.
    // Clear it before each test so the "no deadline yet" precondition is deterministic regardless of
    // test execution order.
    @Before fun clearDeadline() = runBlocking { settingsDataStore.clearLongTimerWarningDeadline() }

    @Test
    fun deadline_round_trips_through_settings_data_store() = runTest {
        val deadlineEpochMs = 1_752_000_000_000L
        val entryStartEpochMs = 1_751_990_000_000L

        assertNull(
            "Precondition: no deadline persisted yet",
            settingsDataStore.longTimerWarningDeadline.first(),
        )

        settingsDataStore.setLongTimerWarningDeadline(
            deadlineEpochMs = deadlineEpochMs,
            entryStartEpochMs = entryStartEpochMs,
        )

        val stored = settingsDataStore.longTimerWarningDeadline.first()
        assertEquals(deadlineEpochMs, stored?.deadlineEpochMs)
        assertEquals(entryStartEpochMs, stored?.entryStartEpochMs)

        settingsDataStore.clearLongTimerWarningDeadline()

        assertNull(
            "Deadline must be gone after clearLongTimerWarningDeadline()",
            settingsDataStore.longTimerWarningDeadline.first(),
        )
    }

    @Test
    fun setting_a_new_deadline_replaces_the_previous_one() = runTest {
        settingsDataStore.setLongTimerWarningDeadline(deadlineEpochMs = 100L, entryStartEpochMs = 10L)
        settingsDataStore.setLongTimerWarningDeadline(deadlineEpochMs = 200L, entryStartEpochMs = 20L)

        val stored = settingsDataStore.longTimerWarningDeadline.first()
        assertEquals(200L, stored?.deadlineEpochMs)
        assertEquals(20L, stored?.entryStartEpochMs)
    }
}

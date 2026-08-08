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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsDataStoreLiveUpdateTest {

    private val settingsDataStore = SettingsDataStore(ApplicationProvider.getApplicationContext())

    @Before
    fun resetSetting() = runBlocking {
        settingsDataStore.setLiveUpdateEnabled(false)
    }

    @Test
    fun live_updates_are_disabled_by_default() = runTest {
        assertFalse(settingsDataStore.liveUpdateEnabled.first())
    }

    @Test
    fun live_updates_setting_round_trips() = runTest {
        settingsDataStore.setLiveUpdateEnabled(true)
        assertTrue(settingsDataStore.liveUpdateEnabled.first())

        settingsDataStore.setLiveUpdateEnabled(false)
        assertFalse(settingsDataStore.liveUpdateEnabled.first())
    }
}

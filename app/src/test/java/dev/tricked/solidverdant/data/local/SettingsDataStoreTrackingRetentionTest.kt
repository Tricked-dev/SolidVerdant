/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsDataStoreTrackingRetentionTest {

    private val settingsDataStore = SettingsDataStore(ApplicationProvider.getApplicationContext())

    @Test
    fun auto_clear_entry_fields_setting_round_trips_through_flow_and_first_frame_cache() = runTest {
        settingsDataStore.setAutoClearEntryFieldsAfterStop(false)
        assertFalse(settingsDataStore.autoClearEntryFieldsAfterStop.first())
        assertFalse(settingsDataStore.getCachedAutoClearEntryFieldsAfterStop())

        settingsDataStore.setAutoClearEntryFieldsAfterStop(true)
        assertTrue(settingsDataStore.autoClearEntryFieldsAfterStop.first())
        assertTrue(settingsDataStore.getCachedAutoClearEntryFieldsAfterStop())
    }

    @Test
    fun clear_description_setting_round_trips_through_flow_and_first_frame_cache() = runTest {
        settingsDataStore.setClearDescriptionAfterStop(true)
        assertTrue(settingsDataStore.clearDescriptionAfterStop.first())
        assertTrue(settingsDataStore.getCachedClearDescriptionAfterStop())

        settingsDataStore.setClearDescriptionAfterStop(false)
        assertFalse(settingsDataStore.clearDescriptionAfterStop.first())
        assertFalse(settingsDataStore.getCachedClearDescriptionAfterStop())
    }
}

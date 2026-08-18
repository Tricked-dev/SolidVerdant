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
    fun keep_entry_fields_setting_round_trips_through_flow_and_first_frame_cache() = runTest {
        settingsDataStore.setKeepEntryFieldsAfterStop(false)
        assertFalse(settingsDataStore.keepEntryFieldsAfterStop.first())
        assertFalse(settingsDataStore.getCachedKeepEntryFieldsAfterStop())

        settingsDataStore.setKeepEntryFieldsAfterStop(true)
        assertTrue(settingsDataStore.keepEntryFieldsAfterStop.first())
        assertTrue(settingsDataStore.getCachedKeepEntryFieldsAfterStop())
    }
}

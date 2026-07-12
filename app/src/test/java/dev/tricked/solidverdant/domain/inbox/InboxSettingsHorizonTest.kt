/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.domain.inbox

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * SV-005: the inbox horizon choice must round-trip through the real DataStore-backed store so the
 * analyzer (and thus the badge) obeys it after a restart. Uses a Robolectric application context the
 * same way the other DataStore-backed tests in this codebase do.
 */
@RunWith(RobolectricTestRunner::class)
class InboxSettingsHorizonTest {

    private val store = InboxSettingsDataStore(ApplicationProvider.getApplicationContext())

    @Test
    fun setHorizonStart_with_value_marks_chosen_and_persists_the_bound() = runTest {
        val bound = 1_752_000_000_000L
        store.setHorizonStart(bound)

        val settings = store.settings.first()
        assertTrue(settings.horizonChosen)
        assertEquals(bound, settings.horizonStartMs)
    }

    @Test
    fun setHorizonStart_null_marks_chosen_and_removes_the_bound() = runTest {
        // First store a real bound, then switch to "Everything".
        store.setHorizonStart(123L)
        store.setHorizonStart(null)

        val settings = store.settings.first()
        assertTrue("choosing Everything still counts as chosen", settings.horizonChosen)
        assertNull("Everything removes the stored bound rather than storing a sentinel", settings.horizonStartMs)
    }
}

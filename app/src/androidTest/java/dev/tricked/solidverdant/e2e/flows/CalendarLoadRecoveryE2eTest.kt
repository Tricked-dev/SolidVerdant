/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.flows

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarLoadRecoveryE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun calendar_load_failure_can_retry_without_leaving_an_error_state() {
        val server = e2e.requireMockBackend()
        e2e.prepare(E2eFixture.Empty)
        server.setTimeEntriesRequestsFailing(true)
        e2e.launchApp()

        e2e.composeRule.onNodeWithTag("main_nav_calendar", useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.CALENDAR_LOAD_ERROR), WAIT_MS)
        val failedRequestCount = server.callsMatching("GET", "/time-entries").size

        server.setTimeEntriesRequestsFailing(false)
        e2e.composeRule.onNodeWithText(context.getString(R.string.retry), useUnmergedTree = true).performClick()
        e2e.composeRule.waitUntilDoesNotExist(hasTestTag(TestTags.CALENDAR_LOAD_ERROR), WAIT_MS)

        assertTrue(
            "Retry must issue a fresh time-entry request",
            server.callsMatching("GET", "/time-entries").size > failedRequestCount,
        )
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

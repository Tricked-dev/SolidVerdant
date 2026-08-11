/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.flows

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.MainActivity
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarDeepLinkE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @BackendPortable
    @Test
    fun calendarDateLinkOpensTheRequestedDay() {
        e2e.prepare(E2eFixture.Empty)
        val target = Instant.ofEpochMilli(e2e.testClock.nowMs())
            .atZone(e2e.session.zone)
            .toLocalDate()
            .plusDays(10)
        e2e.launchApp(
            Intent(
                ApplicationProvider.getApplicationContext(),
                MainActivity::class.java,
            ).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("solidtime://calendar?date=$target")
            },
        )

        e2e.composeRule.waitUntil(WAIT_MS) {
            e2e.composeRule.onAllNodes(
                hasTestTag("week-day-header-$target"),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    companion object {
        private const val WAIT_MS = 15_000L
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.flows

import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SyncDetailsNavigationE2eTest {
    @get:Rule
    val e2e = E2eRule(this)

    @Test
    fun compactTrackSyncSummaryOpensSyncAndRecoveryScreen() {
        val fixture = e2e.prepare(
            E2eFixture.Completed(
                e2e.completedFixtureEntry(
                    logicalId = "sync-details-entry",
                    start = Instant.now().minusSeconds(3_600),
                ),
            ),
        )
        e2e.launchApp()

        val track = TrackRobot(e2e.composeRule).waitForHistory()
        e2e.seedFailedSync(requireNotNull(fixture.serverId))
        e2e.composeRule.waitUntilAtLeastOneExists(hasTestTag(TestTags.TRACK_SYNC_STATUS_CARD), WAIT_MS)
        track.openSyncDetails().closeSyncDetails()
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}

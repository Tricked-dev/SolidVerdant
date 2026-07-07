/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.mock.MockSolidtimeServer
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Cross-device continuity: a timer started elsewhere (server reports an active entry) must show
 * up as the running timer when this device launches, without any local interaction — and stopping
 * it here must work against that server entry.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ActiveEntryOnLaunchE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @Test
    fun serverSideActiveEntryShowsAsRunningTimerOnLaunch() {
        e2e.mockServer.presetLoggedInWorld(seededEntry = null)
        val remoteActive = TimeEntry(
            id = "remote-active-1",
            description = "Started on the desktop",
            userId = MockSolidtimeServer.DEFAULT_USER_ID,
            start = Instant.ofEpochMilli(e2e.testClock.nowMs).minusSeconds(600).toString(),
            end = null,
            organizationId = MockSolidtimeServer.DEFAULT_ORG_ID,
        )
        e2e.mockServer.activeEntry = remoteActive
        e2e.mockServer.addTimeEntry(remoteActive)

        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule).waitForHistory()

        // Running state restored purely from the server's active entry.
        robot.assertStopButtonVisible()

        // And the remote timer is stoppable from this device.
        robot.tapStop().assertStartButtonVisible()
    }
}

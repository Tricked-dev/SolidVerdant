/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.mock.MockSolidtimeServer
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walking-skeleton E2E: with the harness seeded logged-in and MockWebServer serving a user, one
 * membership, and one completed time entry, launch the real app and assert the seeded entry lands in
 * the Track history.
 *
 * This exercises the full boot path: AuthDataStore-seeded session -> real ViewModels ->
 * dynamic-base-URL Retrofit -> MockWebServer -> Room -> Compose history.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppLaunchE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @Test
    fun seededEntryAppearsInHistory() {
        e2e.mockServer.presetLoggedInWorld(
            seededEntry = MockSolidtimeServer.defaultCompletedEntry(description = "Seeded work"),
        )

        e2e.launchApp()

        TrackRobot(e2e.composeRule)
            .waitForHistory()
            .assertEntryVisible("Seeded work")
    }
}

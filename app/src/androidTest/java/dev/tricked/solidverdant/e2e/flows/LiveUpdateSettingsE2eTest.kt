/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import dev.tricked.solidverdant.service.LIVE_UPDATES_API_LEVEL
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that Android 16+ users can discover the Live Update timer setting in the drawer. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = LIVE_UPDATES_API_LEVEL)
class LiveUpdateSettingsE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @BackendPortable
    @Test
    fun liveUpdateSettingIsDiscoverableOnAndroid16() {
        e2e.prepare(E2eFixture.Empty)
        e2e.launchApp()

        TrackRobot(e2e.composeRule)
            .waitForHistory()
            .openSettings()
            .assertLiveUpdateSettingVisible()
    }
}

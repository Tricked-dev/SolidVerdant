package dev.tricked.solidverdant.e2e.flows

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that logout from the settings drawer returns a logged-in session to the login screen. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LogoutE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @Test
    fun logoutReturnsToLoginScreen() {
        e2e.mockServer.presetLoggedInWorld(seededEntry = null)
        e2e.launchApp()

        TrackRobot(e2e.composeRule)
            .waitForHistory()
            .openSettings()
            .logout()
            .assertLoginVisible()
    }
}

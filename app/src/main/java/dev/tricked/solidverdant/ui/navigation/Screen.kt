/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import dev.tricked.solidverdant.R

sealed class Screen(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Track : Screen("track", R.string.nav_track, Icons.Outlined.Timer)
    data object Calendar : Screen("calendar", R.string.nav_calendar, Icons.Outlined.CalendarMonth)
    data object Stats : Screen("stats", R.string.nav_stats, Icons.Outlined.BarChart)

    /** Review-loop home (Time Inbox + end-of-day review). Fourth bottom-nav destination. */
    data object Review : Screen("review", R.string.nav_review, Icons.Outlined.Inbox)
}

val bottomNavScreens: List<Screen> =
    listOf(Screen.Track, Screen.Calendar, Screen.Stats, Screen.Review)

/**
 * Routes for review-loop destinations that are pushed on top of the bottom-nav graph rather than
 * being tabs themselves. They are reached from the Review tab, its overflow menu, or a reminder /
 * end-of-day notification, and each renders full-screen with its own back navigation.
 */
object ReviewRoutes {
    /** Compact end-of-day review flow (opened from the end-of-day notification). */
    const val END_OF_DAY: String = "review/end_of_day"

    /** Tracking-reminder + end-of-day reminder configuration. */
    const val REMINDER_SETTINGS: String = "review/reminders"

    /** Manage reusable entry templates / favorites. */
    const val MANAGE_TEMPLATES: String = "templates/manage"
}

/**
 * Routes for the sync surface (#33). The dedicated Sync Center is pushed full-screen on top of the
 * tab graph with its own back navigation, reached from the Track screen's sync summary.
 */
object SyncRoutes {
    /** Dedicated Sync Center: freshness, pending changes, failures + retry/discard. */
    const val SYNC_CENTER: String = "sync/center"
}

/**
 * Routes for the settings surface. The privacy & data-management screen (#48) is pushed full-screen
 * on top of the tab graph with its own back navigation, reached from the Track screen's settings
 * drawer.
 */
object SettingsRoutes {
    /** Privacy & data-management: what is stored/sent, token protection, permissions, data controls. */
    const val PRIVACY: String = "settings/privacy"
}

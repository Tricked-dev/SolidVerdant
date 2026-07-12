/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.privacy.PrivacyScreen
import dev.tricked.solidverdant.ui.review.ReminderSettingsScreen
import dev.tricked.solidverdant.ui.review.ReviewDayPane
import dev.tricked.solidverdant.ui.sync.SyncCenterScreen
import dev.tricked.solidverdant.ui.templates.ManageTemplatesScreen

@Composable
fun MainNavHost(
    navController: NavHostController,
    trackContent: @Composable () -> Unit,
    calendarContent: @Composable () -> Unit,
    statsContent: @Composable () -> Unit,
    reviewContent: @Composable () -> Unit = {},
    inboxBadgeCount: Int = 0,
    onPrivacyLogout: () -> Unit = {},
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            MainNavigationBar(
                currentRoute = currentRoute,
                inboxBadgeCount = inboxBadgeCount,
                onNavigate = { screen ->
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Track.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Track.route,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Screen.Track.route) { trackContent() }
            composable(Screen.Calendar.route) { calendarContent() }
            composable(Screen.Stats.route) { statsContent() }
            composable(Screen.Review.route) { reviewContent() }

            // Full-screen review-loop destinations pushed on top of the tab graph. They render the
            // feature agents' stub screens/panes with their own back navigation.
            composable(ReviewRoutes.END_OF_DAY) {
                EndOfDayReviewHost(onBack = { navController.popBackStack() })
            }
            composable(ReviewRoutes.REMINDER_SETTINGS) {
                ReminderSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(ReviewRoutes.MANAGE_TEMPLATES) {
                ManageTemplatesScreen(onBack = { navController.popBackStack() })
            }
            composable(SyncRoutes.SYNC_CENTER) {
                SyncCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoutes.PRIVACY) {
                PrivacyScreen(
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        navController.popBackStack()
                        onPrivacyLogout()
                    },
                )
            }
        }
    }
}

/** Shared production bottom navigation, also used by full-app screenshot rendering. */
@Composable
internal fun MainNavigationBar(currentRoute: String?, inboxBadgeCount: Int, onNavigate: (Screen) -> Unit) {
    NavigationBar {
        bottomNavScreens.forEach { screen ->
            NavigationBarItem(
                modifier = Modifier.testTag("main_nav_${screen.route}"),
                selected = currentRoute == screen.route,
                onClick = { onNavigate(screen) },
                icon = {
                    if (screen == Screen.Review && inboxBadgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(
                                        text = if (inboxBadgeCount > 99) {
                                            stringResource(R.string.review_badge_overflow)
                                        } else {
                                            inboxBadgeCount.toString()
                                        },
                                    )
                                }
                            },
                        ) {
                            Icon(
                                screen.icon,
                                contentDescription = pluralStringResource(
                                    R.plurals.review_pending_badge_description,
                                    inboxBadgeCount,
                                    inboxBadgeCount,
                                ),
                            )
                        }
                    } else {
                        Icon(screen.icon, contentDescription = null)
                    }
                },
                label = { Text(stringResource(screen.labelRes)) },
            )
        }
    }
}

/**
 * Full-screen host for the end-of-day review flow (opened from the end-of-day notification). Wraps
 * the review/reminders agent's [ReviewDayPane] with a top bar and back navigation. Foundation shell
 * only; the pane itself is fleshed out by that agent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndOfDayReviewHost(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_end_of_day_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.review_navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ReviewDayPane()
        }
    }
}

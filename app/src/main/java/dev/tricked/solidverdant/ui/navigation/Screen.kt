package dev.tricked.solidverdant.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import dev.tricked.solidverdant.R

sealed class Screen(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    data object Track : Screen("track", R.string.nav_track, Icons.Outlined.Timer)
    data object Calendar : Screen("calendar", R.string.nav_calendar, Icons.Outlined.CalendarMonth)
    data object Stats : Screen("stats", R.string.nav_stats, Icons.Outlined.BarChart)
}

val bottomNavScreens: List<Screen> = listOf(Screen.Track, Screen.Calendar, Screen.Stats)

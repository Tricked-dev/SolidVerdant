package dev.tricked.solidverdant.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTest {
    @Test
    fun bottomNavScreens_haveUniqueStableRoutes() {
        val routes = bottomNavScreens.map { it.route }
        assertEquals(listOf("track", "calendar", "stats", "review"), routes)
        assertEquals(routes.size, routes.toSet().size)
    }
}

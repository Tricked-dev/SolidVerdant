/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

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

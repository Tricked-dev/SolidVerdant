/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.domain.time

import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.model.User
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.ZoneId

/**
 * Task 2.1: reporting surfaces derive a [TemporalPolicy] from the cached Solidtime profile
 * (timezone + week_start) instead of [ZoneId.systemDefault]. This exercises the timezone/week_start
 * mapping, the fallback rules, and — critically — that the provider re-emits when the cached auth
 * changes via the REAL [SettingsDataStore.observeCachedAuth] observable (not a fake), proving the
 * change-notification wiring added to the synchronous immediate_ui_cache.
 */
@RunWith(RobolectricTestRunner::class)
class TemporalPolicyProviderTest {

    private val settings = SettingsDataStore(ApplicationProvider.getApplicationContext())

    // immediate_ui_cache is a file-backed SharedPreferences under a fixed name, so state persists
    // across tests in this class. Clear it before each test for a deterministic starting point.
    @Before fun clearCache() = runBlocking { settings.clearCachedData() }

    private fun user(timezone: String = "UTC", weekStart: String = "monday") = User(
        id = "u1",
        name = "Tester",
        email = "t@example.com",
        timezone = timezone,
        weekStart = weekStart,
    )

    @Test
    fun valid_timezone_maps_to_zone_id() = runTest {
        settings.cacheAuth(user(timezone = "Europe/Amsterdam"), emptyList(), null)
        val policy = TemporalPolicyProvider(settings).current()
        assertEquals(ZoneId.of("Europe/Amsterdam"), policy.zone)
    }

    @Test
    fun garbage_timezone_falls_back_to_device_zone() = runTest {
        settings.cacheAuth(user(timezone = "Not/AZone"), emptyList(), null)
        val policy = TemporalPolicyProvider(settings).current()
        assertEquals(ZoneId.systemDefault(), policy.zone)
    }

    @Test
    fun absent_auth_uses_device_defaults() = runTest {
        // No cacheAuth call -> logged out -> device zone + Monday.
        val policy = TemporalPolicyProvider(settings).current()
        assertEquals(ZoneId.systemDefault(), policy.zone)
        assertEquals(DayOfWeek.MONDAY, policy.firstDayOfWeek)
    }

    @Test
    fun week_start_names_map_to_day_of_week() = runTest {
        val cases = mapOf(
            "monday" to DayOfWeek.MONDAY,
            "tuesday" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY,
            "saturday" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY,
        )
        for ((name, expected) in cases) {
            settings.cacheAuth(user(weekStart = name), emptyList(), null)
            val policy = TemporalPolicyProvider(settings).current()
            assertEquals("week_start=$name", expected, policy.firstDayOfWeek)
        }
    }

    @Test
    fun unknown_week_start_falls_back_to_monday() = runTest {
        settings.cacheAuth(user(weekStart = "funday"), emptyList(), null)
        val policy = TemporalPolicyProvider(settings).current()
        assertEquals(DayOfWeek.MONDAY, policy.firstDayOfWeek)
    }

    @Test
    fun blank_week_start_falls_back_to_monday() = runTest {
        settings.cacheAuth(user(weekStart = ""), emptyList(), null)
        val policy = TemporalPolicyProvider(settings).current()
        assertEquals(DayOfWeek.MONDAY, policy.firstDayOfWeek)
    }

    @Test
    fun provider_re_emits_when_cached_auth_changes() = runTest {
        settings.cacheAuth(user(timezone = "Europe/Amsterdam"), emptyList(), null)
        val provider = TemporalPolicyProvider(settings)

        // A single live collector: the second value is only observed if the authCacheChanges bump
        // in cacheAuth() actually fires. Two separate .first() calls would each re-read the cache
        // and pass even if the bump were deleted, so this guards the change-notification wiring.
        val zones = mutableListOf<ZoneId>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            provider.policy.collect { zones.add(it.zone) }
        }

        assertEquals(listOf(ZoneId.of("Europe/Amsterdam")), zones)

        settings.cacheAuth(user(timezone = "America/New_York"), emptyList(), null)
        runCurrent()

        assertEquals(
            listOf(ZoneId.of("Europe/Amsterdam"), ZoneId.of("America/New_York")),
            zones,
        )
        job.cancel()
    }
}

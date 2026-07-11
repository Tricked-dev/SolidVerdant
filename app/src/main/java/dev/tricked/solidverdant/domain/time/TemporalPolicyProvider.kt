/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.domain.time

import dev.tricked.solidverdant.data.local.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives the account-scoped [TemporalPolicy] from the cached Solidtime profile, re-emitting when
 * the cached auth changes (login/logout/profile refresh). Fallbacks:
 * - unparseable/absent timezone -> device zone ([ZoneId.systemDefault]);
 * - unknown/blank `week_start` -> [DayOfWeek.MONDAY];
 * - logged out (no cached auth) -> device zone + Monday.
 */
@Singleton
class TemporalPolicyProvider @Inject constructor(private val settings: SettingsDataStore) {

    val policy: Flow<TemporalPolicy> = settings.observeCachedAuth().map { cached ->
        val user = cached?.user
        TemporalPolicy(
            zone = parseZone(user?.timezone),
            firstDayOfWeek = parseFirstDayOfWeek(user?.weekStart),
        )
    }

    suspend fun current(): TemporalPolicy = policy.first()

    private fun parseZone(timezone: String?): ZoneId = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

    private fun parseFirstDayOfWeek(weekStart: String?): DayOfWeek =
        weekStart?.let { runCatching { DayOfWeek.valueOf(it.uppercase()) }.getOrNull() } ?: DayOfWeek.MONDAY
}

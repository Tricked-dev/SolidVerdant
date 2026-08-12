/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.di

import dev.tricked.solidverdant.util.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic [Clock] for on-device E2E tests.
 *
 * Defaults to a fixed epoch so timestamps in synced/reconciled entries are reproducible. Tests can
 * mutate [nowMs] (or advance it via [advanceBy]) to drive time-dependent behaviour. Bound in place
 * of [dev.tricked.solidverdant.util.SystemClock] by [TestRemoteModule].
 */
@Singleton
class TestClock @Inject constructor() : Clock {
    // 2026-07-07T12:00:00Z, matching the harness's fixed "today" while keeping the default
    // two-hour fixture interval inside the visible day on narrow three-day calendars.
    @Volatile
    var nowMs: Long = DEFAULT_NOW_MS

    override fun nowMs(): Long = nowMs

    fun advanceBy(millis: Long) {
        nowMs += millis
    }

    fun reset() {
        nowMs = DEFAULT_NOW_MS
    }

    companion object {
        const val DEFAULT_NOW_MS: Long = 1_783_425_600_000L // 2026-07-07T12:00:00Z
    }
}

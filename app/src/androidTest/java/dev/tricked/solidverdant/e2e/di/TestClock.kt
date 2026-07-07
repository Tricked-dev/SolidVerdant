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
    // 2026-07-07T00:00:00Z, matching the harness's fixed "today".
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
        const val DEFAULT_NOW_MS: Long = 1_783_382_400_000L // 2026-07-07T00:00:00Z
    }
}

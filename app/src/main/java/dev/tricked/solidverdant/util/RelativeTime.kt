/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.util

/**
 * Coarse relative-time bucketing for freshness labels on the Sync Center screen (#33). Kept as a
 * pure, resource-free helper so it is trivially unit-testable; the composable layer maps a
 * [Bucket] + [amount] to a localized string. "Just now" also absorbs future / sentinel-zero
 * timestamps so a clock skew never renders a negative age.
 */
object RelativeTime {
    enum class Bucket { Never, JustNow, Minutes, Hours, Days }

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val DAY_MS = 24 * HOUR_MS

    fun bucketOf(thenMs: Long?, nowMs: Long): Bucket {
        if (thenMs == null) return Bucket.Never
        val delta = nowMs - thenMs
        return when {
            delta < MINUTE_MS -> Bucket.JustNow
            delta < HOUR_MS -> Bucket.Minutes
            delta < DAY_MS -> Bucket.Hours
            else -> Bucket.Days
        }
    }

    /** Whole-unit magnitude for the [bucketOf] result (minutes / hours / days). 0 for Never/JustNow. */
    fun amount(thenMs: Long?, nowMs: Long): Int {
        if (thenMs == null) return 0
        val delta = (nowMs - thenMs).coerceAtLeast(0)
        return when (bucketOf(thenMs, nowMs)) {
            Bucket.Minutes -> (delta / MINUTE_MS).toInt()
            Bucket.Hours -> (delta / HOUR_MS).toInt()
            Bucket.Days -> (delta / DAY_MS).toInt()
            else -> 0
        }
    }
}

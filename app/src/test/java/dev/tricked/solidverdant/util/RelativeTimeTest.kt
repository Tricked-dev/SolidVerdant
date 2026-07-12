/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `null timestamp maps to Never`() {
        assertEquals(RelativeTime.Bucket.Never, RelativeTime.bucketOf(null, now))
    }

    @Test
    fun `moment within the last minute is JustNow`() {
        assertEquals(RelativeTime.Bucket.JustNow, RelativeTime.bucketOf(now - 30_000L, now))
    }

    @Test
    fun `a future or sentinel-zero timestamp is JustNow rather than negative`() {
        assertEquals(RelativeTime.Bucket.JustNow, RelativeTime.bucketOf(now + 5_000L, now))
    }

    @Test
    fun `minutes ago rounds down to whole minutes`() {
        val result = RelativeTime.bucketOf(now - (5 * 60_000L + 45_000L), now)
        assertEquals(RelativeTime.Bucket.Minutes, result)
        assertEquals(5, RelativeTime.amount(now - (5 * 60_000L + 45_000L), now))
    }

    @Test
    fun `hours ago rounds down to whole hours`() {
        val threeHours = now - (3 * 60 * 60_000L)
        assertEquals(RelativeTime.Bucket.Hours, RelativeTime.bucketOf(threeHours, now))
        assertEquals(3, RelativeTime.amount(threeHours, now))
    }

    @Test
    fun `days ago rounds down to whole days`() {
        val twoDays = now - (2 * 24 * 60 * 60_000L)
        assertEquals(RelativeTime.Bucket.Days, RelativeTime.bucketOf(twoDays, now))
        assertEquals(2, RelativeTime.amount(twoDays, now))
    }
}

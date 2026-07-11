/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsPaginationTest {

    private val pageSize = 500

    @Test
    fun continuesPastFullPageWhenTotalUnknown() {
        // Regression: total previously defaulted to the page size, so `offset < total` was false
        // right after the first full page and later pages were silently dropped (undercount).
        assertTrue(
            shouldFetchNextPage(pageSize, lastPageSize = pageSize, offsetAfterPage = 500, total = null),
        )
    }

    @Test
    fun stopsOnShortPageWhenTotalUnknown() {
        assertFalse(
            shouldFetchNextPage(pageSize, lastPageSize = 200, offsetAfterPage = 200, total = null),
        )
    }

    @Test
    fun stopsOnEmptyPage() {
        assertFalse(
            shouldFetchNextPage(pageSize, lastPageSize = 0, offsetAfterPage = 500, total = null),
        )
    }

    @Test
    fun respectsKnownTotal() {
        // Full page but total reached -> stop.
        assertFalse(
            shouldFetchNextPage(pageSize, lastPageSize = pageSize, offsetAfterPage = 500, total = 500),
        )
        // Full page and more remain -> continue.
        assertTrue(
            shouldFetchNextPage(pageSize, lastPageSize = pageSize, offsetAfterPage = 500, total = 1200),
        )
    }
}

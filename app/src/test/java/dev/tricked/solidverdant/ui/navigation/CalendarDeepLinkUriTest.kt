/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.navigation

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class CalendarDeepLinkUriTest {

    @Test
    fun resolvesHierarchicalCalendarLinks() {
        assertEquals(LocalDate.of(2026, 8, 11), calendarDateFromUri(Uri.parse("solidtime://calendar?date=2026-08-11")))
        assertEquals(LocalDate.of(2026, 8, 11), calendarDateFromUri(Uri.parse("solidtime://calendar/2026-08-11")))
    }

    @Test
    fun ignoresOpaqueUrisInsteadOfThrowing() {
        assertNull(calendarDateFromUri(Uri.parse("solidtime:calendar?date=2026-08-11")))
        assertNull(calendarDateFromUri(Uri.parse("mailto:someone@example.com")))
    }
}

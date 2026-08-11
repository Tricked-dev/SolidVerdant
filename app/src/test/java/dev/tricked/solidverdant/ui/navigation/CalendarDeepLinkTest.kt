/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CalendarDeepLinkTest {

    @Test
    fun parsesQueryDate() {
        assertEquals(
            LocalDate.of(2026, 8, 11),
            parseCalendarDateDeepLink("solidtime", "calendar", null, "2026-08-11"),
        )
    }

    @Test
    fun parsesPathDateWhenQueryIsAbsent() {
        assertEquals(
            LocalDate.of(2026, 8, 11),
            parseCalendarDateDeepLink("SOLIDTIME", "CALENDAR", "/2026-08-11", null),
        )
    }

    @Test
    fun rejectsUnsupportedUriTarget() {
        assertNull(parseCalendarDateDeepLink("https", "calendar", null, "2026-08-11"))
        assertNull(parseCalendarDateDeepLink("solidtime", "track", null, "2026-08-11"))
        assertNull(parseCalendarDateDeepLink("solidtime", null, null, "2026-08-11"))
    }

    @Test
    fun rejectsMissingAndMalformedDates() {
        assertNull(parseCalendarDateDeepLink("solidtime", "calendar", null, null))
        assertNull(parseCalendarDateDeepLink("solidtime", "calendar", null, ""))
        assertNull(parseCalendarDateDeepLink("solidtime", "calendar", null, "2026-02-30"))
        assertNull(parseCalendarDateDeepLink("solidtime", "calendar", "/2026-8-11", null))
        assertNull(parseCalendarDateDeepLink("solidtime", "calendar", "/2026-08-11/extra", null))
    }

    @Test
    fun queryDateTakesPrecedenceAndInvalidatesTheLinkWhenMalformed() {
        assertNull(
            parseCalendarDateDeepLink(
                "solidtime",
                "calendar",
                "/2026-08-11",
                "not-a-date",
            ),
        )
    }
}

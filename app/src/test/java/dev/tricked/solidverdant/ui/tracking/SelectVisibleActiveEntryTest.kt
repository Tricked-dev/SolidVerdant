/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SelectVisibleActiveEntryTest {
    private fun active(id: String, start: String) = TimeEntry(
        id = id,
        userId = "user",
        start = start,
        end = null,
        organizationId = "org",
    )

    @Test fun `same id keeps the polled copy`() {
        val room = active("srv-1", "2026-07-06T08:00:00Z")
        val polled = active("srv-1", "2026-07-06T08:05:00Z")
        assertSame(polled, selectVisibleActiveEntry(room, polled))
    }

    @Test fun `newer room timer wins over an older polled row`() {
        val room = active("srv-2", "2026-07-06T09:00:00Z")
        val polled = active("srv-1", "2026-07-06T08:00:00Z")
        assertSame(room, selectVisibleActiveEntry(room, polled))
    }

    @Test fun `rekeyed room entry wins over a retired local id even when the local row is newer`() {
        val room = active("srv-1", "2026-07-06T08:00:00Z")
        val polled = active("local-1", "2026-07-06T08:30:00Z")
        assertSame(room, selectVisibleActiveEntry(room, polled))
    }

    @Test fun `local ids on both sides fall back to the start comparison`() {
        val room = active("local-1", "2026-07-06T08:00:00Z")
        val polled = active("local-2", "2026-07-06T08:30:00Z")
        assertEquals(polled, selectVisibleActiveEntry(room, polled))
    }
}

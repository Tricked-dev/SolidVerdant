/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class EntryTrustRulesTest {
    private fun entry(id: String, start: String, end: String?, org: String = "org") = TimeEntry(
        id = id,
        userId = "user",
        start = start,
        end = end,
        organizationId = org,
    )

    @Test fun `adjacent entries do not overlap`() {
        assertFalse(
            EntryTrustRules.overlaps(
                entry("a", "2026-07-06T08:00:00Z", "2026-07-06T09:00:00Z"),
                entry("b", "2026-07-06T09:00:00Z", "2026-07-06T10:00:00Z"),
            ),
        )
    }

    @Test fun `contained and identical intervals overlap`() {
        val outer = entry("a", "2026-07-06T08:00:00Z", "2026-07-06T11:00:00Z")
        assertTrue(EntryTrustRules.overlaps(outer, entry("b", "2026-07-06T09:00:00Z", "2026-07-06T10:00:00Z")))
        assertTrue(EntryTrustRules.overlaps(outer, entry("c", outer.start, outer.end)))
    }

    @Test fun `different organizations never overlap`() {
        assertFalse(
            EntryTrustRules.overlaps(
                entry("a", "2026-07-06T08:00:00Z", "2026-07-06T10:00:00Z", "one"),
                entry("b", "2026-07-06T09:00:00Z", "2026-07-06T11:00:00Z", "two"),
            ),
        )
    }

    @Test fun `overlap count is exact for nested and adjacent intervals`() {
        val entries = listOf(
            entry("a", "2026-07-06T08:00:00Z", "2026-07-06T12:00:00Z"),
            entry("b", "2026-07-06T09:00:00Z", "2026-07-06T10:00:00Z"),
            entry("c", "2026-07-06T10:00:00Z", "2026-07-06T11:00:00Z"),
        )
        assertTrue(EntryTrustRules.overlapCount(entries) == 2)
    }

    @Test fun `positive duration without end is completed and overlaps across midnight`() {
        val durationOnly = entry("duration", "2026-07-06T23:00:00+02:00", null).copy(duration = 3 * 3600)
        val overlapping = entry("other", "2026-07-07T00:30:00+02:00", "2026-07-07T01:30:00+02:00")

        assertTrue(EntryTrustRules.overlaps(durationOnly, overlapping))
        assertFalse(EntryTrustRules.isLongRunning(durationOnly, Duration.ofHours(1), Instant.parse("2026-07-08T00:00:00Z")))

        val running = EntryTrustRules.filter(
            entries = listOf(durationOnly),
            filter = HistoryFilter(runningOnly = true),
            projects = emptyList(),
            tasks = emptyList(),
            zone = ZoneOffset.UTC,
        )
        assertTrue(running.isEmpty())
    }

    @Test fun `long timer check uses explicit threshold`() {
        val now = Instant.parse("2026-07-06T12:00:00Z")
        assertTrue(
            EntryTrustRules.isLongRunning(
                entry("a", "2026-07-06T08:00:00Z", null),
                Duration.ofHours(4),
                now,
            ),
        )
        assertFalse(
            EntryTrustRules.isLongRunning(
                entry("b", "2026-07-06T09:00:01Z", null),
                Duration.ofHours(3),
                now,
            ),
        )
    }

    @Test fun `history filter matches client and non billable entries`() {
        val billable = entry("a", "2026-07-06T08:00:00Z", "2026-07-06T09:00:00Z")
            .copy(projectId = "p1", billable = true)
        val nonBillable = entry("b", "2026-07-06T09:00:00Z", "2026-07-06T10:00:00Z")
            .copy(projectId = "p2", billable = false)
        val projects = listOf(
            Project("p1", "One", "#000000", clientId = "c1"),
            Project("p2", "Two", "#000000", clientId = "c2"),
        )
        val filtered = EntryTrustRules.filter(
            listOf(billable, nonBillable),
            HistoryFilter(clientId = "c2", billable = false),
            projects = projects,
            tasks = emptyList(),
            clients = listOf(Client("c1", "First"), Client("c2", "Second")),
            zone = java.time.ZoneOffset.UTC,
        )
        assertEquals(listOf("b"), filtered.map { it.id })
    }

    @Test
    fun `history date filter includes entry that overlaps selected day`() {
        val spanning = entry(
            id = "spanning",
            start = "2026-07-06T23:00:00Z",
            end = "2026-07-07T01:00:00Z",
        )

        val filtered = EntryTrustRules.filter(
            entries = listOf(spanning),
            filter = HistoryFilter(
                startDate = LocalDate.of(2026, 7, 7),
                endDate = LocalDate.of(2026, 7, 7),
            ),
            projects = emptyList(),
            tasks = emptyList(),
            zone = ZoneOffset.UTC,
            now = Instant.parse("2026-07-08T00:00:00Z"),
        )

        assertEquals(listOf("spanning"), filtered.map { it.id })
    }
}

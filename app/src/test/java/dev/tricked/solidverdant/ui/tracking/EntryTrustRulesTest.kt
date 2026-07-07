package dev.tricked.solidverdant.ui.tracking

import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class EntryTrustRulesTest {
    private fun entry(id: String, start: String, end: String?, org: String = "org") = TimeEntry(
        id = id, userId = "user", start = start, end = end, organizationId = org,
    )

    @Test fun `adjacent entries do not overlap`() {
        assertFalse(EntryTrustRules.overlaps(
            entry("a", "2026-07-06T08:00:00Z", "2026-07-06T09:00:00Z"),
            entry("b", "2026-07-06T09:00:00Z", "2026-07-06T10:00:00Z"),
        ))
    }

    @Test fun `contained and identical intervals overlap`() {
        val outer = entry("a", "2026-07-06T08:00:00Z", "2026-07-06T11:00:00Z")
        assertTrue(EntryTrustRules.overlaps(outer, entry("b", "2026-07-06T09:00:00Z", "2026-07-06T10:00:00Z")))
        assertTrue(EntryTrustRules.overlaps(outer, entry("c", outer.start, outer.end)))
    }

    @Test fun `different organizations never overlap`() {
        assertFalse(EntryTrustRules.overlaps(
            entry("a", "2026-07-06T08:00:00Z", "2026-07-06T10:00:00Z", "one"),
            entry("b", "2026-07-06T09:00:00Z", "2026-07-06T11:00:00Z", "two"),
        ))
    }

    @Test fun `overlap count is exact for nested and adjacent intervals`() {
        val entries = listOf(
            entry("a", "2026-07-06T08:00:00Z", "2026-07-06T12:00:00Z"),
            entry("b", "2026-07-06T09:00:00Z", "2026-07-06T10:00:00Z"),
            entry("c", "2026-07-06T10:00:00Z", "2026-07-06T11:00:00Z"),
        )
        assertTrue(EntryTrustRules.overlapCount(entries) == 2)
    }

    @Test fun `long timer check uses explicit threshold`() {
        val now = Instant.parse("2026-07-06T12:00:00Z")
        assertTrue(EntryTrustRules.isLongRunning(
            entry("a", "2026-07-06T08:00:00Z", null), Duration.ofHours(4), now,
        ))
        assertFalse(EntryTrustRules.isLongRunning(
            entry("b", "2026-07-06T09:00:01Z", null), Duration.ofHours(3), now,
        ))
    }
}

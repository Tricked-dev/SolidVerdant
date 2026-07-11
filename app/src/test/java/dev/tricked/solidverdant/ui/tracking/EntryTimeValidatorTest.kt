/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class EntryTimeValidatorTest {
    private fun at(iso: String) = ZonedDateTime.parse(iso)

    // --- resolveEnd: cross-midnight vs. mistake -------------------------------------------------

    @Test fun `end after start on the same day is used as-is`() {
        val start = at("2026-07-06T09:00:00Z")
        val end = at("2026-07-06T17:00:00Z")
        assertEquals(end, EntryTimeValidator.resolveEnd(start, end))
    }

    @Test fun `plausible overnight span rolls to the next day`() {
        // 23:00 start, 01:00 end clock-time -> a 2h cross-midnight entry.
        val start = at("2026-07-06T23:00:00Z")
        val sameDayEnd = at("2026-07-06T01:00:00Z")
        val resolved = EntryTimeValidator.resolveEnd(start, sameDayEnd)
        assertEquals(at("2026-07-07T01:00:00Z"), resolved)
    }

    @Test fun `implausibly long rollover is rejected as a likely typo`() {
        // 09:00 start, 08:00 end would roll to a ~23h entry -> treat as a mistake.
        val start = at("2026-07-06T09:00:00Z")
        val sameDayEnd = at("2026-07-06T08:00:00Z")
        assertNull(EntryTimeValidator.resolveEnd(start, sameDayEnd))
    }

    // --- evaluate -------------------------------------------------------------------------------

    @Test fun `end not after start is a blocking error`() {
        val start = at("2026-07-06T09:00:00Z")
        val result = EntryTimeValidator.evaluate(start, start)
        assertEquals(EntryTimeValidator.Error.END_NOT_AFTER_START, result.error)
        assertFalse(result.canSave)
    }

    @Test fun `end before start is a blocking error`() {
        val result = EntryTimeValidator.evaluate(
            at("2026-07-06T09:00:00Z"),
            at("2026-07-06T08:00:00Z"),
        )
        assertEquals(EntryTimeValidator.Error.END_NOT_AFTER_START, result.error)
    }

    @Test fun `normal short entry is valid without warnings`() {
        val result = EntryTimeValidator.evaluate(
            at("2026-07-06T09:00:00Z"),
            at("2026-07-06T10:30:00Z"),
        )
        assertNull(result.error)
        assertTrue(result.warnings.isEmpty())
        assertTrue(result.canSave)
    }

    @Test fun `duration over 24h is blocked as too long`() {
        val result = EntryTimeValidator.evaluate(
            at("2026-07-06T09:00:00Z"),
            at("2026-07-07T10:00:00Z"),
        )
        assertEquals(EntryTimeValidator.Error.TOO_LONG, result.error)
        assertFalse(result.canSave)
    }

    @Test fun `long but plausible duration warns yet still saves`() {
        val result = EntryTimeValidator.evaluate(
            at("2026-07-06T08:00:00Z"),
            at("2026-07-06T22:00:00Z"), // 14h
        )
        assertNull(result.error)
        assertTrue(result.warnings.contains(EntryTimeValidator.Warning.LONG_DURATION))
        assertTrue(result.canSave)
    }

    @Test fun `overlap surfaces a warning that does not block saving`() {
        val result = EntryTimeValidator.evaluate(
            at("2026-07-06T09:00:00Z"),
            at("2026-07-06T10:00:00Z"),
            overlaps = true,
        )
        assertTrue(result.warnings.contains(EntryTimeValidator.Warning.OVERLAP))
        assertTrue(result.canSave)
    }

    @Test fun `overlap under an org policy uses the stronger policy warning`() {
        val result = EntryTimeValidator.evaluate(
            at("2026-07-06T09:00:00Z"),
            at("2026-07-06T10:00:00Z"),
            overlaps = true,
            overlapProhibited = true,
        )
        assertTrue(result.warnings.contains(EntryTimeValidator.Warning.OVERLAP_POLICY))
        assertFalse(result.warnings.contains(EntryTimeValidator.Warning.OVERLAP))
        assertTrue(result.canSave)
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.domain.inbox

import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class InboxAnalyzerTest {

    // 2026-07-06 is a Monday; "now" is well after so every test date is a completed past day.
    private val nowMs = Instant.parse("2026-07-20T00:00:00Z").toEpochMilli()
    private val utc: ZoneId = ZoneOffset.UTC

    private fun entry(
        id: String,
        start: String,
        end: String?,
        projectId: String? = "p1",
        taskId: String? = "t1",
        description: String? = "desc",
        tags: List<Tag> = listOf(Tag("tag1", "One")),
    ) = TimeEntry(
        id = id,
        description = description,
        userId = "u1",
        start = start,
        end = end,
        taskId = taskId,
        projectId = projectId,
        tags = tags,
        billable = false,
        organizationId = "org",
    )

    @Suppress("LongParameterList")
    private fun config(
        workDays: Set<DayOfWeek> = DayOfWeek.values().toSet(),
        workStartMinute: Int = 9 * 60,
        workEndMinute: Int = 17 * 60,
        minGapMinutes: Int = 30,
        maxDurationHours: Int = 4,
        checkGaps: Boolean = true,
        checkOverlaps: Boolean = true,
        checkMissingProject: Boolean = false,
        checkMissingTask: Boolean = false,
        checkMissingDescription: Boolean = false,
        checkMissingTags: Boolean = false,
        checkLongDuration: Boolean = false,
    ) = InboxCheckConfig(
        workDays = workDays,
        workStartMinute = workStartMinute,
        workEndMinute = workEndMinute,
        minGapMinutes = minGapMinutes,
        maxDurationHours = maxDurationHours,
        checkGaps = checkGaps,
        checkOverlaps = checkOverlaps,
        checkMissingProject = checkMissingProject,
        checkMissingTask = checkMissingTask,
        checkMissingDescription = checkMissingDescription,
        checkMissingTags = checkMissingTags,
        checkLongDuration = checkLongDuration,
    )

    // Existing tests use a permissive (very old) horizon so the clamp is a no-op for them.
    private fun analyze(entries: List<TimeEntry>, config: InboxCheckConfig, dismissed: Set<String> = emptySet()) =
        InboxAnalyzer.analyze(entries, config, dismissed, nowMs, utc, horizonStartMs = 0L)

    // ---- Overlaps -------------------------------------------------------------------------------

    @Test fun `exact intersection is flagged as an overlap`() {
        val issues = analyze(
            listOf(
                entry("a", "2026-07-06T09:00:00Z", "2026-07-06T11:00:00Z"),
                entry("b", "2026-07-06T10:00:00Z", "2026-07-06T12:00:00Z"),
            ),
            config(checkGaps = false),
        )
        assertEquals(1, issues.count { it.type == InboxIssueType.OVERLAP })
    }

    @Test fun `adjacent entries do not overlap`() {
        val issues = analyze(
            listOf(
                entry("a", "2026-07-06T09:00:00Z", "2026-07-06T10:00:00Z"),
                entry("b", "2026-07-06T10:00:00Z", "2026-07-06T11:00:00Z"),
            ),
            config(checkGaps = false),
        )
        assertEquals(0, issues.count { it.type == InboxIssueType.OVERLAP })
    }

    @Test fun `overlap key is stable regardless of pair order`() {
        val a = entry("a", "2026-07-06T09:00:00Z", "2026-07-06T11:00:00Z")
        val b = entry("b", "2026-07-06T10:00:00Z", "2026-07-06T12:00:00Z")
        val first = analyze(listOf(a, b), config(checkGaps = false)).first { it.type == InboxIssueType.OVERLAP }
        val second = analyze(listOf(b, a), config(checkGaps = false)).first { it.type == InboxIssueType.OVERLAP }
        assertEquals(first.key, second.key)
    }

    // ---- Gaps -----------------------------------------------------------------------------------

    @Test fun `gaps inside the working window are detected between and after entries`() {
        val issues = analyze(
            listOf(
                entry("a", "2026-07-06T09:00:00Z", "2026-07-06T10:00:00Z"),
                entry("b", "2026-07-06T11:00:00Z", "2026-07-06T12:00:00Z"),
            ),
            config(checkOverlaps = false),
        ).filter { it.type == InboxIssueType.GAP }
        // 10:00-11:00 (60m) and 12:00-17:00 (300m).
        assertEquals(2, issues.size)
    }

    @Test fun `gaps shorter than the minimum are ignored`() {
        val issues = analyze(
            listOf(
                entry("a", "2026-07-06T09:00:00Z", "2026-07-06T10:00:00Z"),
                entry("b", "2026-07-06T10:20:00Z", "2026-07-06T17:00:00Z"),
            ),
            config(checkOverlaps = false, minGapMinutes = 30),
        ).filter { it.type == InboxIssueType.GAP }
        assertEquals(0, issues.size)
    }

    @Test fun `days with no tracked time are not flagged as gaps`() {
        // Only one day has an entry that fully covers its window; the other work days are silent.
        val issues = analyze(
            listOf(entry("a", "2026-07-06T09:00:00Z", "2026-07-06T17:00:00Z")),
            config(checkOverlaps = false),
        ).filter { it.type == InboxIssueType.GAP }
        assertEquals(0, issues.size)
    }

    @Test fun `non-working days are excluded from gap detection`() {
        // Monday only in work days; the Monday entry covers its window, so no gaps anywhere.
        val issues = analyze(
            listOf(entry("a", "2026-07-06T09:00:00Z", "2026-07-06T17:00:00Z")),
            config(checkOverlaps = false, workDays = setOf(DayOfWeek.MONDAY)),
        ).filter { it.type == InboxIssueType.GAP }
        assertEquals(0, issues.size)
    }

    @Test fun `today's working window is clipped to now`() {
        // "Now" is midday on the entry's day; the afternoon is in the future and must not be a gap.
        val todayNow = Instant.parse("2026-07-06T12:00:00Z").toEpochMilli()
        val issues = InboxAnalyzer.analyze(
            listOf(entry("a", "2026-07-06T09:00:00Z", "2026-07-06T12:00:00Z")),
            config(checkOverlaps = false),
            emptySet(),
            todayNow,
            utc,
            horizonStartMs = 0L,
        ).filter { it.type == InboxIssueType.GAP }
        assertEquals(0, issues.size)
    }

    @Test fun `gap window honors the supplied timezone`() {
        val plusTwo = ZoneOffset.ofHours(2)
        // 07:00-15:00Z == 09:00-17:00 local at +02:00, so the whole window is covered: no gaps.
        val covered = InboxAnalyzer.analyze(
            listOf(entry("a", "2026-07-06T07:00:00Z", "2026-07-06T15:00:00Z")),
            config(checkOverlaps = false),
            emptySet(),
            nowMs,
            plusTwo,
            horizonStartMs = 0L,
        ).filter { it.type == InboxIssueType.GAP }
        assertEquals(0, covered.size)

        // The same entry analyzed in UTC leaves 15:00-17:00 uncovered.
        val utcGaps = InboxAnalyzer.analyze(
            listOf(entry("a", "2026-07-06T07:00:00Z", "2026-07-06T15:00:00Z")),
            config(checkOverlaps = false),
            emptySet(),
            nowMs,
            utc,
            horizonStartMs = 0L,
        ).filter { it.type == InboxIssueType.GAP }
        assertEquals(1, utcGaps.size)
    }

    // ---- Missing metadata -----------------------------------------------------------------------

    @Test fun `missing project is flagged only when the check is enabled`() {
        val entries = listOf(entry("a", "2026-07-06T09:00:00Z", "2026-07-06T17:00:00Z", projectId = null, taskId = null))
        assertEquals(
            0,
            analyze(entries, config(checkGaps = false, checkMissingProject = false))
                .count { it.type == InboxIssueType.MISSING_METADATA },
        )
        val flagged = analyze(entries, config(checkGaps = false, checkMissingProject = true))
            .first { it.type == InboxIssueType.MISSING_METADATA }
        assertTrue(MissingField.PROJECT in flagged.missingFields)
    }

    @Test fun `missing task is only flagged when a project is present`() {
        val withProjectNoTask = entry("a", "2026-07-06T09:00:00Z", "2026-07-06T17:00:00Z", taskId = null)
        val noProject = entry("b", "2026-07-06T09:00:00Z", "2026-07-06T17:00:00Z", projectId = null, taskId = null)
        val issues = analyze(
            listOf(withProjectNoTask, noProject),
            config(checkGaps = false, checkMissingTask = true),
        ).filter { it.type == InboxIssueType.MISSING_METADATA }
        assertEquals(1, issues.size)
        assertEquals("a", issues.first().primaryEntry?.id)
    }

    @Test fun `running entries are never flagged for missing metadata`() {
        val running = entry("a", "2026-07-06T09:00:00Z", null, projectId = null, taskId = null)
        val issues = analyze(listOf(running), config(checkGaps = false, checkMissingProject = true))
        assertEquals(0, issues.count { it.type == InboxIssueType.MISSING_METADATA })
    }

    // ---- Long duration --------------------------------------------------------------------------

    @Test fun `entries at or over the threshold are flagged as long`() {
        val long = entry("a", "2026-07-06T09:00:00Z", "2026-07-06T14:00:00Z") // 5h
        val short = entry("b", "2026-07-06T09:00:00Z", "2026-07-06T11:00:00Z") // 2h
        val issues = analyze(
            listOf(long, short),
            config(checkGaps = false, checkOverlaps = false, maxDurationHours = 4, checkLongDuration = true),
        ).filter { it.type == InboxIssueType.LONG_DURATION }
        assertEquals(1, issues.size)
        assertEquals("a", issues.first().primaryEntry?.id)
    }

    // ---- Dismissals & ordering ------------------------------------------------------------------

    @Test fun `dismissed keys are filtered out`() {
        val entries = listOf(
            entry("a", "2026-07-06T09:00:00Z", "2026-07-06T11:00:00Z"),
            entry("b", "2026-07-06T10:00:00Z", "2026-07-06T12:00:00Z"),
        )
        val overlap = analyze(entries, config(checkGaps = false)).first { it.type == InboxIssueType.OVERLAP }
        val afterDismiss = analyze(entries, config(checkGaps = false), setOf(overlap.key))
        assertEquals(0, afterDismiss.count { it.key == overlap.key })
    }

    @Test fun `issues are ordered by start time descending`() {
        val issues = analyze(
            listOf(
                entry("a", "2026-07-06T09:00:00Z", "2026-07-06T09:30:00Z"),
                entry("b", "2026-07-06T15:00:00Z", "2026-07-06T15:30:00Z"),
            ),
            config(checkOverlaps = false),
        )
        val starts = issues.map { it.startMs }
        assertEquals(starts.sortedDescending(), starts)
    }

    @Test fun `disabled checks produce no issues`() {
        val issues = analyze(
            listOf(
                entry("a", "2026-07-06T09:00:00Z", "2026-07-06T11:00:00Z"),
                entry("b", "2026-07-06T10:00:00Z", "2026-07-06T12:00:00Z", projectId = null),
            ),
            config(
                checkGaps = false,
                checkOverlaps = false,
                checkMissingProject = false,
                checkLongDuration = false,
            ),
        )
        assertEquals(0, issues.size)
    }

    // ---- Horizon resolver (SV-005) --------------------------------------------------------------

    @Test fun `resolver not chosen returns start of today in zone`() {
        val midday = Instant.parse("2026-07-20T13:45:00Z").toEpochMilli()
        val expected = Instant.parse("2026-07-20T00:00:00Z").toEpochMilli()
        assertEquals(expected, resolveHorizonStartMs(horizonChosen = false, horizonStartMs = null, nowMs = midday, zone = utc))
        // A stored start is ignored while the choice is not yet made.
        assertEquals(expected, resolveHorizonStartMs(horizonChosen = false, horizonStartMs = 123L, nowMs = midday, zone = utc))
    }

    @Test fun `resolver chosen with null returns now minus 370 days`() {
        val expected = nowMs - java.time.Duration.ofDays(370).toMillis()
        assertEquals(expected, resolveHorizonStartMs(horizonChosen = true, horizonStartMs = null, nowMs = nowMs, zone = utc))
    }

    @Test fun `resolver chosen with value returns that value`() {
        val chosen = Instant.parse("2026-07-01T00:00:00Z").toEpochMilli()
        assertEquals(chosen, resolveHorizonStartMs(horizonChosen = true, horizonStartMs = chosen, nowMs = nowMs, zone = utc))
    }

    // ---- Horizon clamp (SV-005) -----------------------------------------------------------------

    @Test fun `horizon of today's start hides older issues of every type but keeps today's`() {
        // "now" is midday today; entries span an old day and today. Enable every analyzer check.
        val today = Instant.parse("2026-07-20T12:00:00Z").toEpochMilli()
        val todayStart = Instant.parse("2026-07-20T00:00:00Z").toEpochMilli()
        val entries = listOf(
            // Old day (2026-07-06): overlap pair, long entry, missing project.
            entry("oldA", "2026-07-06T09:00:00Z", "2026-07-06T11:00:00Z", projectId = null, taskId = null),
            entry("oldB", "2026-07-06T10:00:00Z", "2026-07-06T16:00:00Z"),
            // Today (2026-07-20): overlap pair, long entry, missing project.
            entry("newA", "2026-07-20T00:30:00Z", "2026-07-20T02:00:00Z", projectId = null, taskId = null),
            entry("newB", "2026-07-20T01:00:00Z", "2026-07-20T07:00:00Z"),
        )
        val cfg = config(
            checkGaps = false,
            checkOverlaps = true,
            checkMissingProject = true,
            checkLongDuration = true,
            maxDurationHours = 4,
        )

        val clamped = InboxAnalyzer.analyze(entries, cfg, emptySet(), today, utc, horizonStartMs = todayStart)
        assertTrue("only today's issues survive the clamp", clamped.all { it.endMs >= todayStart })
        assertTrue("clamped must still contain today's issues", clamped.isNotEmpty())
        assertTrue(clamped.any { it.type == InboxIssueType.OVERLAP })
        assertTrue(clamped.any { it.type == InboxIssueType.MISSING_METADATA })
        assertTrue(clamped.any { it.type == InboxIssueType.LONG_DURATION })

        // An earlier horizon brings the old-day issues back.
        val wide = InboxAnalyzer.analyze(entries, cfg, emptySet(), today, utc, horizonStartMs = 0L)
        assertTrue("earlier horizon surfaces more issues", wide.size > clamped.size)
        assertTrue("older-day issues reappear", wide.any { it.endMs < todayStart })
    }

    @Test fun `count equals clamped analyze size`() {
        val today = Instant.parse("2026-07-20T12:00:00Z").toEpochMilli()
        val todayStart = Instant.parse("2026-07-20T00:00:00Z").toEpochMilli()
        val entries = listOf(
            entry("oldA", "2026-07-06T09:00:00Z", "2026-07-06T14:00:00Z"),
            entry("newA", "2026-07-20T00:30:00Z", "2026-07-20T06:00:00Z"),
        )
        val cfg = config(checkGaps = false, checkOverlaps = false, checkLongDuration = true, maxDurationHours = 4)
        val clamped = InboxAnalyzer.analyze(entries, cfg, emptySet(), today, utc, horizonStartMs = todayStart)
        val count = InboxAnalyzer.count(entries, cfg, emptySet(), today, utc, horizonStartMs = todayStart)
        assertEquals(clamped.size, count)
    }

    @Test fun `issue straddling the horizon is kept`() {
        // An entry that starts before today's start but ends after it must survive a today-clamp.
        val today = Instant.parse("2026-07-20T12:00:00Z").toEpochMilli()
        val todayStart = Instant.parse("2026-07-20T00:00:00Z").toEpochMilli()
        val straddling = entry("s", "2026-07-19T22:00:00Z", "2026-07-20T04:00:00Z") // 6h, crosses midnight
        val cfg = config(checkGaps = false, checkOverlaps = false, checkLongDuration = true, maxDurationHours = 4)
        val clamped = InboxAnalyzer.analyze(listOf(straddling), cfg, emptySet(), today, utc, horizonStartMs = todayStart)
        assertEquals(1, clamped.count { it.type == InboxIssueType.LONG_DURATION })
    }
}

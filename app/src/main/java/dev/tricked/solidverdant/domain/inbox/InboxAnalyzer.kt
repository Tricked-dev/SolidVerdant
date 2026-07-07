package dev.tricked.solidverdant.domain.inbox

import dev.tricked.solidverdant.data.model.TimeEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A single, deterministic "check" surfaced by the Time Inbox (gap analysis #16/#17).
 *
 * Every issue carries a stable [key] built from the factual subject of the check (entry ids and the
 * exact instants involved) plus [RULE_VERSION]. When the underlying facts change — an entry is
 * re-timed or re-categorised — the key changes, so a previous dismissal stops applying (see gap
 * analysis #76). The key is what is persisted in the `inbox_dismissals` table.
 */
data class InboxIssue(
    val key: String,
    val type: InboxIssueType,
    /** Start of the window the issue is about (gap window, or the entry's start), epoch millis. */
    val startMs: Long,
    /** End of the window the issue is about (gap window, or the entry's end), epoch millis. */
    val endMs: Long,
    /** The entry the quick-fix edits (null for gaps, which create a new entry). */
    val primaryEntry: TimeEntry? = null,
    /** For overlaps, the other entry involved. */
    val secondaryEntry: TimeEntry? = null,
    /** For [InboxIssueType.MISSING_METADATA], which fields are absent. */
    val missingFields: Set<MissingField> = emptySet(),
)

enum class InboxIssueType { OVERLAP, GAP, MISSING_METADATA, LONG_DURATION }

enum class MissingField { PROJECT, TASK, DESCRIPTION, TAGS }

/**
 * User-tunable inputs for the checks. Working hours and the minimum gap are inbox-local state
 * because Solidtime does not describe them (gap analysis #17); [maxDurationHours] mirrors the
 * existing "long timer" preference so the two long-entry surfaces agree.
 *
 * All fields are plain data so [InboxAnalyzer] stays a pure, unit-testable function.
 */
data class InboxCheckConfig(
    val workDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    ),
    /** Minutes since local midnight the working window opens (0..1440). */
    val workStartMinute: Int = 9 * 60,
    /** Minutes since local midnight the working window closes (0..1440). */
    val workEndMinute: Int = 17 * 60,
    /** Gaps shorter than this are ignored so harmless transitions do not fill the inbox. */
    val minGapMinutes: Int = 30,
    /** Completed entries at or above this many hours are flagged. */
    val maxDurationHours: Int = 4,
    val checkGaps: Boolean = true,
    val checkOverlaps: Boolean = true,
    val checkMissingProject: Boolean = true,
    val checkMissingTask: Boolean = false,
    val checkMissingDescription: Boolean = false,
    val checkMissingTags: Boolean = false,
    val checkLongDuration: Boolean = true,
) {
    val anyMissingCheckEnabled: Boolean
        get() = checkMissingProject || checkMissingTask || checkMissingDescription || checkMissingTags
}

/**
 * Turns cached time entries into a deterministic, ordered list of review checks. No statistical
 * anomaly detection is used: overlaps are exact interval intersections and gaps are the uncovered
 * parts of the configured working window on days the user actually tracked something.
 *
 * The analyzer is pure (only `java.time`) so the same code drives both the Inbox pane and the
 * bottom-nav badge count, and can be exhaustively unit-tested for boundaries and timezones.
 */
object InboxAnalyzer {
    const val RULE_VERSION = "v1"

    /** Dismissals older than this are treated as expired so local storage does not grow forever. */
    const val DISMISSAL_RETENTION_DAYS = 45L

    /** Bounds gap iteration for a pathologically long-running entry. */
    private const val MAX_GAP_LOOKBACK_DAYS = 370L

    private data class Interval(val start: Instant, val end: Instant, val entry: TimeEntry)

    fun analyze(
        entries: List<TimeEntry>,
        config: InboxCheckConfig,
        dismissedKeys: Set<String>,
        nowMs: Long,
        zone: ZoneId,
    ): List<InboxIssue> {
        val now = Instant.ofEpochMilli(nowMs)
        val intervals = entries.mapNotNull { entry ->
            val start = parseInstant(entry.start) ?: return@mapNotNull null
            val end = entry.end?.let { parseInstant(it) } ?: now
            if (end.isAfter(start)) Interval(start, end, entry) else null
        }.sortedBy { it.start }

        val issues = buildList {
            if (config.checkOverlaps) addAll(overlapIssues(intervals))
            if (config.checkGaps) addAll(gapIssues(intervals, config, now, zone))
            if (config.anyMissingCheckEnabled) addAll(missingIssues(entries, config))
            if (config.checkLongDuration) addAll(longDurationIssues(entries, config))
        }

        return issues
            .filterNot { it.key in dismissedKeys }
            .sortedWith(
                compareByDescending<InboxIssue> { it.startMs }
                    .thenBy { it.type.ordinal }
                    .thenBy { it.key },
            )
    }

    /** Convenience for the badge: how many open checks there are for the given inputs. */
    fun count(
        entries: List<TimeEntry>,
        config: InboxCheckConfig,
        dismissedKeys: Set<String>,
        nowMs: Long,
        zone: ZoneId,
    ): Int = analyze(entries, config, dismissedKeys, nowMs, zone).size

    // ---- Overlaps -------------------------------------------------------------------------------

    private fun overlapIssues(intervals: List<Interval>): List<InboxIssue> {
        val issues = mutableListOf<InboxIssue>()
        val active = mutableListOf<Interval>()
        intervals.forEach { current ->
            // Adjacent entries (end == next start) do not overlap: drop anything that ends at or
            // before the current start.
            active.removeAll { !it.end.isAfter(current.start) }
            active.forEach { other ->
                issues += InboxIssue(
                    key = overlapKey(other.entry, current.entry),
                    type = InboxIssueType.OVERLAP,
                    startMs = maxOf(current.start, other.start).toEpochMilli(),
                    endMs = minOf(current.end, other.end).toEpochMilli(),
                    primaryEntry = current.entry,
                    secondaryEntry = other.entry,
                )
            }
            active += current
        }
        return issues
    }

    // ---- Gaps -----------------------------------------------------------------------------------

    private fun gapIssues(
        intervals: List<Interval>,
        config: InboxCheckConfig,
        now: Instant,
        zone: ZoneId,
    ): List<InboxIssue> {
        if (intervals.isEmpty() || config.workDays.isEmpty()) return emptyList()
        if (config.workEndMinute <= config.workStartMinute) return emptyList()
        if (config.minGapMinutes <= 0) return emptyList()

        val today = now.atZone(zone).toLocalDate()
        val earliestAllowed = today.minusDays(MAX_GAP_LOOKBACK_DAYS)

        // Days the user actually tracked something (so fully-empty days are not flagged).
        val candidateDates = sortedSetOf<LocalDate>()
        intervals.forEach { interval ->
            var day = maxOf(interval.start.atZone(zone).toLocalDate(), earliestAllowed)
            val lastDay = interval.end.atZone(zone).toLocalDate()
            while (!day.isAfter(lastDay) && !day.isAfter(today)) {
                candidateDates += day
                day = day.plusDays(1)
            }
        }

        val minGapSeconds = config.minGapMinutes * 60L
        val issues = mutableListOf<InboxIssue>()
        candidateDates.forEach { date ->
            if (date.dayOfWeek !in config.workDays) return@forEach
            val dayStart = date.atStartOfDay(zone)
            val windowStart = dayStart.plusMinutes(config.workStartMinute.toLong()).toInstant()
            var windowEnd = dayStart.plusMinutes(config.workEndMinute.toLong()).toInstant()
            if (date == today && windowEnd.isAfter(now)) windowEnd = now
            if (!windowEnd.isAfter(windowStart)) return@forEach

            // Coverage for this day, clipped to the working window and merged.
            val covered = intervals.mapNotNull { interval ->
                val s = maxOf(interval.start, windowStart)
                val e = minOf(interval.end, windowEnd)
                if (e.isAfter(s)) s to e else null
            }.sortedBy { it.first }
            if (covered.isEmpty()) return@forEach // no tracked time inside the window that day

            var cursor = windowStart
            val merged = mutableListOf<Pair<Instant, Instant>>()
            covered.forEach { (s, e) ->
                if (merged.isEmpty() || s.isAfter(merged.last().second)) {
                    merged += s to e
                } else if (e.isAfter(merged.last().second)) {
                    merged[merged.lastIndex] = merged.last().first to e
                }
            }
            merged.forEach { (s, e) ->
                if (s.epochSecond - cursor.epochSecond >= minGapSeconds) {
                    issues += gapIssue(cursor, s)
                }
                if (e.isAfter(cursor)) cursor = e
            }
            if (windowEnd.epochSecond - cursor.epochSecond >= minGapSeconds) {
                issues += gapIssue(cursor, windowEnd)
            }
        }
        return issues
    }

    private fun gapIssue(start: Instant, end: Instant) = InboxIssue(
        key = "gap:$RULE_VERSION:${start.epochSecond}:${end.epochSecond}",
        type = InboxIssueType.GAP,
        startMs = start.toEpochMilli(),
        endMs = end.toEpochMilli(),
    )

    // ---- Missing metadata -----------------------------------------------------------------------

    private fun missingIssues(entries: List<TimeEntry>, config: InboxCheckConfig): List<InboxIssue> =
        entries.mapNotNull { entry ->
            if (entry.end == null) return@mapNotNull null // still running; handled on Track
            val start = parseInstant(entry.start) ?: return@mapNotNull null
            val end = parseInstant(entry.end) ?: return@mapNotNull null
            val missing = buildSet {
                if (config.checkMissingProject && entry.projectId == null) add(MissingField.PROJECT)
                // A missing task is only meaningful once a project is chosen.
                if (config.checkMissingTask && entry.projectId != null && entry.taskId == null) {
                    add(MissingField.TASK)
                }
                if (config.checkMissingDescription && entry.description.isNullOrBlank()) {
                    add(MissingField.DESCRIPTION)
                }
                if (config.checkMissingTags && entry.tags.isEmpty()) add(MissingField.TAGS)
            }
            if (missing.isEmpty()) return@mapNotNull null
            val fieldToken = missing.map { it.name }.sorted().joinToString(",")
            InboxIssue(
                key = "missing:$RULE_VERSION:${entry.id}:${start.epochSecond}:$fieldToken",
                type = InboxIssueType.MISSING_METADATA,
                startMs = start.toEpochMilli(),
                endMs = end.toEpochMilli(),
                primaryEntry = entry,
                missingFields = missing,
            )
        }

    // ---- Long duration --------------------------------------------------------------------------

    private fun longDurationIssues(entries: List<TimeEntry>, config: InboxCheckConfig): List<InboxIssue> {
        if (config.maxDurationHours <= 0) return emptyList()
        val thresholdSeconds = config.maxDurationHours * 3600L
        return entries.mapNotNull { entry ->
            val endRaw = entry.end ?: return@mapNotNull null
            val start = parseInstant(entry.start) ?: return@mapNotNull null
            val end = parseInstant(endRaw) ?: return@mapNotNull null
            if (!end.isAfter(start)) return@mapNotNull null
            if (end.epochSecond - start.epochSecond < thresholdSeconds) return@mapNotNull null
            InboxIssue(
                key = "long:$RULE_VERSION:${entry.id}:${config.maxDurationHours}:${start.epochSecond}:${end.epochSecond}",
                type = InboxIssueType.LONG_DURATION,
                startMs = start.toEpochMilli(),
                endMs = end.toEpochMilli(),
                primaryEntry = entry,
            )
        }
    }

    // ---- Keys / parsing -------------------------------------------------------------------------

    private fun overlapKey(a: TimeEntry, b: TimeEntry): String {
        // Order the pair by id so the same two entries always produce the same key.
        val (first, second) = if (a.id <= b.id) a to b else b to a
        return "overlap:$RULE_VERSION:${first.id}:${second.id}:" +
            "${endToken(first.start)}:${endToken(first.end)}:${endToken(second.start)}:${endToken(second.end)}"
    }

    /** Stable token for an instant string: epoch seconds, or "open" for a still-running end. */
    private fun endToken(iso: String?): String {
        if (iso == null) return "open"
        return parseInstant(iso)?.epochSecond?.toString() ?: iso
    }

    private fun parseInstant(value: String): Instant? =
        runCatching { OffsetDateTime.parse(value).toInstant() }
            .recoverCatching { Instant.parse(value) }
            .recoverCatching { ZonedDateTime.parse(value).toInstant() }
            .getOrNull()
}

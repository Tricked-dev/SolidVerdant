/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.review

import dev.tricked.solidverdant.data.model.TimeEntry

/**
 * The kind of correction a single review step represents. Ordered by urgency: a still-running timer
 * is the most time-sensitive, a change that failed to sync is next, and an uncategorized entry is a
 * quality fix (gap analysis #18 — the review "prioritizes corrections over analytics").
 */
enum class ReviewItemType { RUNNING_TIMER, FAILED_SYNC, UNCATEGORIZED }

/**
 * One actionable item in the guided end-of-day review. [id] is a stable key derived from the
 * underlying data (never a list index) so the step machine stays correct as items are resolved or
 * as new items appear. Text fields hold the user's own data for on-screen display only; they are
 * never logged.
 */
data class ReviewItem(
    val id: String,
    val type: ReviewItemType,
    val entryId: String,
    val description: String? = null,
    val startIso: String? = null,
    val endIso: String? = null,
    val detail: String? = null,
)

/** Progress through the guided review, expressed as a stable "completed of total". */
data class ReviewProgress(val completed: Int, val total: Int) {
    val isComplete: Boolean get() = total == 0 || completed >= total

    /** 1-based index of the current step, clamped so it is meaningful even when complete. */
    val position: Int get() = (completed + 1).coerceIn(1, maxOf(total, 1))

    val fraction: Float get() = if (total == 0) 1f else (completed.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * Pure step-state machine for the guided review.
 *
 * The "current step" is the first item the user has not yet handled. It is derived from the live
 * item list plus the set of handled ids rather than stored as an index, so it stays correct when
 * the underlying list changes: an item disappears once its correction lands (e.g. a project is
 * assigned), the running-timer item vanishes when the timer stops, and a newly-failed sync item can
 * appear mid-review. Because it is a pure function of its inputs it is trivially unit-testable.
 */
object ReviewStepMachine {

    /** The next item to act on, or null when everything is handled. */
    fun current(items: List<ReviewItem>, handledIds: Set<String>): ReviewItem? = items.firstOrNull { it.id !in handledIds }

    fun isComplete(items: List<ReviewItem>, handledIds: Set<String>): Boolean = items.all { it.id in handledIds }

    /**
     * Progress that does not regress as items are resolved: an item the user handled which has
     * since dropped out of [items] still counts toward the denominator, so the "x of n" total is
     * stable across a single review session even while the live list shrinks.
     */
    fun progress(items: List<ReviewItem>, handledIds: Set<String>): ReviewProgress {
        val itemIds = items.mapTo(HashSet()) { it.id }
        val goneHandled = handledIds.count { it !in itemIds }
        val total = items.size + goneHandled
        val completed = handledIds.size.coerceAtMost(total)
        return ReviewProgress(completed = completed, total = total)
    }
}

/**
 * Immutable snapshot of the end-of-day review for a single local day. Built by
 * [ReviewDayViewModel] from cached Room data so it works fully offline (gap analysis #18).
 */
data class ReviewDayUiState(
    val loading: Boolean = true,
    val hasOrganization: Boolean = true,
    val dateEpochDay: Long = 0L,
    val totalTrackedSeconds: Long = 0L,
    val billableSeconds: Long = 0L,
    val entryCount: Int = 0,
    val largestGapSeconds: Long = 0L,
    val uncategorizedCount: Int = 0,
    val failedSyncCount: Int = 0,
    val items: List<ReviewItem> = emptyList(),
    val handledIds: Set<String> = emptySet(),
    val runningEntry: TimeEntry? = null,
    val uncategorizedById: Map<String, TimeEntry> = emptyMap(),
    val projects: List<ReviewProject> = emptyList(),
) {
    /** Billable share of tracked time, 0..100, or null when nothing is tracked. */
    val billablePercent: Int?
        get() = if (totalTrackedSeconds > 0) {
            ((billableSeconds * 100) / totalTrackedSeconds).toInt().coerceIn(0, 100)
        } else {
            null
        }

    val progress: ReviewProgress get() = ReviewStepMachine.progress(items, handledIds)
    val currentItem: ReviewItem? get() = ReviewStepMachine.current(items, handledIds)

    /** True when the day has entries but no corrections remain (the "all caught up" success state). */
    val allCaughtUp: Boolean get() = items.isNotEmpty() && currentItem == null

    /** True when there is simply nothing to review yet (no entries and no items). */
    val nothingTracked: Boolean get() = entryCount == 0 && items.isEmpty()
}

/** Minimal project projection for the "assign project" picker (id, display name, color hex). */
data class ReviewProject(val id: String, val name: String, val color: String)

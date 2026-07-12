/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.review

import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.domain.inbox.InboxCheckConfig
import dev.tricked.solidverdant.domain.inbox.InboxIssue
import java.time.ZoneId

/** Transient, one-shot failures the Inbox surfaces as a snackbar. Mapped to strings in the pane. */
enum class InboxActionError { REFRESH_FAILED, CREATE_FAILED, RESOLVE_FAILED }

/**
 * SV-005 first-run horizon choices ("how far back should Review look?"). The ViewModel maps each to
 * an epoch-millis lower bound in the account zone / week-start, or null for [EVERYTHING].
 */
enum class HorizonOption { TODAY, THIS_WEEK, LAST_30_DAYS, EVERYTHING }

/**
 * Everything the [InboxPane] renders. The list of [issues] is already filtered for dismissals and
 * ordered by [dev.tricked.solidverdant.domain.inbox.InboxAnalyzer]; the catalogue lists back the
 * reused edit dialog so a quick-fix can reassign project/task/tags.
 */
data class InboxUiState(
    val isLoading: Boolean = true,
    val organizationId: String? = null,
    val issues: List<InboxIssue> = emptyList(),
    /** True when the org has any cached entries at all (distinguishes "caught up" from "no data"). */
    val hasEntries: Boolean = false,
    val isRefreshing: Boolean = false,
    /** A background refresh failed; cached results are still shown (offline / stale). */
    val refreshError: Boolean = false,
    val actionError: InboxActionError? = null,
    /** Key of the just-dismissed issue awaiting an undo window. */
    val pendingUndoKey: String? = null,
    val config: InboxCheckConfig = InboxCheckConfig(),
    /** Whether the org enforces `prevent_overlapping_time_entries` (affects overlap wording). */
    val preventOverlap: Boolean = false,
    val projects: List<Project> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val tags: List<Tag> = emptyList(),
    /** Account temporal-policy zone for gap windows and shown-in-zone formatting. */
    val zone: ZoneId = ZoneId.systemDefault(),
    /**
     * SV-005 inbox horizon. Before the user picks ([horizonChosen] false) the pane shows the one-time
     * picker instead of the list. Once chosen, [horizonStartMs] drives the chip: null = "Everything".
     */
    val horizonChosen: Boolean = false,
    val horizonStartMs: Long? = null,
) {
    /** All issues resolved: show the reassuring "all caught up" state. */
    val isCaughtUp: Boolean get() = !isLoading && issues.isEmpty()
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task

/** Billable dimension of the statistics filter. */
enum class BillableFilter { All, Billable, NonBillable }

/**
 * The active statistics filter scope. Organization is already fixed by the current membership, so
 * these narrow the visible entries by project, client, task, tag and billable state. An empty set
 * for a dimension means "no restriction" on that dimension; [isActive] and [activeCount] drive the
 * on-screen active-scope summary and the clear/reset affordances.
 */
data class StatFilters(
    val projectIds: Set<String> = emptySet(),
    val clientIds: Set<String> = emptySet(),
    val taskIds: Set<String> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val billable: BillableFilter = BillableFilter.All,
) {
    val isActive: Boolean
        get() = projectIds.isNotEmpty() ||
            clientIds.isNotEmpty() ||
            taskIds.isNotEmpty() ||
            tagIds.isNotEmpty() ||
            billable != BillableFilter.All

    /** Number of distinct active constraints, used for the "N active" badge. */
    val activeCount: Int
        get() = projectIds.size + clientIds.size + taskIds.size + tagIds.size +
            (if (billable != BillableFilter.All) 1 else 0)

    fun toggleProject(id: String) = copy(projectIds = projectIds.toggle(id))
    fun toggleClient(id: String) = copy(clientIds = clientIds.toggle(id))
    fun toggleTask(id: String) = copy(taskIds = taskIds.toggle(id))
    fun toggleTag(id: String) = copy(tagIds = tagIds.toggle(id))

    companion object {
        private fun Set<String>.toggle(id: String): Set<String> = if (contains(id)) this - id else this + id
    }
}

/**
 * Catalogue snapshot used to populate the filter controls and resolve human-readable labels for
 * chips, drill-down rows and CSV export. Held per organization; empty until the caches load.
 */
data class StatCatalog(
    val projects: List<Project> = emptyList(),
    val clients: List<Client> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

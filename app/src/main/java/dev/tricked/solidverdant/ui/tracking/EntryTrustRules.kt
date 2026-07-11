/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.PriorityQueue

data class HistoryFilter(
    val query: String = "",
    val billable: Boolean? = null,
    val projectId: String? = null,
    val clientId: String? = null,
    val taskId: String? = null,
    val tagId: String? = null,
    val runningOnly: Boolean = false,
    val missingProjectOnly: Boolean = false,
    val missingDescriptionOnly: Boolean = false,
    val needsCategorization: Boolean = false,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val syncStatus: TimeEntryRepository.EntrySyncStatus? = null,
)

/** Deterministic, local checks. Server policy remains authoritative. */
object EntryTrustRules {
    fun overlapCount(entries: List<TimeEntry>, now: Instant = Instant.now()): Int =
        entries.groupBy { it.organizationId }.values.sumOf { organizationEntries ->
            val intervals = organizationEntries.mapNotNull { entry ->
                val start = entry.start.toInstantOrNull() ?: return@mapNotNull null
                val end = entry.end?.toInstantOrNull() ?: now
                if (end.isAfter(start)) start to end else null
            }.sortedBy { it.first }
            val activeEnds = PriorityQueue<Instant>()
            var count = 0
            intervals.forEach { (start, end) ->
                while (activeEnds.peek()?.let { !it.isAfter(start) } == true) activeEnds.poll()
                count += activeEnds.size
                activeEnds += end
            }
            count
        }

    fun overlaps(first: TimeEntry, second: TimeEntry, now: Instant = Instant.now()): Boolean {
        if (first.organizationId != second.organizationId || first.id == second.id) return false
        val firstStart = first.start.toInstantOrNull() ?: return false
        val secondStart = second.start.toInstantOrNull() ?: return false
        val firstEnd = first.end?.toInstantOrNull() ?: now
        val secondEnd = second.end?.toInstantOrNull() ?: now
        if (!firstEnd.isAfter(firstStart) || !secondEnd.isAfter(secondStart)) return false
        return firstStart < secondEnd && secondStart < firstEnd
    }

    fun isLongRunning(entry: TimeEntry, threshold: Duration, now: Instant = Instant.now()): Boolean {
        if (entry.end != null || threshold.isNegative || threshold.isZero) return false
        val start = entry.start.toInstantOrNull() ?: return false
        return Duration.between(start, now) >= threshold
    }

    fun filter(
        entries: List<TimeEntry>,
        filter: HistoryFilter,
        projects: List<Project>,
        tasks: List<Task>,
        clients: List<Client> = emptyList(),
        syncOperations: List<TimeEntryRepository.SyncOperation> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TimeEntry> {
        val query = filter.query.trim()
        val projectNames = projects.associate { it.id to it.name }
        val taskNames = tasks.associate { it.id to it.name }
        val clientNames = clients.associate { it.id to it.name }
        val clientIdByProject = projects.associate { it.id to it.clientId }
        return entries.filter { entry ->
            val searchable = buildList {
                add(entry.description.orEmpty())
                add(projectNames[entry.projectId].orEmpty())
                add(taskNames[entry.taskId].orEmpty())
                add(clientNames[clientIdByProject[entry.projectId]].orEmpty())
                addAll(entry.tags.map { it.name })
            }
            val entryDate = entry.start.toInstantOrNull()?.atZone(zone)?.toLocalDate()
            val status = syncOperations.lastOrNull { it.entryId == entry.id }?.status
            (query.isBlank() || searchable.any { it.contains(query, ignoreCase = true) }) &&
                (filter.billable == null || entry.billable == filter.billable) &&
                (filter.projectId == null || entry.projectId == filter.projectId) &&
                (filter.clientId == null || clientIdByProject[entry.projectId] == filter.clientId) &&
                (filter.taskId == null || entry.taskId == filter.taskId) &&
                (filter.tagId == null || entry.tags.any { it.id == filter.tagId }) &&
                (!filter.runningOnly || entry.end == null) &&
                (!filter.missingProjectOnly || entry.projectId == null) &&
                (!filter.missingDescriptionOnly || entry.description.isNullOrBlank()) &&
                (!filter.needsCategorization || entry.projectId == null || entry.description.isNullOrBlank()) &&
                (filter.startDate == null || (entryDate != null && !entryDate.isBefore(filter.startDate))) &&
                (filter.endDate == null || (entryDate != null && !entryDate.isAfter(filter.endDate))) &&
                (filter.syncStatus == null || status == filter.syncStatus)
        }
    }

    private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()
}

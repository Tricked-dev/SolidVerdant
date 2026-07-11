/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.remote

import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntriesResponse
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.AuthRepository
import javax.inject.Inject

data class TimeEntriesQuery(
    val organizationId: String,
    val memberId: String,
    val limit: Int,
    val offset: Int,
    val onlyFullDates: Boolean,
    val start: String? = null,
    val end: String? = null,
)

interface RemoteDataSource {
    suspend fun getTimeEntries(query: TimeEntriesQuery): Result<TimeEntriesResponse>
    suspend fun getProjects(organizationId: String): Result<List<Project>>
    suspend fun getClients(organizationId: String): Result<List<Client>>
    suspend fun getTasks(organizationId: String): Result<List<Task>>
    suspend fun getTags(organizationId: String): Result<List<Tag>>
    suspend fun getActiveTimeEntry(): Result<TimeEntry?>
    suspend fun getMyMemberships(): Result<List<Membership>>
    suspend fun startTimeEntry(
        organizationId: String,
        memberId: String,
        userId: String,
        projectId: String?,
        taskId: String?,
        description: String,
        // Capture-time start (ISO-8601) so an offline-queued START keeps its real start time.
        startTime: String,
    ): Result<TimeEntry>
    suspend fun createTimeEntry(
        organizationId: String,
        memberId: String,
        userId: String,
        entry: TimeEntry,
        tags: List<String>,
    ): Result<TimeEntry>

    // endTime: capture-time end (ISO-8601) so an offline-queued STOP keeps its real end time.
    suspend fun stopTimeEntry(
        organizationId: String,
        timeEntryId: String,
        userId: String,
        startTime: String,
        endTime: String,
    ): Result<TimeEntry>
    suspend fun updateTimeEntry(organizationId: String, timeEntry: TimeEntry, tags: List<String>): Result<TimeEntry>
    suspend fun deleteTimeEntry(organizationId: String, timeEntryId: String): Result<Unit>
}

class AuthRemoteDataSource @Inject constructor(private val authRepository: AuthRepository) : RemoteDataSource {
    override suspend fun getTimeEntries(query: TimeEntriesQuery) = authRepository.getTimeEntries(
        query.organizationId,
        query.memberId,
        query.limit,
        query.offset,
        query.onlyFullDates,
        query.start,
        query.end,
    )
    override suspend fun getProjects(organizationId: String) = authRepository.getProjects(organizationId)
    override suspend fun getClients(organizationId: String) = authRepository.getClients(organizationId)
    override suspend fun getTasks(organizationId: String) = authRepository.getTasks(organizationId)
    override suspend fun getTags(organizationId: String) = authRepository.getTags(organizationId)
    override suspend fun getActiveTimeEntry() = authRepository.getActiveTimeEntry()
    override suspend fun getMyMemberships() = authRepository.getMyMemberships()
    override suspend fun startTimeEntry(
        organizationId: String,
        memberId: String,
        userId: String,
        projectId: String?,
        taskId: String?,
        description: String,
        startTime: String,
    ) = authRepository.startTimeEntry(organizationId, memberId, userId, projectId, taskId, description, startIso = startTime)
    override suspend fun createTimeEntry(organizationId: String, memberId: String, userId: String, entry: TimeEntry, tags: List<String>) =
        authRepository.createTimeEntry(
            organizationId, memberId, userId, entry.start, requireNotNull(entry.end),
            entry.description.orEmpty(), entry.projectId, entry.taskId, tags, entry.billable,
        )
    override suspend fun stopTimeEntry(organizationId: String, timeEntryId: String, userId: String, startTime: String, endTime: String) =
        authRepository.stopTimeEntry(organizationId, timeEntryId, userId, startTime, endIso = endTime)
    override suspend fun updateTimeEntry(organizationId: String, timeEntry: TimeEntry, tags: List<String>) =
        authRepository.updateTimeEntry(organizationId, timeEntry, tags)
    override suspend fun deleteTimeEntry(organizationId: String, timeEntryId: String) =
        authRepository.deleteTimeEntry(organizationId, timeEntryId)
}

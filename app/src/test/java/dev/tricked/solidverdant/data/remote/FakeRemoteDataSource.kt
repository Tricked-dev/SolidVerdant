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

class FakeRemoteDataSource(
    var entries: List<TimeEntry> = emptyList(),
    var projects: List<Project> = emptyList(),
    var clients: List<Client> = emptyList(),
    var tasks: List<Task> = emptyList(),
    var tags: List<Tag> = emptyList(),
    var active: TimeEntry? = null,
    var memberships: List<Membership> = emptyList(),
    var failNextWrite: Boolean = false,
    /** When set, every write fails with this throwable (use a non-IOException to exercise FAIL). */
    var writeError: Throwable? = null,
    var startResult: (TimeEntry) -> TimeEntry = { it },
    var stopResult: (TimeEntry) -> TimeEntry = { it },
    var updateResult: (TimeEntry) -> TimeEntry = { it },
) : RemoteDataSource {
    val started = mutableListOf<Triple<String, String?, String?>>()

    // Capture-time timestamps received on the last START/STOP call, for SV-017 assertions.
    var lastStartTime: String? = null
    var lastEndTime: String? = null
    val deleted = mutableListOf<String>()

    override suspend fun getTimeEntries(
        organizationId: String,
        memberId: String,
        limit: Int,
        offset: Int,
        onlyFullDates: Boolean,
        start: String?,
        end: String?,
    ) = Result.success(TimeEntriesResponse(data = entries))
    override suspend fun getProjects(organizationId: String) = Result.success(projects)
    override suspend fun getClients(organizationId: String) = Result.success(clients)
    override suspend fun getTasks(organizationId: String) = Result.success(tasks)
    override suspend fun getTags(organizationId: String) = Result.success(tags)
    override suspend fun getActiveTimeEntry() = Result.success(active)
    override suspend fun getMyMemberships() = Result.success(memberships)

    override suspend fun startTimeEntry(
        organizationId: String,
        memberId: String,
        userId: String,
        projectId: String?,
        taskId: String?,
        description: String,
        startTime: String,
    ): Result<TimeEntry> {
        writeError?.let { return Result.failure(it) }
        if (failNextWrite) return Result.failure(java.io.IOException("offline"))
        started += Triple(description, projectId, taskId)
        lastStartTime = startTime
        return Result.success(
            startResult(
                TimeEntry(
                    id = "server-1",
                    description = description,
                    userId = userId,
                    // Echo the capture-time start the caller sent, so a test can assert the offline
                    // START timestamp is preserved rather than fabricated at sync time (SV-017).
                    start = startTime.ifBlank { "2026-01-01T09:00:00Z" },
                    end = null,
                    projectId = projectId,
                    taskId = taskId,
                    organizationId = organizationId,
                ),
            ),
        )
    }
    override suspend fun createTimeEntry(organizationId: String, memberId: String, userId: String, entry: TimeEntry, tags: List<String>) =
        writeError?.let { Result.failure(it) }
            ?: if (failNextWrite) Result.failure(java.io.IOException("offline")) else Result.success(startResult(entry))
    override suspend fun stopTimeEntry(organizationId: String, timeEntryId: String, userId: String, startTime: String, endTime: String) =
        writeError?.let { Result.failure(it) }
            ?: if (failNextWrite) {
                Result.failure(java.io.IOException("offline"))
            } else {
                lastEndTime = endTime
                Result.success(
                    stopResult(
                        TimeEntry(
                            id = timeEntryId,
                            userId = userId,
                            start = startTime,
                            // Echo the capture-time end the caller sent (SV-017).
                            end = endTime.ifBlank { "2026-01-01T10:00:00Z" },
                            organizationId = organizationId,
                        ),
                    ),
                )
            }
    override suspend fun updateTimeEntry(organizationId: String, timeEntry: TimeEntry, tags: List<String>) =
        writeError?.let { Result.failure(it) }
            ?: if (failNextWrite) Result.failure(java.io.IOException("offline")) else Result.success(updateResult(timeEntry))
    override suspend fun deleteTimeEntry(organizationId: String, timeEntryId: String): Result<Unit> {
        writeError?.let { return Result.failure(it) }
        if (failNextWrite) return Result.failure(java.io.IOException("offline"))
        deleted += timeEntryId
        return Result.success(Unit)
    }
}

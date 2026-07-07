package dev.tricked.solidverdant.data.remote

import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntriesResponse
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.Client

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
    val deleted = mutableListOf<String>()

    override suspend fun getTimeEntries(organizationId: String, memberId: String, limit: Int, offset: Int, onlyFullDates: Boolean) =
        Result.success(TimeEntriesResponse(data = entries))
    override suspend fun getProjects(organizationId: String) = Result.success(projects)
    override suspend fun getClients(organizationId: String) = Result.success(clients)
    override suspend fun getTasks(organizationId: String) = Result.success(tasks)
    override suspend fun getTags(organizationId: String) = Result.success(tags)
    override suspend fun getActiveTimeEntry() = Result.success(active)
    override suspend fun getMyMemberships() = Result.success(memberships)

    override suspend fun startTimeEntry(organizationId: String, memberId: String, userId: String, projectId: String?, taskId: String?, description: String): Result<TimeEntry> {
        writeError?.let { return Result.failure(it) }
        if (failNextWrite) return Result.failure(java.io.IOException("offline"))
        started += Triple(description, projectId, taskId)
        return Result.success(startResult(TimeEntry(
            id = "server-1", description = description, userId = userId,
            start = "2026-01-01T09:00:00Z", end = null, projectId = projectId,
            taskId = taskId, organizationId = organizationId
        )))
    }
    override suspend fun createTimeEntry(organizationId: String, memberId: String, userId: String, entry: TimeEntry, tags: List<String>) =
        writeError?.let { Result.failure(it) }
            ?: if (failNextWrite) Result.failure(java.io.IOException("offline")) else Result.success(startResult(entry))
    override suspend fun stopTimeEntry(organizationId: String, timeEntryId: String, userId: String, startTime: String) =
        writeError?.let { Result.failure(it) }
            ?: if (failNextWrite) Result.failure(java.io.IOException("offline"))
            else Result.success(stopResult(TimeEntry(id = timeEntryId, userId = userId, start = startTime, end = "2026-01-01T10:00:00Z", organizationId = organizationId)))
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

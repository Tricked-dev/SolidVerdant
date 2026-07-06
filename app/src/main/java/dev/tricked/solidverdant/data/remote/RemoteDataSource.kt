package dev.tricked.solidverdant.data.remote

import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntriesResponse
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.AuthRepository
import javax.inject.Inject

interface RemoteDataSource {
    suspend fun getTimeEntries(organizationId: String, memberId: String, limit: Int, offset: Int, onlyFullDates: Boolean): Result<TimeEntriesResponse>
    suspend fun getProjects(organizationId: String): Result<List<Project>>
    suspend fun getTasks(organizationId: String): Result<List<Task>>
    suspend fun getTags(organizationId: String): Result<List<Tag>>
    suspend fun getActiveTimeEntry(): Result<TimeEntry?>
    suspend fun getMyMemberships(): Result<List<Membership>>
    suspend fun startTimeEntry(organizationId: String, memberId: String, userId: String, projectId: String?, taskId: String?, description: String): Result<TimeEntry>
    suspend fun stopTimeEntry(organizationId: String, timeEntryId: String, userId: String, startTime: String): Result<TimeEntry>
    suspend fun updateTimeEntry(organizationId: String, timeEntry: TimeEntry, tags: List<String>): Result<TimeEntry>
    suspend fun deleteTimeEntry(organizationId: String, timeEntryId: String): Result<Unit>
}

class AuthRemoteDataSource @Inject constructor(
    private val authRepository: AuthRepository
) : RemoteDataSource {
    override suspend fun getTimeEntries(organizationId: String, memberId: String, limit: Int, offset: Int, onlyFullDates: Boolean) =
        authRepository.getTimeEntries(organizationId, memberId, limit, offset, onlyFullDates)
    override suspend fun getProjects(organizationId: String) = authRepository.getProjects(organizationId)
    override suspend fun getTasks(organizationId: String) = authRepository.getTasks(organizationId)
    override suspend fun getTags(organizationId: String) = authRepository.getTags(organizationId)
    override suspend fun getActiveTimeEntry() = authRepository.getActiveTimeEntry()
    override suspend fun getMyMemberships() = authRepository.getMyMemberships()
    override suspend fun startTimeEntry(organizationId: String, memberId: String, userId: String, projectId: String?, taskId: String?, description: String) =
        authRepository.startTimeEntry(organizationId, memberId, userId, projectId, taskId, description)
    override suspend fun stopTimeEntry(organizationId: String, timeEntryId: String, userId: String, startTime: String) =
        authRepository.stopTimeEntry(organizationId, timeEntryId, userId, startTime)
    override suspend fun updateTimeEntry(organizationId: String, timeEntry: TimeEntry, tags: List<String>) =
        authRepository.updateTimeEntry(organizationId, timeEntry, tags)
    override suspend fun deleteTimeEntry(organizationId: String, timeEntryId: String) =
        authRepository.deleteTimeEntry(organizationId, timeEntryId)
}

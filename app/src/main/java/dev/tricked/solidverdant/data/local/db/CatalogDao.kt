package dev.tricked.solidverdant.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Upsert suspend fun upsertClients(items: List<ClientEntity>)
    @Query("SELECT * FROM clients WHERE organizationId = :orgId")
    fun observeClients(orgId: String): Flow<List<ClientEntity>>
    @Upsert suspend fun upsertProjects(items: List<ProjectEntity>)
    @Query("SELECT * FROM projects WHERE organizationId = :orgId")
    fun observeProjects(orgId: String): Flow<List<ProjectEntity>>

    @Upsert suspend fun upsertTasks(items: List<TaskEntity>)
    @Query("SELECT * FROM tasks WHERE organizationId = :orgId")
    fun observeTasks(orgId: String): Flow<List<TaskEntity>>

    @Upsert suspend fun upsertTags(items: List<TagEntity>)
    @Query("SELECT * FROM tags WHERE organizationId = :orgId")
    fun observeTags(orgId: String): Flow<List<TagEntity>>

    @Upsert suspend fun upsertOrganizations(items: List<OrganizationEntity>)
    @Upsert suspend fun upsertMemberships(items: List<MembershipEntity>)
    @Query("SELECT * FROM memberships")
    fun observeMemberships(): Flow<List<MembershipEntity>>

    @Query("DELETE FROM projects") suspend fun clearProjects()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM clients") suspend fun clearClients()
}

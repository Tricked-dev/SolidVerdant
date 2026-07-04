package dev.tricked.solidverdant.data.repository

import dev.tricked.solidverdant.data.local.CacheDataStore
import dev.tricked.solidverdant.data.local.ScreenSnapshot
import dev.tricked.solidverdant.data.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Sole owner of snapshot reads and writes; merges independently refreshed state slices. */
@Singleton
class SnapshotRepository @Inject constructor(
    private val store: CacheDataStore
) {
    private val mutex = Mutex()
    private var loaded = false
    private var snapshot: ScreenSnapshot? = null
    private var pendingAuth: Triple<User, List<Membership>, String>? = null
    private var pendingTracking: TrackingSlice? = null

    suspend fun read(): ScreenSnapshot? = mutex.withLock {
        ensureLoaded()
        snapshot
    }

    suspend fun updateAuthSlice(user: User, memberships: List<Membership>, currentMembershipId: String) = mutex.withLock {
        ensureLoaded()
        pendingAuth = Triple(user, memberships, currentMembershipId)
        val current = snapshot
        val selectedOrganizationId = memberships.firstOrNull { it.id == currentMembershipId }?.organizationId
        if (current != null && selectedOrganizationId != current.organizationId) {
            // Never leave a complete snapshot containing auth and tracking data from different orgs.
            snapshot = null
            pendingTracking = null
            store.clearCache()
        } else if (current != null) {
            persist(current.copy(user = user, memberships = memberships, currentMembershipId = currentMembershipId))
        } else {
            combinePending()?.let { persist(it) }
        }
    }

    suspend fun updateTrackingSlice(
        organizationId: String,
        timeEntries: List<TimeEntry>,
        activeEntry: TimeEntry?,
        projects: List<Project>,
        tasks: List<Task>,
        tags: List<Tag>
    ) = mutex.withLock {
        ensureLoaded()
        pendingTracking = TrackingSlice(organizationId, timeEntries, activeEntry, projects, tasks, tags)
        val current = snapshot
        if (current != null && current.organizationId == organizationId) {
            persist(current.copy(
                timeEntries = timeEntries, activeEntry = activeEntry,
                projects = projects, tasks = tasks, tags = tags
            ))
        } else if (authOrganizationId() == organizationId) {
            if (current != null) {
                snapshot = null
                store.clearCache()
            }
            combinePending()?.let { persist(it) }
        }
    }

    /** Merge tile/dialog label refreshes only into the matching complete snapshot. */
    suspend fun updateProjectsTasks(
        organizationId: String,
        projects: List<Project>,
        tasks: List<Task>
    ) = mutex.withLock {
        ensureLoaded()
        snapshot?.takeIf { it.organizationId == organizationId }?.let {
            persist(it.copy(projects = projects, tasks = tasks))
        }
    }

    suspend fun clear() = mutex.withLock {
        snapshot = null
        pendingAuth = null
        pendingTracking = null
        loaded = true
        store.clearCache()
    }

    private suspend fun ensureLoaded() {
        if (!loaded) {
            snapshot = store.readSnapshot()
            snapshot?.let { pendingAuth = Triple(it.user, it.memberships, it.currentMembershipId) }
            loaded = true
        }
    }

    private fun combinePending(): ScreenSnapshot? {
        val (user, memberships, membershipId) = pendingAuth ?: return null
        val tracking = pendingTracking ?: return null
        if (memberships.firstOrNull { it.id == membershipId }?.organizationId != tracking.organizationId) {
            return null
        }
        return ScreenSnapshot(
            organizationId = tracking.organizationId, user = user,
            memberships = memberships, currentMembershipId = membershipId,
            timeEntries = tracking.entries, activeEntry = tracking.active,
            projects = tracking.projects, tasks = tracking.tasks, tags = tracking.tags
        )
    }

    private fun authOrganizationId(): String? = pendingAuth?.let { (_, memberships, membershipId) ->
        memberships.firstOrNull { it.id == membershipId }?.organizationId
    }

    private suspend fun persist(value: ScreenSnapshot) {
        snapshot = value
        store.writeSnapshot(value)
    }

    private data class TrackingSlice(
        val organizationId: String, val entries: List<TimeEntry>, val active: TimeEntry?,
        val projects: List<Project>, val tasks: List<Task>, val tags: List<Tag>
    )
}

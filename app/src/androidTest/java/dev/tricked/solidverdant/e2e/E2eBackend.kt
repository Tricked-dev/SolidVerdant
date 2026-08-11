/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e

import android.content.Context
import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.remote.RemoteDataSource
import dev.tricked.solidverdant.data.remote.TimeEntriesQuery
import dev.tricked.solidverdant.e2e.mock.MockSolidtimeServer
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

/** Backend-independent account values installed in the app before a flow starts. */
data class E2eSession(
    val baseUrl: String,
    val accessToken: String,
    val refreshToken: String,
    val clientId: String,
    val userId: String,
    val membershipId: String,
    val organizationId: String,
    val zone: ZoneId,
)

/** Catalogue records available to the selected Solidtime account for metadata-edit workflows. */
data class E2eCatalog(val projects: List<Project>, val tasks: List<Task>, val tags: List<Tag>, val clients: List<Client> = emptyList())

/** A complete initial server world for one portable flow. */
sealed interface E2eFixture {
    data object Empty : E2eFixture

    data class Completed(val entry: TimeEntry) : E2eFixture

    data class Active(val entry: TimeEntry) : E2eFixture
}

/** Maps a stable test-owned identity to the identity assigned by the selected backend. */
data class E2eFixtureHandle(val logicalId: String?, val serverId: String?) {
    companion object {
        val Empty = E2eFixtureHandle(logicalId = null, serverId = null)
    }
}

/** A point-in-time server view plus the logical fixture identities known to the harness. */
data class E2eServerSnapshot(
    val entries: List<TimeEntry>,
    val activeEntry: TimeEntry?,
    private val logicalToServerIds: Map<String, String>,
) {
    fun serverId(logicalId: String): String = logicalToServerIds[logicalId]
        ?: error("No server entry is mapped to logical fixture '$logicalId'")

    fun entry(logicalId: String): TimeEntry? {
        val id = logicalToServerIds[logicalId] ?: logicalId
        return entries.firstOrNull { it.id == id } ?: activeEntry?.takeIf { it.id == id }
    }

    fun entry(handle: E2eFixtureHandle): TimeEntry? = handle.serverId?.let { id ->
        entries.firstOrNull { it.id == id } ?: activeEntry?.takeIf { it.id == id }
    }
}

internal interface E2eBackend : AutoCloseable {
    fun open(): E2eSession

    suspend fun prepare(fixture: E2eFixture): E2eFixtureHandle

    suspend fun catalog(): E2eCatalog

    suspend fun snapshot(): E2eServerSnapshot

    suspend fun create(entry: TimeEntry): E2eFixtureHandle

    suspend fun update(logicalId: String, entry: TimeEntry): E2eFixtureHandle

    fun mockServerOrNull(): MockSolidtimeServer? = null

    override fun close() = Unit
}

internal class MockE2eBackend : E2eBackend {
    private val server = MockSolidtimeServer()
    private val logicalToServerIds = linkedMapOf<String, String>()
    private lateinit var session: E2eSession

    override fun open(): E2eSession {
        server.start()
        presetTestCatalogue()
        return E2eSession(
            baseUrl = server.baseUrl(),
            accessToken = "test-access-token",
            refreshToken = "test-refresh-token",
            clientId = "test-client",
            userId = MockSolidtimeServer.DEFAULT_USER_ID,
            membershipId = MockSolidtimeServer.DEFAULT_MEMBERSHIP_ID,
            organizationId = MockSolidtimeServer.DEFAULT_ORG_ID,
            zone = ZoneId.of("UTC"),
        ).also { session = it }
    }

    override suspend fun prepare(fixture: E2eFixture): E2eFixtureHandle {
        server.presetLoggedInWorld(seededEntry = null)
        server.activeEntry = null
        presetTestCatalogue()
        logicalToServerIds.clear()
        return when (fixture) {
            E2eFixture.Empty -> E2eFixtureHandle.Empty
            is E2eFixture.Completed -> seed(fixture.entry.copy(end = requireNotNull(fixture.entry.end)))
            is E2eFixture.Active -> seed(fixture.entry.copy(end = null, duration = null), active = true)
        }
    }

    override suspend fun catalog(): E2eCatalog = E2eCatalog(
        projects = synchronized(server.projects) { server.projects.toList() },
        tasks = synchronized(server.tasks) { server.tasks.toList() },
        tags = synchronized(server.tags) { server.tags.toList() },
        clients = synchronized(server.clients) { server.clients.toList() },
    )

    override suspend fun snapshot(): E2eServerSnapshot {
        val entries = synchronized(server.timeEntries) { server.timeEntries.toList() }
        return E2eServerSnapshot(entries, server.activeEntry, logicalToServerIds.toMap())
    }

    override suspend fun create(entry: TimeEntry): E2eFixtureHandle = seed(entry, active = entry.end == null)

    override suspend fun update(logicalId: String, entry: TimeEntry): E2eFixtureHandle {
        val serverId = logicalToServerIds[logicalId]
            ?: error("No server entry is mapped to logical fixture '$logicalId'")
        val normalized = normalize(entry).copy(id = serverId)
        synchronized(server.timeEntries) {
            server.timeEntries.removeAll { it.id == serverId }
            server.timeEntries += normalized
        }
        if (server.activeEntry?.id == serverId || normalized.end == null) {
            server.activeEntry = normalized.takeIf { it.end == null }
        }
        return E2eFixtureHandle(logicalId, serverId)
    }

    override fun mockServerOrNull(): MockSolidtimeServer = server

    override fun close() = server.shutdown()

    private fun seed(entry: TimeEntry, active: Boolean = false): E2eFixtureHandle {
        val normalized = normalize(entry)
        server.addTimeEntry(normalized)
        if (active) server.activeEntry = normalized
        logicalToServerIds[entry.id] = normalized.id
        return E2eFixtureHandle(entry.id, normalized.id)
    }

    private fun normalize(entry: TimeEntry) = entry.copy(
        userId = session.userId,
        organizationId = session.organizationId,
    )

    private fun presetTestCatalogue() {
        server.clients.clear()
        server.clients += Client(TEST_CLIENT_ID, "Live Test Client")
        server.projects.clear()
        server.projects += Project(
            id = TEST_PROJECT_ID,
            name = "Live Test Project",
            color = "#4F46E5",
            clientId = TEST_CLIENT_ID,
            isPublic = true,
            isBillable = true,
        )
        server.tasks.clear()
        server.tasks += Task(
            id = TEST_TASK_ID,
            name = "Live Test Task",
            projectId = TEST_PROJECT_ID,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )
        server.tags.clear()
        server.tags += Tag(TEST_TAG_ID, "Live Test Tag")
    }

    companion object {
        private const val TEST_CLIENT_ID = "live-test-client"
        private const val TEST_PROJECT_ID = "live-test-project"
        private const val TEST_TASK_ID = "live-test-task"
        private const val TEST_TAG_ID = "live-test-tag"
    }
}

internal class RealSolidtimeE2eBackend(private val context: Context, private val remoteDataSource: RemoteDataSource) : E2eBackend {
    private val logicalToServerIds = linkedMapOf<String, String>()
    private val controlCallRetrier = RealControlCallRetrier()
    private lateinit var session: E2eSession

    override fun open(): E2eSession = readSession().also { session = it }

    override suspend fun prepare(fixture: E2eFixture): E2eFixtureHandle {
        deleteAllEntries()
        logicalToServerIds.clear()
        return when (fixture) {
            E2eFixture.Empty -> E2eFixtureHandle.Empty
            is E2eFixture.Completed -> create(fixture.entry.copy(end = requireNotNull(fixture.entry.end)))
            is E2eFixture.Active -> create(fixture.entry.copy(end = null, duration = null))
        }
    }

    override suspend fun catalog(): E2eCatalog = E2eCatalog(
        projects = controlCallRetrier.run { remoteDataSource.getProjects(session.organizationId) },
        tasks = controlCallRetrier.run { remoteDataSource.getTasks(session.organizationId) },
        tags = controlCallRetrier.run { remoteDataSource.getTags(session.organizationId) },
        clients = controlCallRetrier.run { remoteDataSource.getClients(session.organizationId) },
    )

    override suspend fun snapshot(): E2eServerSnapshot = E2eServerSnapshot(
        entries = fetchAllEntries(),
        activeEntry = controlCallRetrier.run { remoteDataSource.getActiveTimeEntry() },
        logicalToServerIds = logicalToServerIds.toMap(),
    )

    override suspend fun create(entry: TimeEntry): E2eFixtureHandle {
        val normalized = normalize(entry)
        val created = if (normalized.end == null) {
            controlCallRetrier.run {
                remoteDataSource.startTimeEntry(
                    organizationId = session.organizationId,
                    memberId = session.membershipId,
                    userId = session.userId,
                    projectId = normalized.projectId,
                    taskId = normalized.taskId,
                    description = normalized.description.orEmpty(),
                    startTime = normalized.start,
                )
            }
        } else {
            controlCallRetrier.run {
                remoteDataSource.createTimeEntry(
                    organizationId = session.organizationId,
                    memberId = session.membershipId,
                    userId = session.userId,
                    entry = normalized,
                    tags = normalized.tags.map { it.id },
                )
            }
        }
        logicalToServerIds[entry.id] = created.id
        return E2eFixtureHandle(entry.id, created.id)
    }

    override suspend fun update(logicalId: String, entry: TimeEntry): E2eFixtureHandle {
        val serverId = logicalToServerIds[logicalId]
            ?: error("No server entry is mapped to logical fixture '$logicalId'")
        val normalized = normalize(entry).copy(id = serverId)
        val updated = controlCallRetrier.run {
            remoteDataSource.updateTimeEntry(
                organizationId = session.organizationId,
                timeEntry = normalized,
                tags = normalized.tags.map { it.id },
            )
        }
        logicalToServerIds[logicalId] = updated.id
        return E2eFixtureHandle(logicalId, updated.id)
    }

    private suspend fun deleteAllEntries() {
        val entries = fetchAllEntries().toMutableList()
        controlCallRetrier.run { remoteDataSource.getActiveTimeEntry() }?.let { active ->
            if (entries.none { it.id == active.id }) entries += active
        }
        entries.distinctBy { it.id }.forEach { entry ->
            controlCallRetrier.run { remoteDataSource.deleteTimeEntry(session.organizationId, entry.id) }
        }
    }

    private suspend fun fetchAllEntries(): List<TimeEntry> {
        val entries = mutableListOf<TimeEntry>()
        var offset = 0
        do {
            val page = controlCallRetrier.run {
                remoteDataSource.getTimeEntries(
                    TimeEntriesQuery(
                        organizationId = session.organizationId,
                        memberId = session.membershipId,
                        limit = PAGE_SIZE,
                        offset = offset,
                        onlyFullDates = false,
                    ),
                )
            }
            entries += page.data
            offset += page.data.size
        } while (page.data.size == PAGE_SIZE)
        return entries
    }

    private fun normalize(entry: TimeEntry) = entry.copy(
        userId = session.userId,
        organizationId = session.organizationId,
    )

    private fun readSession(): E2eSession {
        val file = File(context.filesDir, SESSION_FILE)
        check(file.isFile) { "Missing real Solidtime session file '$SESSION_FILE'" }
        val properties = Properties().apply { file.inputStream().use(::load) }
        fun required(name: String): String = properties.getProperty(name)?.takeIf(String::isNotBlank)
            ?: error("Real Solidtime session is missing $name")
        return E2eSession(
            baseUrl = required("base_url"),
            accessToken = required("access_token"),
            refreshToken = LIVE_REFRESH_PLACEHOLDER,
            clientId = LIVE_CLIENT_ID,
            userId = required("user_id"),
            membershipId = required("membership_id"),
            organizationId = required("organization_id"),
            zone = ZoneId.of(required("timezone")),
        )
    }

    companion object {
        private const val SESSION_FILE = "solidtime-live-e2e.properties"
        private const val LIVE_CLIENT_ID = "solidverdant-live-e2e"
        private const val LIVE_REFRESH_PLACEHOLDER = "no-refresh-token-for-live-e2e"
        private const val PAGE_SIZE = 250
    }
}

/**
 * Retries only real-harness control-plane requests. App requests continue to exercise production
 * retry behavior unchanged. A server-provided Retry-After is never shortened: if it exceeds this
 * harness's total wait budget, the original 429 is surfaced instead of retrying early.
 */
internal class RealControlCallRetrier(
    private val elapsedRealtimeMs: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun <T> run(call: suspend () -> Result<T>): T {
        val startedAt = elapsedRealtimeMs()
        var attempts = 0
        while (true) {
            attempts += 1
            val result = call()
            if (result.isSuccess) return result.getOrThrow()

            val error = result.exceptionOrNull() ?: error("Failed real E2E control call without an exception")
            if (error !is HttpException || error.code() != HTTP_TOO_MANY_REQUESTS || attempts >= MAX_ATTEMPTS) throw error

            val retryDelayMs = retryAfterDelayMs(
                header = error.response()?.headers()?.get(RETRY_AFTER_HEADER),
                nowMs = wallClockMs(),
            )
            val elapsedMs = (elapsedRealtimeMs() - startedAt).coerceAtLeast(0L)
            if (retryDelayMs > MAX_TOTAL_WAIT_MS - elapsedMs) throw error
            sleep(retryDelayMs)
        }
    }

    companion object {
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val RETRY_AFTER_HEADER = "Retry-After"
        private const val MAX_ATTEMPTS = 3
        private const val MAX_TOTAL_WAIT_MS = 30_000L
        private const val DEFAULT_RETRY_DELAY_MS = 1_000L
        private const val MIN_RETRY_DELAY_MS = 500L
        private const val NANOS_PER_MILLISECOND = 1_000_000L

        internal fun retryAfterDelayMs(header: String?, nowMs: Long): Long {
            val secondsDelay = header?.trim()?.toLongOrNull()?.coerceAtLeast(0L)?.let { seconds ->
                if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L
            }
            val dateDelay = header?.let { value ->
                runCatching {
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant()
                        .toEpochMilli() - Instant.ofEpochMilli(nowMs).toEpochMilli()
                }.getOrNull()
            }
            return (secondsDelay ?: dateDelay ?: DEFAULT_RETRY_DELAY_MS).coerceAtLeast(MIN_RETRY_DELAY_MS)
        }
    }
}

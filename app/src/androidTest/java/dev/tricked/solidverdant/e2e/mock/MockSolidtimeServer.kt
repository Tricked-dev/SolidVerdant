/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.mock

import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.ClientsResponse
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.MembershipsResponse
import dev.tricked.solidverdant.data.model.Organization
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.ProjectsResponse
import dev.tricked.solidverdant.data.model.StartTimeEntryRequest
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.TagsResponse
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TasksResponse
import dev.tricked.solidverdant.data.model.TimeEntriesMeta
import dev.tricked.solidverdant.data.model.TimeEntriesResponse
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.TimeEntryResponse
import dev.tricked.solidverdant.data.model.TokenResponse
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.data.model.UserResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.time.Instant
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * A stateful fake of the Solidtime backend built on [MockWebServer].
 *
 * The app's Retrofit base URL is dynamic (built from the endpoint stored in AuthDataStore), so the
 * E2E harness points the app at [baseUrl] and this dispatcher answers the real OkHttp requests with
 * JSON that is valid against the `data.model` classes.
 *
 * State: the [timeEntries] list is mutable and shared between the POST (start/create) and GET (list)
 * routes, so a created entry is reflected in the history list — enough to drive create -> sync ->
 * history flows. Preset the catalogue with [preset] before [start].
 *
 * Thread-safety: [Dispatcher.dispatch] runs on MockWebServer's background threads, so all mutable
 * collections are synchronized and [dispatch] is guarded by [lock].
 */
class MockSolidtimeServer {

    private val server = MockWebServer()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val lock = Any()
    private val idCounter = AtomicInteger(1000)

    // ---- Seedable catalogue -------------------------------------------------------------------

    var user: User = defaultUser()
    val memberships: MutableList<Membership> = Collections.synchronizedList(mutableListOf())
    val projects: MutableList<Project> = Collections.synchronizedList(mutableListOf())
    val tasks: MutableList<Task> = Collections.synchronizedList(mutableListOf())
    val tags: MutableList<Tag> = Collections.synchronizedList(mutableListOf())
    val clients: MutableList<Client> = Collections.synchronizedList(mutableListOf())
    val timeEntries: MutableList<TimeEntry> = Collections.synchronizedList(mutableListOf())

    @Volatile
    var activeEntry: TimeEntry? = null

    /** Every request the app made, in order. Read from tests for assertions. */
    val recordedCalls: MutableList<RecordedCall> = Collections.synchronizedList(mutableListOf())

    // ---- Lifecycle ----------------------------------------------------------------------------

    fun start() {
        server.dispatcher = dispatcher
        server.start()
    }

    /** Base URL to seed into AuthDataStore, e.g. `http://127.0.0.1:<port>/`. */
    fun baseUrl(): String = server.url("/").toString()

    fun shutdown() {
        server.shutdown()
    }

    // ---- Presets ------------------------------------------------------------------------------

    /**
     * Convenience preset producing one user, one membership/organization, and (optionally) a single
     * completed time entry so the walking-skeleton test has something to render in the history.
     */
    fun presetLoggedInWorld(
        organizationId: String = DEFAULT_ORG_ID,
        membershipId: String = DEFAULT_MEMBERSHIP_ID,
        userId: String = DEFAULT_USER_ID,
        seededEntry: TimeEntry? = defaultCompletedEntry(organizationId, userId),
    ) {
        user = defaultUser(userId)
        memberships.clear()
        memberships += Membership(
            id = membershipId,
            role = "owner",
            organization = Organization(
                id = organizationId,
                name = "Acme Org",
                currency = "USD",
                preventOverlappingTimeEntries = false,
            ),
        )
        timeEntries.clear()
        seededEntry?.let { timeEntries += it }
    }

    fun addTimeEntry(entry: TimeEntry) {
        timeEntries += entry
    }

    /** Deterministic large dataset used by scrolling and composition stress tests. */
    fun presetStressWorld(entryCount: Int = 250, projectCount: Int = 120, taskCount: Int = 480, tagCount: Int = 160) {
        presetLoggedInWorld(seededEntry = null)
        clients.clear()
        clients += (0 until 40).map { Client("client-$it", "Client ${it.toString().padStart(3, '0')}") }
        projects.clear()
        projects += (0 until projectCount).map {
            Project(
                id = "project-$it",
                name = "Project ${it.toString().padStart(3, '0')}",
                color = "#4F46E5",
                clientId = "client-${it % 40}",
            )
        }
        tasks.clear()
        tasks += (0 until taskCount).map {
            Task(
                id = "task-$it",
                name = "Task ${it.toString().padStart(3, '0')}",
                projectId = "project-${it % projectCount}",
                createdAt = "2026-01-01T00:00:00Z",
                updatedAt = "2026-01-01T00:00:00Z",
            )
        }
        tags.clear()
        tags += (0 until tagCount).map { Tag("tag-$it", "Tag ${it.toString().padStart(3, '0')}") }
        val newest = Instant.parse("2026-07-07T16:00:00Z")
        timeEntries.clear()
        timeEntries += (0 until entryCount).map { index ->
            val start = newest.minusSeconds(index * 1_800L)
            TimeEntry(
                id = "stress-entry-$index",
                description = "Stress entry ${index.toString().padStart(3, '0')}",
                userId = DEFAULT_USER_ID,
                start = start.toString(),
                end = start.plusSeconds(1_500).toString(),
                duration = 1_500,
                projectId = "project-${index % projectCount}",
                taskId = "task-${index % taskCount}",
                tags = listOf(Tag("tag-${index % tagCount}")),
                billable = index % 2 == 0,
                organizationId = DEFAULT_ORG_ID,
            )
        }
    }

    // ---- Assertions ---------------------------------------------------------------------------

    fun callsMatching(method: String, pathContains: String): List<RecordedCall> =
        recordedCalls.filter { it.method == method && it.path.contains(pathContains) }

    fun wasRequested(method: String, pathContains: String): Boolean = callsMatching(method, pathContains).isNotEmpty()

    // ---- Dispatcher ---------------------------------------------------------------------------

    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = synchronized(lock) {
            val method = request.method ?: "GET"
            val fullPath = request.path ?: "/"
            val path = fullPath.substringBefore("?")
            val body = runCatching { request.body.readUtf8() }.getOrDefault("")
            recordedCalls += RecordedCall(method, fullPath, body)

            route(method, path, fullPath, body)
        }
    }

    private fun route(method: String, path: String, fullPath: String, body: String): MockResponse = when {
        method == "POST" && path == "/oauth/token" ->
            ok(json.encodeToString(TokenResponse(accessToken = "test-access-token", refreshToken = "test-refresh-token", expiresIn = 3600)))

        method == "GET" && path == "/api/v1/users/me" ->
            ok(json.encodeToString(UserResponse(data = user)))

        method == "GET" && path == "/api/v1/users/me/memberships" ->
            ok(json.encodeToString(MembershipsResponse(data = memberships.toList())))

        method == "GET" && path == "/api/v1/users/me/time-entries/active" ->
            ok(json.encodeToString(TimeEntryResponse(data = activeEntry)))

        method == "GET" && path.endsWith("/projects") ->
            ok(json.encodeToString(ProjectsResponse(data = projects.toList())))

        method == "GET" && path.endsWith("/tasks") ->
            ok(json.encodeToString(TasksResponse(data = tasks.toList())))

        method == "GET" && path.endsWith("/tags") ->
            ok(json.encodeToString(TagsResponse(data = tags.toList())))

        method == "GET" && path.endsWith("/clients") ->
            ok(json.encodeToString(ClientsResponse(data = clients.toList())))

        ENTRY_ITEM_REGEX.matches(path) -> {
            val id = path.substringAfterLast("/")
            val orgId = ENTRY_ITEM_REGEX.find(path)!!.groupValues[1]
            when (method) {
                "PUT" -> handleUpdateOrStop(orgId, id, body)
                "DELETE" -> {
                    timeEntries.removeAll { it.id == id }
                    MockResponse().setResponseCode(204)
                }
                else -> notFound()
            }
        }

        ENTRY_COLLECTION_REGEX.matches(path) -> {
            val orgId = ENTRY_COLLECTION_REGEX.find(path)!!.groupValues[1]
            when (method) {
                "POST" -> handleCreateOrStart(orgId, body)
                "GET" -> handleListEntries(fullPath)
                else -> notFound()
            }
        }

        else -> notFound()
    }

    /**
     * Honors the app's `limit`/`offset` query params like the real backend so pagination is
     * actually exercised: history paging (loadMoreTimeEntries) and date jumps binary-search over
     * offsets, which a return-everything fake silently short-circuits.
     */
    private fun handleListEntries(fullPath: String): MockResponse {
        val query = fullPath.substringAfter("?", missingDelimiterValue = "")
        val params = query.split("&").mapNotNull {
            val (k, v) = it.split("=", limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            k to v
        }.toMap()
        val limit = params["limit"]?.toIntOrNull() ?: 250
        val offset = params["offset"]?.toIntOrNull() ?: 0
        val sorted = timeEntries.sortedByDescending { it.start }
        val page = sorted.drop(offset).take(limit)
        return ok(
            json.encodeToString(
                TimeEntriesResponse(
                    data = page,
                    meta = TimeEntriesMeta(
                        total = sorted.size,
                        currentPage = (offset / limit.coerceAtLeast(1)) + 1,
                        lastPage = (sorted.size + limit - 1) / limit.coerceAtLeast(1),
                        perPage = limit,
                    ),
                ),
            ),
        )
    }

    private fun handleCreateOrStart(orgId: String, body: String): MockResponse {
        val req = runCatching { json.decodeFromString<StartTimeEntryRequest>(body) }.getOrNull()
        val entry = TimeEntry(
            id = "server-${idCounter.getAndIncrement()}",
            description = req?.description,
            userId = user.id,
            start = req?.start ?: "2026-07-07T09:00:00Z",
            end = req?.end,
            duration = computeDuration(req?.start, req?.end),
            projectId = req?.projectId,
            taskId = req?.taskId,
            tags = req?.tags.orEmpty().map { Tag(id = it) },
            billable = req?.billable ?: false,
            organizationId = orgId,
        )
        timeEntries += entry
        if (entry.end == null) activeEntry = entry
        return ok(json.encodeToString(TimeEntryResponse(data = entry)))
    }

    private fun handleUpdateOrStop(orgId: String, id: String, body: String): MockResponse {
        // Both stop and update arrive as PUT; decode leniently for the fields we echo back.
        val existing = timeEntries.firstOrNull { it.id == id }
        val patch = runCatching { json.decodeFromString<PutBody>(body) }.getOrNull()
        val updated = (existing ?: TimeEntry(id = id, userId = user.id, start = patch?.start ?: "", organizationId = orgId)).copy(
            start = patch?.start ?: existing?.start ?: "",
            end = patch?.end ?: existing?.end,
            description = patch?.description ?: existing?.description,
            projectId = patch?.project_id ?: existing?.projectId,
            taskId = patch?.task_id ?: existing?.taskId,
            billable = patch?.billable ?: existing?.billable ?: false,
            duration = computeDuration(patch?.start ?: existing?.start, patch?.end ?: existing?.end),
        )
        timeEntries.removeAll { it.id == id }
        timeEntries += updated
        if (updated.end != null && activeEntry?.id == id) activeEntry = null
        return ok(json.encodeToString(TimeEntryResponse(data = updated)))
    }

    private fun ok(bodyJson: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(bodyJson)

    private fun notFound(): MockResponse = MockResponse().setResponseCode(404).setBody("""{"message":"not found"}""")

    companion object {
        const val DEFAULT_USER_ID = "user-1"
        const val DEFAULT_MEMBERSHIP_ID = "member-1"
        const val DEFAULT_ORG_ID = "org-1"

        private val ENTRY_COLLECTION_REGEX =
            Regex("""/api/v1/organizations/([^/]+)/time-entries""")
        private val ENTRY_ITEM_REGEX =
            Regex("""/api/v1/organizations/([^/]+)/time-entries/([^/]+)""")

        fun defaultUser(id: String = DEFAULT_USER_ID): User =
            User(id = id, name = "Test User", email = "test@example.com", timezone = "UTC", weekStart = "monday")

        fun defaultCompletedEntry(
            organizationId: String = DEFAULT_ORG_ID,
            userId: String = DEFAULT_USER_ID,
            id: String = "seed-entry-1",
            description: String = "Seeded work",
        ): TimeEntry = TimeEntry(
            id = id,
            description = description,
            userId = userId,
            start = "2026-07-07T09:00:00Z",
            end = "2026-07-07T10:00:00Z",
            duration = 3600,
            organizationId = organizationId,
        )

        private fun computeDuration(start: String?, end: String?): Int? {
            if (start == null || end == null) return null
            return runCatching {
                val s = java.time.Instant.parse(start)
                val e = java.time.Instant.parse(end)
                (e.epochSecond - s.epochSecond).toInt().coerceAtLeast(0)
            }.getOrNull()
        }
    }
}

/** A request the app issued against the fake, captured for assertions. */
data class RecordedCall(val method: String, val path: String, val body: String)

/** Lenient view over the PUT payload (StopTimeEntryRequest / UpdateTimeEntryRequest share fields). */
@kotlinx.serialization.Serializable
private data class PutBody(
    val start: String? = null,
    val end: String? = null,
    val description: String? = null,
    val project_id: String? = null,
    val task_id: String? = null,
    val billable: Boolean? = null,
)

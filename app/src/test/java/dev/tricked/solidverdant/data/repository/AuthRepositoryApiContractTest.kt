/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.remote.ApiClientFactory
import dev.tricked.solidverdant.data.remote.SolidtimeApi
import dev.tricked.solidverdant.di.NetworkModule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
class AuthRepositoryApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: AuthRepository
    private val requests = Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authDataStore = AuthDataStore(context)
        runBlocking { authDataStore.saveOAuthConfig(server.url("/").toString(), "test-client") }
        repository = AuthRepository(
            authDataStore,
            ApiClientFactory(OkHttpClient(), NetworkModule.provideJson()),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `catalogue endpoints collect every Solidtime pagination page`() = runTest {
        server.dispatcher = catalogueDispatcher()

        assertEquals(listOf("project-1", "project-2"), repository.getProjects("org").getOrThrow().map { it.id })
        assertEquals(listOf("task-1", "task-2"), repository.getTasks("org").getOrThrow().map { it.id })
        assertEquals(listOf("tag-1", "tag-2"), repository.getTags("org").getOrThrow().map { it.id })
        assertEquals(listOf("client-1", "client-2"), repository.getClients("org").getOrThrow().map { it.id })

        listOf("projects", "tasks", "tags", "clients").forEach { endpoint ->
            val matching = requests.filter { it.substringBefore("?").endsWith("/$endpoint") }
            assertEquals(2, matching.size)
            assertEquals("1", matching[0].queryValue("page"))
            assertEquals("2", matching[1].queryValue("page"))
        }
    }

    @Test
    fun `official 404 active response means there is no running entry`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"message":"No query results for model [App\\Models\\TimeEntry]."}"""),
        )

        assertNull(repository.getActiveTimeEntry().getOrThrow())
    }

    @Test
    fun `official active response accepts tag id arrays and extra type field`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"data":{"id":"entry","start":"2026-08-08T08:00:00Z","end":null,"duration":0,"description":"Running","task_id":null,"project_id":null,"organization_id":"org","user_id":"user","tags":["tag-id"],"billable":false,"type":"work"}}""",
                ),
        )

        val active = repository.getActiveTimeEntry().getOrThrow()

        assertEquals("entry", active?.id)
        assertEquals(listOf("tag-id"), active?.tags?.map { it.id })
    }

    @Test
    fun `cancellation from the active endpoint is not converted into a failed result`() {
        val authDataStore = mockk<AuthDataStore>(relaxed = true)
        val api = mockk<SolidtimeApi>()
        val apiClientFactory = mockk<ApiClientFactory>()
        coEvery { authDataStore.getEndpoint() } returns "https://example.test"
        every { apiClientFactory.createApi(any()) } returns api
        coEvery { api.getActiveTimeEntry() } throws CancellationException("screen closed")

        val repository = AuthRepository(authDataStore, apiClientFactory)

        assertThrows(CancellationException::class.java) {
            runBlocking { repository.getActiveTimeEntry() }
        }
    }

    @Test
    fun `multi-day update converts editor offsets to Solidtime UTC wire format`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return if (
                    body.contains("\"start\":\"2026-08-07T08:00:00Z\"") &&
                    body.contains("\"end\":\"2026-08-08T08:00:00Z\"")
                ) {
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"data":{"id":"entry","start":"2026-08-07T08:00:00Z","end":"2026-08-08T08:00:00Z","duration":86400,"description":null,"task_id":null,"project_id":null,"organization_id":"org","user_id":"user","tags":[],"billable":false,"type":"work"}}""",
                        )
                } else {
                    MockResponse()
                        .setResponseCode(422)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"message":"The given data was invalid.","errors":{"start":["The start field must match the format Y-m-dTH:i:sZ."],"end":["The end field must match the format Y-m-dTH:i:sZ."]}}""",
                        )
                }
            }
        }

        val result = repository.updateTimeEntry(
            "org",
            TimeEntry(
                id = "entry",
                userId = "user",
                start = "2026-08-07T10:00:00+02:00",
                end = "2026-08-08T10:00:00+02:00",
                organizationId = "org",
            ),
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `start create and stop convert captured offsets to Solidtime UTC wire format`() = runTest {
        val writeBodies = Collections.synchronizedList(mutableListOf<String>())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                writeBodies += body
                return MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"data":{"id":"entry","start":"2026-08-07T08:00:00Z","end":"2026-08-08T08:00:00Z","duration":86400,"description":null,"task_id":null,"project_id":null,"organization_id":"org","user_id":"user","tags":[],"billable":false,"type":"work"}}""",
                    )
            }
        }

        assertTrue(repository.startTimeEntry("org", "member", "user", startIso = "2026-08-07T10:00:00+02:00").isSuccess)
        assertTrue(
            repository.createTimeEntry(
                organizationId = "org",
                memberId = "member",
                userId = "user",
                start = "2026-08-07T10:00:00+02:00",
                end = "2026-08-08T10:00:00+02:00",
            ).isSuccess,
        )
        assertTrue(
            repository.stopTimeEntry(
                organizationId = "org",
                timeEntryId = "entry",
                userId = "user",
                startTime = "2026-08-07T10:00:00+02:00",
                endIso = "2026-08-08T10:00:00+02:00",
            ).isSuccess,
        )

        assertTrue(writeBodies[0].contains("\"start\":\"2026-08-07T08:00:00Z\""))
        assertTrue(writeBodies[1].contains("\"start\":\"2026-08-07T08:00:00Z\""))
        assertTrue(writeBodies[1].contains("\"end\":\"2026-08-08T08:00:00Z\""))
        assertEquals("{\"end\":\"2026-08-08T08:00:00Z\"}", writeBodies[2])
    }

    @Test
    fun `completed entry is created atomically with billable and tags in the official post`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return if (
                    request.method == "POST" &&
                    body.contains("\"billable\":true") &&
                    body.contains("\"tags\":[\"tag-id\"]")
                ) {
                    MockResponse()
                        .setResponseCode(201)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"data":{"id":"entry","start":"2026-08-07T08:00:00Z","end":"2026-08-07T09:00:00Z","duration":3600,"description":"Atomic","task_id":null,"project_id":null,"organization_id":"org","user_id":"user","tags":["tag-id"],"billable":true,"type":"work"}}""",
                        )
                } else {
                    MockResponse()
                        .setResponseCode(422)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"message":"The given data was invalid."}""")
                }
            }
        }

        val created = repository.createTimeEntry(
            organizationId = "org",
            memberId = "member",
            userId = "user",
            start = "2026-08-07T08:00:00Z",
            end = "2026-08-07T09:00:00Z",
            description = "Atomic",
            tags = listOf("tag-id"),
            billable = true,
        ).getOrThrow()

        assertTrue(created.billable)
        assertEquals(listOf("tag-id"), created.tags.map { it.id })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `official time entry list accepts total-only metadata and unknown links`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"data":[{"id":"entry","start":"2026-08-07T08:00:00Z","end":null,"duration":0,"description":null,"task_id":null,"project_id":null,"organization_id":"org","user_id":"user","tags":["tag-id"],"billable":false,"type":"work"}],"links":{"next":null},"meta":{"total":1}}""",
                ),
        )

        val response = repository.getTimeEntries("org", "member").getOrThrow()

        assertEquals(1, response.meta?.total)
        assertNull(response.meta?.currentPage)
        assertEquals(listOf("tag-id"), response.data.single().tags.map { it.id })
    }

    @Test
    fun `official empty 204 delete succeeds while missing entry 404 remains observable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"message":"No query results for model [App\\Models\\TimeEntry]."}"""),
        )

        assertTrue(repository.deleteTimeEntry("org", "existing").isSuccess)
        val missing = repository.deleteTimeEntry("org", "missing")
        assertTrue(missing.isFailure)
        assertEquals(404, (missing.exceptionOrNull() as HttpException).code())
    }

    @Test
    fun `official validation and rate limit statuses retain their HTTP details`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"message":"The given data was invalid.","errors":{"end":["The end field must match the format Y-m-dTH:i:sZ."]}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setHeader("Retry-After", "17")
                .setBody("""{"message":"Too Many Attempts."}"""),
        )

        val invalid = repository.updateTimeEntry(
            "org",
            TimeEntry(
                id = "entry",
                userId = "user",
                start = "2026-08-07T08:00:00Z",
                end = "2026-08-07T09:00:00Z",
                organizationId = "org",
            ),
        )
        val invalidHttp = invalid.exceptionOrNull() as HttpException
        assertEquals(422, invalidHttp.code())

        val limited = repository.stopTimeEntry(
            organizationId = "org",
            timeEntryId = "entry",
            userId = "user",
            startTime = "2026-08-07T08:00:00Z",
            endIso = "2026-08-07T09:00:00Z",
        )
        val limitedHttp = limited.exceptionOrNull() as HttpException
        assertEquals(429, limitedHttp.code())
        assertEquals("17", limitedHttp.response()?.headers()?.get("Retry-After"))
    }

    @Test
    fun `unpaginated custom server catalogue remains compatible`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"data":[${tag(1)}]}"""),
        )

        assertEquals(listOf("tag-1"), repository.getTags("org").getOrThrow().map { it.id })
        assertEquals("1", requireNotNull(server.takeRequest().path).queryValue("page"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `later catalogue page failure does not return a truncated success`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val page = request.requestUrl?.queryParameter("page")?.toIntOrNull() ?: 1
                return if (page == 1) {
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(paginated(project(1), 1))
                } else {
                    MockResponse()
                        .setResponseCode(503)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"message":"Service unavailable"}""")
                }
            }
        }

        assertEquals(true, repository.getProjects("org").isFailure)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `repeated catalogue page metadata fails instead of looping or duplicating`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val requestedPage = request.requestUrl?.queryParameter("page")?.toIntOrNull() ?: 1
                return MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(paginated(project(requestedPage), page = 1))
            }
        }

        assertEquals(true, repository.getProjects("org").isFailure)
        assertEquals(2, server.requestCount)
    }

    private fun catalogueDispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = requireNotNull(request.path)
            requests += path
            val page = request.requestUrl?.queryParameter("page")?.toIntOrNull() ?: 1
            val item = when {
                path.substringBefore("?").endsWith("/projects") -> project(page)
                path.substringBefore("?").endsWith("/tasks") -> task(page)
                path.substringBefore("?").endsWith("/tags") -> tag(page)
                path.substringBefore("?").endsWith("/clients") -> client(page)
                else -> return MockResponse().setResponseCode(404)
            }
            return MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(paginated(item, page))
        }
    }

    private fun project(page: Int) =
        """{"id":"project-$page","name":"Project $page","color":"#123456","client_id":null,"is_archived":false,"billable_rate":null,"is_billable":false,"estimated_time":null,"spent_time":0,"is_public":true}"""

    private fun task(page: Int) =
        """{"id":"task-$page","name":"Task $page","is_done":false,"project_id":"project-$page","estimated_time":null,"spent_time":0,"created_at":"2026-08-08T08:00:00Z","updated_at":"2026-08-08T08:00:00Z"}"""

    private fun tag(page: Int) =
        """{"id":"tag-$page","name":"Tag $page","created_at":"2026-08-08T08:00:00Z","updated_at":"2026-08-08T08:00:00Z"}"""

    private fun client(page: Int) =
        """{"id":"client-$page","name":"Client $page","is_archived":false,"created_at":"2026-08-08T08:00:00Z","updated_at":"2026-08-08T08:00:00Z"}"""

    private fun paginated(item: String, page: Int): String =
        """{"data":[$item],"links":{"first":"https://example.test?page=1","last":"https://example.test?page=2","prev":null,"next":null},"meta":{"current_page":$page,"from":$page,"last_page":2,"links":[],"path":"https://example.test","per_page":15,"to":$page,"total":2}}"""

    private fun String.queryValue(name: String): String? = substringAfter("?", "")
        .split("&")
        .mapNotNull { parameter -> parameter.split("=", limit = 2).takeIf { it.size == 2 } }
        .firstOrNull { it[0] == name }
        ?.get(1)
}

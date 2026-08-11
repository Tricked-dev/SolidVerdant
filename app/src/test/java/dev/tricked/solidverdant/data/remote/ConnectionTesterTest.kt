/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConnectionTesterTest {
    private val tester = ConnectionTester(OkHttpClient())

    @Test fun `rejects malformed endpoints without network access`() = runTest {
        assertFalse(tester.test("not a url", "client").success)
        assertFalse(tester.test("ftp://example.test", "client").success)
    }

    @Test fun `requires tls for remote servers and client id`() = runTest {
        assertFalse(tester.test("http://example.test", "client").success)
        assertFalse(tester.test("https://example.test", "").success)
    }

    @Test
    fun `an authenticated or unauthorized API response proves the endpoint is reachable`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(401))

            val result = tester.test(server.url("/").toString(), "client")

            assertEquals(ConnectionTestCode.READY, result.code)
            assertEquals(401, result.httpStatus)
            assertEquals("/api/v1/users/me", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }
}

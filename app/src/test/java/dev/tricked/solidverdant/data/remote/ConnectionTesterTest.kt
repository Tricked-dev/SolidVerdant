package dev.tricked.solidverdant.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
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
}

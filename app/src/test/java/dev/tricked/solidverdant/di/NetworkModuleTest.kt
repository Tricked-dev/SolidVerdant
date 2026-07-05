package dev.tricked.solidverdant.di

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {

    @Test
    fun `release interceptor disables HTTP logging`() {
        val messages = mutableListOf<String>()
        val interceptor = createLoggingInterceptor(isDebug = false, messages::add)
        val request = Request.Builder()
            .url("https://example.test/oauth/token")
            .header("Authorization", "Bearer secret-access-token")
            .build()

        interceptor.intercept(fakeChain(request))

        assertEquals(okhttp3.logging.HttpLoggingInterceptor.Level.NONE, interceptor.level)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `debug interceptor redacts sensitive headers`() {
        val messages = mutableListOf<String>()
        val interceptor = createLoggingInterceptor(isDebug = true, messages::add)
        val request = Request.Builder()
            .url("https://example.test/api")
            .header("Authorization", "Bearer secret-access-token")
            .header("Cookie", "session=secret-session")
            .build()

        interceptor.intercept(fakeChain(request))

        val output = messages.joinToString("\n")
        assertEquals(okhttp3.logging.HttpLoggingInterceptor.Level.BODY, interceptor.level)
        assertTrue(output.contains("Authorization: ██"))
        assertTrue(output.contains("Cookie: ██"))
        assertFalse(output.contains("secret-access-token"))
        assertFalse(output.contains("secret-session"))
    }

    private fun fakeChain(request: Request): okhttp3.Interceptor.Chain =
        object : okhttp3.Interceptor.Chain {
            override fun request(): Request = request

            override fun proceed(request: Request): Response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()

            override fun connection() = null

            override fun call() = throw UnsupportedOperationException()

            override fun connectTimeoutMillis() = 30_000

            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

            override fun readTimeoutMillis() = 30_000

            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

            override fun writeTimeoutMillis() = 30_000

            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }
}

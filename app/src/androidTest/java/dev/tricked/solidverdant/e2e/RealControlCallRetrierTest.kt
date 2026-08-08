/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e

import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class RealControlCallRetrierTest {
    @Test
    fun retries429AfterServerRequestedDelay() = runBlocking {
        var elapsedMs = 0L
        val delays = mutableListOf<Long>()
        val retrier = RealControlCallRetrier(
            elapsedRealtimeMs = { elapsedMs },
            wallClockMs = { 0L },
            sleep = { delayMs ->
                delays += delayMs
                elapsedMs += delayMs
            },
        )
        var calls = 0

        val value = retrier.run {
            calls += 1
            if (calls == 1) Result.failure(httpException(429, retryAfter = "2")) else Result.success("recovered")
        }

        assertEquals("recovered", value)
        assertEquals(2, calls)
        assertEquals(listOf(2_000L), delays)
    }

    @Test
    fun stopsAfterBoundedNumberOf429Attempts() = runBlocking {
        var elapsedMs = 0L
        val retrier = RealControlCallRetrier(
            elapsedRealtimeMs = { elapsedMs },
            wallClockMs = { 0L },
            sleep = { delayMs -> elapsedMs += delayMs },
        )
        val expected = httpException(429)
        var calls = 0

        try {
            retrier.run<String> {
                calls += 1
                Result.failure(expected)
            }
            fail("Expected the final HTTP 429")
        } catch (actual: HttpException) {
            assertSame(expected, actual)
        }
        assertEquals(3, calls)
    }

    @Test
    fun parsesHttpDateRetryAfterWithoutShorteningIt() {
        val now = Instant.parse("2026-08-08T12:00:00Z")
        val retryAt = DateTimeFormatter.RFC_1123_DATE_TIME.format(now.plusSeconds(3).atZone(ZoneOffset.UTC))

        assertEquals(3_000L, RealControlCallRetrier.retryAfterDelayMs(retryAt, now.toEpochMilli()))
    }

    private fun httpException(code: Int, retryAfter: String? = null): HttpException {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://solidtime.invalid/control").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test response")
            .apply { retryAfter?.let { header("Retry-After", it) } }
            .build()
        return HttpException(Response.error<Unit>("".toResponseBody(), raw))
    }
}

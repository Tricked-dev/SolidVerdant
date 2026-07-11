/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.remote

import dev.tricked.solidverdant.data.local.UserCacheCleaner
import dev.tricked.solidverdant.data.model.TokenResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TokenAuthenticatorTest {
    @Test
    fun `concurrent 401s share one refresh and both retry with new token`() {
        repeat(20) {
            val storage = FakeTokenStorage("old-access", "old-refresh")
            val enteredRefresh = CountDownLatch(1)
            val releaseRefresh = CountDownLatch(1)
            val refreshCalls = AtomicInteger()
            val authenticator = TokenAuthenticator(storage) { _, _, _ ->
                refreshCalls.incrementAndGet()
                enteredRefresh.countDown()
                check(releaseRefresh.await(5, TimeUnit.SECONDS))
                RefreshResult.Success(TokenResponse("new-access", "new-refresh"))
            }
            val pool = Executors.newFixedThreadPool(2)
            try {
                val first = pool.submit<Request?> { authenticator.authenticate(null, unauthorized("old-access")) }
                check(enteredRefresh.await(5, TimeUnit.SECONDS))
                val second = pool.submit<Request?> { authenticator.authenticate(null, unauthorized("old-access")) }
                releaseRefresh.countDown()

                assertEquals("Bearer new-access", first.get(5, TimeUnit.SECONDS)?.header("Authorization"))
                assertEquals("Bearer new-access", second.get(5, TimeUnit.SECONDS)?.header("Authorization"))
                assertEquals(1, refreshCalls.get())
                assertEquals("new-access", storage.access)
                assertEquals("new-refresh", storage.refresh)
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun `transient refresh failure leaves credentials intact`() {
        val storage = FakeTokenStorage("access", "refresh")
        val authenticator = TokenAuthenticator(storage) { _, _, _ -> RefreshResult.Transient }

        assertNull(authenticator.authenticate(null, unauthorized("access")))
        assertEquals("access", storage.access)
        assertEquals("refresh", storage.refresh)
    }

    @Test
    fun `definitive refresh rejection clears the dead credentials`() {
        val storage = FakeTokenStorage("access", "refresh")
        val authenticator = TokenAuthenticator(storage) { _, _, _ -> RefreshResult.Invalid }

        assertNull(authenticator.authenticate(null, unauthorized("access")))
        assertNull(storage.access)
        assertNull(storage.refresh)
    }

    @Test
    fun `missing refresh token clears the dead access token`() {
        val storage = FakeTokenStorage("access", null)
        val refreshCalls = AtomicInteger()
        val authenticator = TokenAuthenticator(storage) { _, _, _ ->
            refreshCalls.incrementAndGet()
            RefreshResult.Invalid
        }

        assertNull(authenticator.authenticate(null, unauthorized("access")))
        assertNull(storage.access)
        assertNull(storage.refresh)
        assertEquals("refresh must not be attempted without a refresh token", 0, refreshCalls.get())
    }

    @Test
    fun `missing refresh token clears the account cache`() {
        val storage = FakeTokenStorage("access", null)
        val userCacheCleaner = mockk<UserCacheCleaner>()
        coEvery { userCacheCleaner.clear() } returns Unit
        val authenticator = TokenAuthenticator(storage, userCacheCleaner) { _, _, _ ->
            error("refresh must not be attempted without a refresh token")
        }

        assertNull(authenticator.authenticate(null, unauthorized("access")))

        coVerify(exactly = 1) { userCacheCleaner.clear() }
    }

    @Test
    fun `definitive refresh rejection clears the account cache`() {
        val storage = FakeTokenStorage("access", "refresh")
        val userCacheCleaner = mockk<UserCacheCleaner>()
        coEvery { userCacheCleaner.clear() } returns Unit
        val authenticator = TokenAuthenticator(storage, userCacheCleaner) { _, _, _ -> RefreshResult.Invalid }

        assertNull(authenticator.authenticate(null, unauthorized("access")))

        coVerify(exactly = 1) { userCacheCleaner.clear() }
    }

    @Test
    fun `successful refresh does not clear the account cache`() {
        val storage = FakeTokenStorage("access", "refresh")
        val userCacheCleaner = mockk<UserCacheCleaner>()
        coEvery { userCacheCleaner.clear() } returns Unit
        val authenticator = TokenAuthenticator(storage, userCacheCleaner) { _, _, _ ->
            RefreshResult.Success(TokenResponse("new-access", "new-refresh"))
        }

        val retried = authenticator.authenticate(null, unauthorized("access"))

        assertEquals("Bearer new-access", retried?.header("Authorization"))
        coVerify(exactly = 0) { userCacheCleaner.clear() }
    }

    @Test
    fun `401 for obsolete token reuses current token without refreshing`() {
        val storage = FakeTokenStorage("new-access", "new-refresh")
        val refreshCalls = AtomicInteger()
        val authenticator = TokenAuthenticator(storage) { _, _, _ ->
            refreshCalls.incrementAndGet()
            RefreshResult.Invalid
        }

        val retried = authenticator.authenticate(null, unauthorized("old-access"))

        assertEquals("Bearer new-access", retried?.header("Authorization"))
        assertEquals(0, refreshCalls.get())
    }

    private fun unauthorized(token: String): Response {
        val request = Request.Builder()
            .url("https://example.test/api")
            .header("Authorization", "Bearer $token")
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()
    }

    private class FakeTokenStorage(@Volatile var access: String?, @Volatile var refresh: String?) : TokenStorage {
        override suspend fun accessToken() = access
        override suspend fun refreshToken() = refresh
        override suspend fun endpoint() = "https://example.test"
        override suspend fun clientId() = "client"
        override suspend fun saveTokens(accessToken: String, refreshToken: String) {
            access = accessToken
            refresh = refreshToken
        }
        override suspend fun clearTokens() {
            access = null
            refresh = null
        }
    }
}

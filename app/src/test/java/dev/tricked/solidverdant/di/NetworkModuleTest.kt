/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.di

import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import javax.net.SocketFactory
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

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
    fun `debug interceptor logs headers without work data bodies`() {
        val messages = mutableListOf<String>()
        val interceptor = createLoggingInterceptor(isDebug = true, messages::add)
        val request = Request.Builder()
            .url("https://example.test/api")
            .header("Authorization", "Bearer secret-access-token")
            .header("Cookie", "session=secret-session")
            .post("{\"description\":\"private work item\"}".toRequestBody())
            .build()

        interceptor.intercept(fakeChain(request, "{\"description\":\"private server work item\"}"))

        val output = messages.joinToString("\n")
        assertEquals(okhttp3.logging.HttpLoggingInterceptor.Level.HEADERS, interceptor.level)
        assertTrue(output.contains("Authorization: ██"))
        assertTrue(output.contains("Cookie: ██"))
        assertFalse(output.contains("secret-access-token"))
        assertFalse(output.contains("secret-session"))
        assertFalse(output.contains("private work item"))
        assertFalse(output.contains("private server work item"))
    }

    private fun fakeChain(request: Request, responseBody: String = "{}"): Interceptor.Chain = object : Interceptor.Chain {
        override fun request(): Request = request

        override fun proceed(request: Request): Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody.toResponseBody())
            .build()

        override fun connection() = null

        override fun call() = throw UnsupportedOperationException()

        override fun connectTimeoutMillis() = 30_000

        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

        override fun readTimeoutMillis() = 30_000

        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

        override fun writeTimeoutMillis() = 30_000

        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

        override fun withDns(dns: Dns) = this

        override fun withSocketFactory(socketFactory: SocketFactory) = this

        override fun withRetryOnConnectionFailure(retryOnConnectionFailure: Boolean) = this

        override fun withAuthenticator(authenticator: Authenticator) = this

        override fun withCookieJar(cookieJar: CookieJar) = this

        override fun withCache(cache: Cache?) = this

        override fun withProxy(proxy: Proxy?) = this

        override fun withProxySelector(proxySelector: ProxySelector) = this

        override fun withProxyAuthenticator(proxyAuthenticator: Authenticator) = this

        override fun withSslSocketFactory(sslSocketFactory: SSLSocketFactory?, x509TrustManager: X509TrustManager?) = this

        override fun withHostnameVerifier(hostnameVerifier: HostnameVerifier) = this

        override fun withCertificatePinner(certificatePinner: CertificatePinner) = this

        override fun withConnectionPool(connectionPool: ConnectionPool) = this

        override val followSslRedirects: Boolean = true

        override val followRedirects: Boolean = true

        override val dns: Dns = Dns.SYSTEM

        override val socketFactory: SocketFactory = SocketFactory.getDefault()

        override val retryOnConnectionFailure: Boolean = true

        override val authenticator: Authenticator = Authenticator.NONE

        override val cookieJar: CookieJar = CookieJar.NO_COOKIES

        override val cache: Cache? = null

        override val proxy: Proxy? = null

        override val proxySelector: ProxySelector = ProxySelector.getDefault()
            ?: object : ProxySelector() {
                override fun select(uri: java.net.URI): List<Proxy> = listOf(Proxy.NO_PROXY)

                override fun connectFailed(uri: java.net.URI, sa: SocketAddress, ioe: java.io.IOException) = Unit
            }

        override val proxyAuthenticator: Authenticator = Authenticator.NONE

        override val sslSocketFactoryOrNull: SSLSocketFactory? = null

        override val x509TrustManagerOrNull: X509TrustManager? = null

        override val hostnameVerifier: HostnameVerifier = HostnameVerifier { _, _ -> true }

        override val certificatePinner: CertificatePinner = CertificatePinner.DEFAULT

        override val connectionPool: ConnectionPool = ConnectionPool()

        override val eventListener: EventListener = EventListener.NONE
    }
}

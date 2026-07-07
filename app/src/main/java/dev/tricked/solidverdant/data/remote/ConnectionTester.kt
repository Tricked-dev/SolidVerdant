/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

enum class ConnectionTestCode {
    READY,
    MISSING_CLIENT_ID,
    INVALID_URL,
    COMPLETE_URL_REQUIRED,
    HTTPS_REQUIRED,
    API_NOT_FOUND,
    SERVER_ERROR,
    UNEXPECTED_RESPONSE,
    TLS_FAILED,
    DNS_FAILED,
    CONNECTION_FAILED,
    TEST_FAILED,
}

data class ConnectionTestResult(val code: ConnectionTestCode, val httpStatus: Int? = null) {
    val success: Boolean get() = code == ConnectionTestCode.READY
}

@Singleton
class ConnectionTester @Inject constructor(private val client: OkHttpClient) {
    suspend fun test(endpoint: String, clientId: String): ConnectionTestResult = withContext(Dispatchers.IO) {
        if (clientId.isBlank()) return@withContext ConnectionTestResult(ConnectionTestCode.MISSING_CLIENT_ID)
        val uri = runCatching { URI(endpoint) }.getOrNull()
            ?: return@withContext ConnectionTestResult(ConnectionTestCode.INVALID_URL)
        if (uri.host.isNullOrBlank() ||
            uri.scheme !in setOf("http", "https") ||
            uri.userInfo != null ||
            uri.query != null ||
            uri.fragment != null
        ) {
            return@withContext ConnectionTestResult(ConnectionTestCode.COMPLETE_URL_REQUIRED)
        }
        if (uri.scheme != "https" && uri.host !in setOf("localhost", "127.0.0.1", "10.0.2.2")) {
            return@withContext ConnectionTestResult(ConnectionTestCode.HTTPS_REQUIRED)
        }
        val url = endpoint.trimEnd('/') + "/api/v1/users/me"
        try {
            client.newBuilder().followRedirects(false).callTimeout(15, TimeUnit.SECONDS).build().newCall(
                Request.Builder().url(url).header("Accept", "application/json").get().build(),
            ).execute().use { response ->
                when (response.code) {
                    200, 401, 403 -> ConnectionTestResult(ConnectionTestCode.READY, response.code)
                    404 -> ConnectionTestResult(ConnectionTestCode.API_NOT_FOUND, response.code)
                    in 500..599 -> ConnectionTestResult(ConnectionTestCode.SERVER_ERROR, response.code)
                    else -> ConnectionTestResult(ConnectionTestCode.UNEXPECTED_RESPONSE, response.code)
                }
            }
        } catch (e: SSLException) {
            ConnectionTestResult(ConnectionTestCode.TLS_FAILED)
        } catch (e: java.net.UnknownHostException) {
            ConnectionTestResult(ConnectionTestCode.DNS_FAILED)
        } catch (e: java.net.ConnectException) {
            ConnectionTestResult(ConnectionTestCode.CONNECTION_FAILED)
        } catch (e: Exception) {
            ConnectionTestResult(ConnectionTestCode.TEST_FAILED)
        }
    }
}

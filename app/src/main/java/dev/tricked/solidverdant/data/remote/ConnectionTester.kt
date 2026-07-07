package dev.tricked.solidverdant.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

data class ConnectionTestResult(val success: Boolean, val message: String)

@Singleton
class ConnectionTester @Inject constructor(private val client: OkHttpClient) {
    suspend fun test(endpoint: String, clientId: String): ConnectionTestResult = withContext(Dispatchers.IO) {
        if (clientId.isBlank()) return@withContext ConnectionTestResult(false, "OAuth client ID is missing")
        val uri = runCatching { URI(endpoint) }.getOrNull()
            ?: return@withContext ConnectionTestResult(false, "The server URL is invalid")
        if (uri.host.isNullOrBlank() || uri.scheme !in setOf("http", "https"))
            return@withContext ConnectionTestResult(false, "Use a complete http:// or https:// server URL")
        if (uri.scheme != "https" && uri.host !in setOf("localhost", "127.0.0.1", "10.0.2.2"))
            return@withContext ConnectionTestResult(false, "HTTPS is required for non-local servers")
        val url = endpoint.trimEnd('/') + "/api/v1/users/me"
        try {
            client.newBuilder().followRedirects(false).build().newCall(
                Request.Builder().url(url).header("Accept", "application/json").get().build()
            ).execute().use { response ->
                when (response.code) {
                    200, 401, 403 -> ConnectionTestResult(true, "Solidtime API reached; OAuth configuration is ready")
                    404 -> ConnectionTestResult(false, "Server reached, but the Solidtime API was not found")
                    in 500..599 -> ConnectionTestResult(false, "Server reached, but it returned an internal error (${response.code})")
                    else -> ConnectionTestResult(false, "Unexpected server response (${response.code})")
                }
            }
        } catch (e: SSLException) {
            ConnectionTestResult(false, "TLS certificate validation failed")
        } catch (e: java.net.UnknownHostException) {
            ConnectionTestResult(false, "Server name could not be resolved")
        } catch (e: java.net.ConnectException) {
            ConnectionTestResult(false, "Could not connect to the server")
        } catch (e: Exception) {
            ConnectionTestResult(false, e.message ?: "Connection test failed")
        }
    }
}

package dev.tricked.solidverdant.data.remote

import dev.tricked.solidverdant.data.local.AuthDataStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Authenticator that automatically refreshes the access token when a 401 response is received
 *
 * Note: Uses runBlocking as a bridge between suspend functions and synchronous
 * OkHttp authenticator. This is a known pattern in Android networking.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val authDataStore: AuthDataStore,
    private val json: Json
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite loops - if we already tried refreshing twice, give up
        if (responseCount(response) >= 2) {
            Timber.w("Token refresh failed after 2 attempts, clearing tokens")
            runBlocking {
                authDataStore.clearTokens()
            }
            return null
        }

        // Get refresh token and client config
        val refreshToken = runBlocking {
            authDataStore.getRefreshToken()
        }

        val clientId = runBlocking {
            authDataStore.getClientId()
        }

        val endpoint = runBlocking {
            authDataStore.getEndpoint()
        }

        // If no refresh token, can't refresh
        if (refreshToken.isNullOrEmpty()) {
            Timber.w("No refresh token available, clearing tokens")
            runBlocking {
                authDataStore.clearTokens()
            }
            return null
        }

        // Attempt to refresh the token
        return try {
            val newTokens = refreshAccessToken(endpoint, clientId, refreshToken)

            if (newTokens != null) {
                // Save new tokens
                runBlocking {
                    authDataStore.saveTokens(newTokens.accessToken, newTokens.refreshToken)
                }

                // Retry the original request with new token
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.accessToken}")
                    .build()
            } else {
                // Token refresh failed, clear tokens
                runBlocking {
                    authDataStore.clearTokens()
                }
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Token refresh exception")
            runBlocking {
                authDataStore.clearTokens()
            }
            null
        }
    }

    /**
     * Performs the token refresh HTTP request
     */
    private fun refreshAccessToken(
        endpoint: String,
        clientId: String,
        refreshToken: String
    ): dev.tricked.solidverdant.data.model.TokenResponse? {
        return try {
            val cleanEndpoint = endpoint.removeSuffix("/")
            val tokenUrl = "$cleanEndpoint/oauth/token"

            val requestBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url(tokenUrl)
                .post(requestBody)
                .build()

            // Create a separate OkHttpClient without authenticator to avoid recursion
            val client = OkHttpClient.Builder().build()
            val tokenResponse = client.newCall(request).execute()

            if (tokenResponse.isSuccessful) {
                val body = tokenResponse.body?.string()
                if (body != null) {
                    json.decodeFromString<dev.tricked.solidverdant.data.model.TokenResponse>(body)
                } else {
                    Timber.w("Token refresh response body is null")
                    null
                }
            } else {
                Timber.w("Token refresh failed with code: ${tokenResponse.code}")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Token refresh request failed")
            null
        }
    }

    /**
     * Counts how many times we've tried to authenticate this request
     */
    private fun responseCount(response: Response): Int {
        var result = 1
        var priorResponse = response.priorResponse
        while (priorResponse != null) {
            result++
            priorResponse = priorResponse.priorResponse
        }
        return result
    }
}

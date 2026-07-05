package dev.tricked.solidverdant.data.remote

import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.model.TokenResponse
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

internal interface TokenStorage {
    suspend fun accessToken(): String?
    suspend fun refreshToken(): String?
    suspend fun endpoint(): String
    suspend fun clientId(): String
    suspend fun saveTokens(accessToken: String, refreshToken: String)
}

private class DataStoreTokenStorage(private val store: AuthDataStore) : TokenStorage {
    override suspend fun accessToken() = store.getAccessToken()
    override suspend fun refreshToken() = store.getRefreshToken()
    override suspend fun endpoint() = store.getEndpoint()
    override suspend fun clientId() = store.getClientId()
    override suspend fun saveTokens(accessToken: String, refreshToken: String) =
        store.saveTokens(accessToken, refreshToken)
}

internal fun interface TokenRefresher {
    fun refresh(endpoint: String, clientId: String, refreshToken: String): TokenResponse?
}

private class HttpTokenRefresher(
    private val json: Json,
    private val client: OkHttpClient = OkHttpClient.Builder().build()
) : TokenRefresher {
    override fun refresh(endpoint: String, clientId: String, refreshToken: String): TokenResponse? {
        val request = Request.Builder()
            .url("${endpoint.removeSuffix("/")}/oauth/token")
            .post(
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("client_id", clientId)
                    .add("refresh_token", refreshToken)
                    .build()
            )
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.w("Token refresh failed with code: ${response.code}")
                return@use null
            }
            response.body?.string()?.let { json.decodeFromString<TokenResponse>(it) }
        }
    }
}

/** Refreshes an expired access token, allowing at most one refresh at a time. */
@Singleton
class TokenAuthenticator internal constructor(
    private val storage: TokenStorage,
    private val refresher: TokenRefresher
) : Authenticator {
    @Inject
    constructor(authDataStore: AuthDataStore, json: Json) : this(
        DataStoreTokenStorage(authDataStore),
        HttpTokenRefresher(json)
    )

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")

        return synchronized(refreshLock) {
            runBlocking {
                // Another request may have completed the refresh while this request waited.
                val currentToken = storage.accessToken()
                if (!currentToken.isNullOrEmpty() && currentToken != failedToken) {
                    return@runBlocking retry(response, currentToken)
                }

                val refreshToken = storage.refreshToken()
                if (refreshToken.isNullOrEmpty()) return@runBlocking null

                try {
                    val tokens = refresher.refresh(
                        storage.endpoint(),
                        storage.clientId(),
                        refreshToken
                    ) ?: return@runBlocking null

                    storage.saveTokens(tokens.accessToken, tokens.refreshToken)
                    retry(response, tokens.accessToken)
                } catch (e: Exception) {
                    // A network/server failure is not evidence that stored credentials are invalid.
                    Timber.e(e, "Token refresh failed")
                    null
                }
            }
        }
    }

    private fun retry(response: Response, token: String): Request = response.request.newBuilder()
        .header("Authorization", "Bearer $token")
        .build()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

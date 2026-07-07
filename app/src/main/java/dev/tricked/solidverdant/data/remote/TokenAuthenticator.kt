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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

internal interface TokenStorage {
    suspend fun accessToken(): String?
    suspend fun refreshToken(): String?
    suspend fun endpoint(): String
    suspend fun clientId(): String
    suspend fun saveTokens(accessToken: String, refreshToken: String)
    suspend fun clearTokens()
}

private class DataStoreTokenStorage(private val store: AuthDataStore) : TokenStorage {
    override suspend fun accessToken() = store.getAccessToken()
    override suspend fun refreshToken() = store.getRefreshToken()
    override suspend fun endpoint() = store.getEndpoint()
    override suspend fun clientId() = store.getClientId()
    override suspend fun saveTokens(accessToken: String, refreshToken: String) =
        store.saveTokens(accessToken, refreshToken)
    override suspend fun clearTokens() = store.clearTokens()
}

/** Outcome of a refresh attempt, distinguishing dead credentials from a recoverable failure. */
internal sealed interface RefreshResult {
    data class Success(val tokens: TokenResponse) : RefreshResult

    /** The server definitively rejected the refresh token (401 / invalid_grant). Credentials are dead. */
    object Invalid : RefreshResult

    /** A transient failure (network error, timeout, 5xx). Stored credentials may still be valid. */
    object Transient : RefreshResult
}

internal fun interface TokenRefresher {
    fun refresh(endpoint: String, clientId: String, refreshToken: String): RefreshResult
}

private class HttpTokenRefresher(
    private val json: Json,
    private val client: OkHttpClient = OkHttpClient.Builder().build()
) : TokenRefresher {
    override fun refresh(endpoint: String, clientId: String, refreshToken: String): RefreshResult {
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

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                when {
                    response.isSuccessful ->
                        body?.let { json.decodeFromString<TokenResponse>(it) }
                            ?.let(RefreshResult::Success)
                            ?: RefreshResult.Transient
                    // 401, or 400 invalid_grant/invalid_client, means the refresh token is no longer
                    // usable: force logout. Other codes (e.g. 429, 5xx) are transient.
                    response.code == 401 || isDefinitiveRejection(response.code, body) -> {
                        Timber.w("Token refresh rejected with code: ${response.code}")
                        RefreshResult.Invalid
                    }
                    else -> {
                        Timber.w("Token refresh failed with code: ${response.code}")
                        RefreshResult.Transient
                    }
                }
            }
        } catch (e: IOException) {
            // Network/timeout failure: not evidence the credentials are invalid.
            Timber.w("Token refresh failed due to a network error")
            RefreshResult.Transient
        }
    }

    private fun isDefinitiveRejection(code: Int, body: String?): Boolean =
        code == 400 && body != null &&
            (body.contains("invalid_grant") || body.contains("invalid_client"))
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
                if (refreshToken.isNullOrEmpty()) {
                    // Nothing can recover this session: clear the dead access token so the app
                    // returns to login instead of silently 401ing every request.
                    storage.clearTokens()
                    return@runBlocking null
                }

                try {
                    when (val result = refresher.refresh(storage.endpoint(), storage.clientId(), refreshToken)) {
                        is RefreshResult.Success -> {
                            storage.saveTokens(result.tokens.accessToken, result.tokens.refreshToken)
                            retry(response, result.tokens.accessToken)
                        }
                        RefreshResult.Invalid -> {
                            // The server rejected the refresh token; the credentials are dead.
                            storage.clearTokens()
                            null
                        }
                        // Transient failure: preserve credentials so a later request can retry.
                        RefreshResult.Transient -> null
                    }
                } catch (e: Exception) {
                    // An unexpected failure (e.g. malformed refresh payload) is not evidence that
                    // stored credentials are invalid; keep them and let a later request retry.
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

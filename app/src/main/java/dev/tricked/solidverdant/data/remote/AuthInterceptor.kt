package dev.tricked.solidverdant.data.remote

import dev.tricked.solidverdant.data.local.AuthDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor that adds Bearer token authorization to API requests
 *
 * Note: Uses runBlocking as a bridge between suspend functions and synchronous
 * OkHttp interceptor. This is a known pattern in Android networking.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authDataStore: AuthDataStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip adding auth header for OAuth token requests
        if (originalRequest.url.encodedPath.contains("/oauth/token")) {
            return chain.proceed(originalRequest)
        }

        // Get access token from DataStore
        val accessToken = runBlocking {
            authDataStore.getAccessToken()
        }

        // Add Authorization header if token exists
        val newRequest = if (!accessToken.isNullOrEmpty()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(newRequest)
    }
}

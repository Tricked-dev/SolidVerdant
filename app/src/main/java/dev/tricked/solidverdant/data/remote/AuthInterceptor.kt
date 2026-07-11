/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.remote

import dev.tricked.solidverdant.data.local.AuthDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor that adds Bearer token authorization to API requests
 *
 * Note: Uses runBlocking as a bridge between suspend functions and synchronous
 * OkHttp interceptor. This is a known pattern in Android networking.
 */
@Singleton
class AuthInterceptor @Inject constructor(private val authDataStore: AuthDataStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip adding auth header for OAuth token requests
        if (originalRequest.url.encodedPath.contains("/oauth/token")) {
            return chain.proceed(originalRequest)
        }

        // Get access token from DataStore. The read must never throw here: an undecryptable secret
        // (Keystore key lost/invalidated) would otherwise crash every API call. Treat any failure as
        // "no token" and proceed unauthenticated; the flows clear the corrupt secret separately.
        val accessToken = runBlocking {
            try {
                authDataStore.getAccessToken()
            } catch (e: Exception) {
                Timber.w("Failed to read access token; proceeding without authorization")
                null
            }
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

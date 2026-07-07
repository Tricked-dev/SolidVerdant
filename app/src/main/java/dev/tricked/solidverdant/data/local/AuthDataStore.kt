/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// Keep the delegate at file scope so every AuthDataStore instance (including instances from
// successive Hilt test components in the same instrumentation process) shares one DataStore.
// Declaring this inside AuthDataStore creates a new delegate for each component and DataStore
// rejects the second active instance for the same auth_prefs file.
private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

/**
 * DataStore for authentication-related data
 * Provides encrypted storage for tokens, OAuth config, and PKCE data
 */
@Singleton
class AuthDataStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object PreferencesKeys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val ENDPOINT = stringPreferencesKey("instance_endpoint")
        val CLIENT_ID = stringPreferencesKey("instance_client_id")
        val CODE_VERIFIER = stringPreferencesKey("code_verifier")
        val STATE = stringPreferencesKey("oauth_state")
        val CURRENT_MEMBERSHIP_ID = stringPreferencesKey("current_membership_id")
    }

    private val secretCipher = AuthSecretCipher()
    private val migrationMutex = Mutex()

    @Volatile private var migrationComplete = false

    private val secretKeys = listOf(
        PreferencesKeys.ACCESS_TOKEN,
        PreferencesKeys.REFRESH_TOKEN,
        PreferencesKeys.CODE_VERIFIER,
        PreferencesKeys.STATE,
    )

    private fun secretFlow(key: Preferences.Key<String>): Flow<String?> = context.authDataStore.data
        .onStart { migratePlaintextSecrets() }
        // decryptOrNull never throws: a secret that can no longer be decrypted (Keystore key lost or
        // invalidated) is treated as absent so the flow emits "logged out" instead of crashing every
        // collector (isLoggedIn/accessToken and the interceptor's runBlocking read path).
        .map { preferences -> preferences[key]?.let(secretCipher::decryptOrNull) }

    private suspend fun migratePlaintextSecrets() {
        if (migrationComplete) return
        migrationMutex.withLock {
            if (migrationComplete) return@withLock
            context.authDataStore.edit { preferences ->
                secretKeys.forEach { key ->
                    val storedValue = preferences[key] ?: return@forEach
                    when (val sanitized = sanitizeSecret(storedValue)) {
                        // Undecryptable secret (e.g. after data restore to a new device): discard it so
                        // the app starts cleanly at the login screen instead of crash-looping.
                        null -> preferences.remove(key)
                        else -> preferences[key] = sanitized
                    }
                }
            }
            migrationComplete = true
        }
    }

    /**
     * Returns the encrypted envelope to persist, or null when the stored secret cannot be recovered
     * and must be discarded. Legacy plaintext is encrypted in place; an already-encrypted value is
     * verified to still be decryptable with the current Keystore key.
     */
    private fun sanitizeSecret(storedValue: String): String? = if (secretCipher.isEncrypted(storedValue)) {
        if (secretCipher.decryptOrNull(storedValue) != null) {
            storedValue
        } else {
            Timber.w("Discarding an undecryptable authentication secret; treating session as logged out")
            null
        }
    } else {
        try {
            secretCipher.encrypt(storedValue)
        } catch (e: Exception) {
            Timber.w("Discarding an authentication secret that could not be encrypted")
            null
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://app.solidtime.io"
        const val DEFAULT_CLIENT_ID = "9c994748-c593-4a6d-951b-6849c829bc4e"
    }

    // Token flows
    val accessToken: Flow<String?> = secretFlow(PreferencesKeys.ACCESS_TOKEN)

    val refreshToken: Flow<String?> = secretFlow(PreferencesKeys.REFRESH_TOKEN)

    // NOTE: this intentionally derives from the decrypt-validating accessToken flow. The splash
    // screen holds the first frame until this emits, which is part of the "first frame shows the
    // full cached UI, no layout shifts" product behavior — a faster presence-only check was tried
    // (2026-07-07) and regressed app load behavior; don't reintroduce it.
    val isLoggedIn: Flow<Boolean> = accessToken.map { !it.isNullOrEmpty() }

    // OAuth config flows
    val endpoint: Flow<String> = context.authDataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ENDPOINT] ?: DEFAULT_ENDPOINT
        }

    val clientId: Flow<String> = context.authDataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.CLIENT_ID] ?: DEFAULT_CLIENT_ID
        }

    // PKCE flows
    val codeVerifier: Flow<String?> = secretFlow(PreferencesKeys.CODE_VERIFIER)

    val state: Flow<String?> = secretFlow(PreferencesKeys.STATE)

    // Membership flow
    val currentMembershipId: Flow<String?> = context.authDataStore.data
        .map { preferences -> preferences[PreferencesKeys.CURRENT_MEMBERSHIP_ID] }

    /**
     * Save access and refresh tokens
     */
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.authDataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN] = secretCipher.encrypt(accessToken)
            preferences[PreferencesKeys.REFRESH_TOKEN] = secretCipher.encrypt(refreshToken)
        }
        cacheAccessToken(accessToken)
    }

    /**
     * Save only the access token (used during token refresh)
     */
    suspend fun saveAccessToken(accessToken: String) {
        context.authDataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN] = secretCipher.encrypt(accessToken)
        }
        cacheAccessToken(accessToken)
    }

    /**
     * Clear all authentication tokens
     */
    suspend fun clearTokens() {
        context.authDataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.ACCESS_TOKEN)
            preferences.remove(PreferencesKeys.REFRESH_TOKEN)
        }
        cacheAccessToken(null)
    }

    /**
     * Save OAuth configuration (endpoint and client ID)
     */
    suspend fun saveOAuthConfig(endpoint: String, clientId: String) {
        context.authDataStore.edit { preferences ->
            preferences[PreferencesKeys.ENDPOINT] = endpoint.removeSuffix("/")
            preferences[PreferencesKeys.CLIENT_ID] = clientId
        }
    }

    /**
     * Save PKCE data for OAuth flow
     */
    suspend fun savePKCEData(codeVerifier: String, state: String) {
        context.authDataStore.edit { preferences ->
            preferences[PreferencesKeys.CODE_VERIFIER] = secretCipher.encrypt(codeVerifier)
            preferences[PreferencesKeys.STATE] = secretCipher.encrypt(state)
        }
    }

    /**
     * Clear PKCE data after successful OAuth flow
     */
    suspend fun clearPKCEData() {
        context.authDataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.CODE_VERIFIER)
            preferences.remove(PreferencesKeys.STATE)
        }
    }

    /**
     * Save current membership ID
     */
    suspend fun saveCurrentMembershipId(membershipId: String) {
        context.authDataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENT_MEMBERSHIP_ID] = membershipId
        }
    }

    /**
     * Clear all stored data except OAuth configuration (logout)
     * Preserves endpoint and client ID settings
     */
    suspend fun clearAll() {
        context.authDataStore.edit { preferences ->
            // Save OAuth config before clearing
            val savedEndpoint = preferences[PreferencesKeys.ENDPOINT]
            val savedClientId = preferences[PreferencesKeys.CLIENT_ID]

            // Clear everything
            preferences.clear()

            // Restore OAuth config
            savedEndpoint?.let { preferences[PreferencesKeys.ENDPOINT] = it }
            savedClientId?.let { preferences[PreferencesKeys.CLIENT_ID] = it }
        }
        cacheAccessToken(null)
    }

    /**
     * Reset OAuth configuration to defaults
     */
    suspend fun resetOAuthConfig() {
        context.authDataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.ENDPOINT)
            preferences.remove(PreferencesKeys.CLIENT_ID)
        }
    }

    /**
     * Get code verifier synchronously (for use in interceptors)
     */
    suspend fun getCodeVerifier(): String? = codeVerifier.first()

    /**
     * Get state synchronously (for use in OAuth callback verification)
     */
    suspend fun getState(): String? = state.first()

    /**
     * Get access token synchronously (for use in interceptors).
     *
     * Served from an in-memory cache after the first read: the auth interceptor calls this on
     * every API request, and without the cache each request pays a DataStore disk read plus a
     * Keystore decrypt IPC. Writes ([saveTokens]/[saveAccessToken]/[clearTokens]/[clearAll])
     * update or invalidate the cache.
     */
    suspend fun getAccessToken(): String? {
        cachedAccessToken?.let { return it.token }
        return accessToken.first().also { cacheAccessToken(it) }
    }

    /** Wrapper so a cached "no token" (null) is distinguishable from "nothing cached yet". */
    private class CachedToken(val token: String?)

    @Volatile
    private var cachedAccessToken: CachedToken? = null

    private fun cacheAccessToken(token: String?) {
        cachedAccessToken = CachedToken(token)
    }

    /**
     * Get refresh token synchronously (for use in token refresh)
     */
    suspend fun getRefreshToken(): String? = refreshToken.first()

    /**
     * Get endpoint synchronously
     */
    suspend fun getEndpoint(): String = endpoint.first()

    /**
     * Get client ID synchronously
     */
    suspend fun getClientId(): String = clientId.first()
}

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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore for authentication-related data
 * Provides encrypted storage for tokens, OAuth config, and PKCE data
 */
@Singleton
class AuthDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

    private object PreferencesKeys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val ENDPOINT = stringPreferencesKey("instance_endpoint")
        val CLIENT_ID = stringPreferencesKey("instance_client_id")
        val CODE_VERIFIER = stringPreferencesKey("code_verifier")
        val STATE = stringPreferencesKey("oauth_state")
        val CURRENT_MEMBERSHIP_ID = stringPreferencesKey("current_membership_id")
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://app.solidtime.io"
        const val DEFAULT_CLIENT_ID = "9c994748-c593-4a6d-951b-6849c829bc4e"
    }

    // Token flows
    val accessToken: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.ACCESS_TOKEN] }

    val refreshToken: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.REFRESH_TOKEN] }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            !preferences[PreferencesKeys.ACCESS_TOKEN].isNullOrEmpty()
        }

    // OAuth config flows
    val endpoint: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ENDPOINT] ?: DEFAULT_ENDPOINT
        }

    val clientId: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.CLIENT_ID] ?: DEFAULT_CLIENT_ID
        }

    // PKCE flows
    val codeVerifier: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.CODE_VERIFIER] }

    val state: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.STATE] }

    // Membership flow
    val currentMembershipId: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.CURRENT_MEMBERSHIP_ID] }

    /**
     * Save access and refresh tokens
     */
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN] = accessToken
            preferences[PreferencesKeys.REFRESH_TOKEN] = refreshToken
        }
    }

    /**
     * Save only the access token (used during token refresh)
     */
    suspend fun saveAccessToken(accessToken: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN] = accessToken
        }
    }

    /**
     * Clear all authentication tokens
     */
    suspend fun clearTokens() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.ACCESS_TOKEN)
            preferences.remove(PreferencesKeys.REFRESH_TOKEN)
        }
    }

    /**
     * Save OAuth configuration (endpoint and client ID)
     */
    suspend fun saveOAuthConfig(endpoint: String, clientId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENDPOINT] = endpoint.removeSuffix("/")
            preferences[PreferencesKeys.CLIENT_ID] = clientId
        }
    }

    /**
     * Save PKCE data for OAuth flow
     */
    suspend fun savePKCEData(codeVerifier: String, state: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CODE_VERIFIER] = codeVerifier
            preferences[PreferencesKeys.STATE] = state
        }
    }

    /**
     * Clear PKCE data after successful OAuth flow
     */
    suspend fun clearPKCEData() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.CODE_VERIFIER)
            preferences.remove(PreferencesKeys.STATE)
        }
    }

    /**
     * Save current membership ID
     */
    suspend fun saveCurrentMembershipId(membershipId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENT_MEMBERSHIP_ID] = membershipId
        }
    }

    /**
     * Clear all stored data except OAuth configuration (logout)
     * Preserves endpoint and client ID settings
     */
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            // Save OAuth config before clearing
            val savedEndpoint = preferences[PreferencesKeys.ENDPOINT]
            val savedClientId = preferences[PreferencesKeys.CLIENT_ID]

            // Clear everything
            preferences.clear()

            // Restore OAuth config
            savedEndpoint?.let { preferences[PreferencesKeys.ENDPOINT] = it }
            savedClientId?.let { preferences[PreferencesKeys.CLIENT_ID] = it }
        }
    }

    /**
     * Reset OAuth configuration to defaults
     */
    suspend fun resetOAuthConfig() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.ENDPOINT)
            preferences.remove(PreferencesKeys.CLIENT_ID)
        }
    }

    /**
     * Get code verifier synchronously (for use in interceptors)
     */
    suspend fun getCodeVerifier(): String? {
        return codeVerifier.first()
    }

    /**
     * Get state synchronously (for use in OAuth callback verification)
     */
    suspend fun getState(): String? {
        return state.first()
    }

    /**
     * Get access token synchronously (for use in interceptors)
     */
    suspend fun getAccessToken(): String? {
        return accessToken.first()
    }

    /**
     * Get refresh token synchronously (for use in token refresh)
     */
    suspend fun getRefreshToken(): String? {
        return refreshToken.first()
    }

    /**
     * Get endpoint synchronously
     */
    suspend fun getEndpoint(): String {
        return endpoint.first()
    }

    /**
     * Get client ID synchronously
     */
    suspend fun getClientId(): String {
        return clientId.first()
    }
}

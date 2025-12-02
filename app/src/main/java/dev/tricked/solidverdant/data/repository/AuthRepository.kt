package dev.tricked.solidverdant.data.repository

import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.data.remote.ApiClientFactory
import dev.tricked.solidverdant.util.PKCEUtil
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for authentication and API operations
 * Handles OAuth2 flow, token management, and API calls
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authDataStore: AuthDataStore,
    private val apiClientFactory: ApiClientFactory
) {
    companion object {
        private const val REDIRECT_URI = "solidtime://oauth/callback"
    }

    val isLoggedIn: Flow<Boolean> = authDataStore.isLoggedIn
    val endpoint: Flow<String> = authDataStore.endpoint
    val clientId: Flow<String> = authDataStore.clientId

    /**
     * Initialize the OAuth2 authorization flow
     * @return Result containing the authorization URL to open in browser
     */
    suspend fun initializeOAuthFlow(): Result<String> {
        return try {
            // Generate PKCE data
            val pkceData = PKCEUtil.generatePKCEData()

            // Store PKCE data for later verification
            authDataStore.savePKCEData(
                codeVerifier = pkceData.codeVerifier,
                state = pkceData.state
            )

            // Get current config
            val currentEndpoint = authDataStore.getEndpoint()
            val currentClientId = authDataStore.getClientId()

            // Build authorization URL
            val authUrl = PKCEUtil.buildAuthorizationUrl(
                endpoint = currentEndpoint,
                clientId = currentClientId,
                codeChallenge = pkceData.codeChallenge,
                state = pkceData.state
            )

            Timber.d("OAuth flow initialized, auth URL: $authUrl")
            Result.success(authUrl)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize OAuth flow")
            Result.failure(e)
        }
    }

    /**
     * Handle OAuth callback and exchange authorization code for tokens
     * @param code The authorization code from the callback
     * @param state The state parameter from the callback
     * @return Result indicating success or failure
     */
    suspend fun handleOAuthCallback(code: String, state: String): Result<Unit> {
        return try {
            // Verify state parameter (CSRF protection)
            val storedState = authDataStore.getState()
            if (state != storedState || state.isEmpty()) {
                Timber.w("Invalid state parameter in OAuth callback")
                return Result.failure(Exception("Invalid state parameter"))
            }

            // Get stored PKCE data
            val codeVerifier = authDataStore.getCodeVerifier()
            if (codeVerifier.isNullOrEmpty()) {
                Timber.w("No code verifier found")
                return Result.failure(Exception("No code verifier found"))
            }

            // Get current config
            val currentEndpoint = authDataStore.getEndpoint()
            val currentClientId = authDataStore.getClientId()

            // Create API instance
            val api = apiClientFactory.createApi(currentEndpoint)

            // Exchange code for tokens
            val tokenResponse = api.exchangeCodeForToken(
                clientId = currentClientId,
                redirectUri = REDIRECT_URI,
                codeVerifier = codeVerifier,
                code = code
            )

            // Save tokens
            authDataStore.saveTokens(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken
            )

            // Clear PKCE data
            authDataStore.clearPKCEData()

            Timber.d("OAuth callback handled successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to handle OAuth callback")
            authDataStore.clearPKCEData()
            Result.failure(e)
        }
    }

    /**
     * Get the current authenticated user
     */
    suspend fun getCurrentUser(): Result<User> {
        return try {
            val endpoint = authDataStore.getEndpoint()
            val api = apiClientFactory.createApi(endpoint)
            val response = api.getCurrentUser()
            Result.success(response.data)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user")
            Result.failure(e)
        }
    }

    /**
     * Get all memberships (organizations) for the current user
     */
    suspend fun getMyMemberships(): Result<List<Membership>> {
        return try {
            val endpoint = authDataStore.getEndpoint()
            val api = apiClientFactory.createApi(endpoint)
            val response = api.getMyMemberships()
            Result.success(response.data)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get memberships")
            Result.failure(e)
        }
    }

    /**
     * Get the active time entry for the current user
     */
    suspend fun getActiveTimeEntry(): Result<TimeEntry?> {
        return try {
            val endpoint = authDataStore.getEndpoint()
            val api = apiClientFactory.createApi(endpoint)
            val response = api.getActiveTimeEntry()
            Result.success(response.data)
        } catch (e: Exception) {
            // 404 with "No active time entry" is expected when not tracking
            if (e is retrofit2.HttpException && e.code() == 404) {
                Timber.d("No active time entry")
                Result.success(null)
            } else {
                Timber.e(e, "Failed to get active time entry")
                Result.failure(e)
            }
        }
    }

    /**
     * Save OAuth configuration (endpoint and client ID)
     */
    suspend fun saveOAuthConfig(endpoint: String, clientId: String): Result<Unit> {
        return try {
            authDataStore.saveOAuthConfig(endpoint, clientId)
            Timber.d("OAuth config saved")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save OAuth config")
            Result.failure(e)
        }
    }

    /**
     * Save current membership ID
     */
    suspend fun saveCurrentMembershipId(membershipId: String): Result<Unit> {
        return try {
            authDataStore.saveCurrentMembershipId(membershipId)
            Timber.d("Current membership ID saved")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save current membership ID")
            Result.failure(e)
        }
    }

    /**
     * Start a new time entry
     */
    suspend fun startTimeEntry(
        organizationId: String,
        memberId: String,
        userId: String,
        projectId: String? = null,
        taskId: String? = null,
        description: String = ""
    ): Result<TimeEntry> {
        return try {
            val endpoint = authDataStore.getEndpoint()
            val api = apiClientFactory.createApi(endpoint)

            // Use current time in format: Y-m-d\TH:i:s\Z (e.g., 2025-12-01T21:32:10Z)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            val now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                .format(formatter)

            val request = dev.tricked.solidverdant.data.model.StartTimeEntryRequest(
                memberId = memberId,
                start = now,
                description = description,
                projectId = projectId,
                taskId = taskId,
                billable = false
            )

            val response = api.startTimeEntry(organizationId, request)
            Timber.d("Time entry started: ${response.data?.id}")
            Result.success(response.data!!)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start time entry")
            Result.failure(e)
        }
    }

    /**
     * Stop the active time entry
     */
    suspend fun stopTimeEntry(
        organizationId: String,
        timeEntryId: String,
        userId: String,
        startTime: String
    ): Result<TimeEntry> {
        return try {
            val endpoint = authDataStore.getEndpoint()
            val api = apiClientFactory.createApi(endpoint)

            // Use current time in format: Y-m-d\TH:i:s\Z (e.g., 2025-12-01T21:32:10Z)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            val now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                .format(formatter)

            val request = dev.tricked.solidverdant.data.model.StopTimeEntryRequest(
                userId = userId,
                start = startTime,
                end = now
            )

            val response = api.stopTimeEntry(organizationId, timeEntryId, request)
            Timber.d("Time entry stopped: ${response.data?.id}")
            Result.success(response.data!!)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop time entry")
            Result.failure(e)
        }
    }

    /**
     * Logout and clear all stored data (preserves OAuth config)
     */
    suspend fun logout() {
        authDataStore.clearAll()
        Timber.d("User logged out")
    }

    /**
     * Reset OAuth configuration to defaults
     */
    suspend fun resetOAuthConfig(): Result<Unit> {
        return try {
            authDataStore.resetOAuthConfig()
            Timber.d("OAuth config reset to defaults")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to reset OAuth config")
            Result.failure(e)
        }
    }
}

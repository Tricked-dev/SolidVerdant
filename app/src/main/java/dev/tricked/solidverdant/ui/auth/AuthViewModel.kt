/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.auth

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.memory.MemoryCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.UserCacheCleaner
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.data.remote.ConnectionTestCode
import dev.tricked.solidverdant.data.remote.ConnectionTester
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.data.repository.TemplateRepository
import dev.tricked.solidverdant.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
/**
 * UI state for authentication screens
 */
@Stable
data class AuthUiState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val memberships: List<Membership> = emptyList(),
    val currentMembership: Membership? = null,
    val error: String? = null,
    val authUrl: String? = null,
    val hasRevalidated: Boolean = false,
)

/**
 * UI state for OAuth configuration
 */
@Stable
data class OAuthConfigState(
    val endpoint: String = "",
    val clientId: String = "",
    val isTesting: Boolean = false,
    val testSuccess: Boolean? = null,
    val testMessage: String? = null,
)

enum class AuthState { Unknown, LoggedIn, LoggedOut }

/**
 * ViewModel for authentication operations
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userCacheCleaner: UserCacheCleaner,
    private val settingsDataStore: SettingsDataStore,
    private val templateRepository: TemplateRepository,
    private val connectionTester: ConnectionTester,
    private val syncScheduler: SyncScheduler,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        settingsDataStore.getCachedAuth()?.let { cached ->
            AuthUiState(
                user = cached.user,
                memberships = cached.memberships,
                currentMembership = cached.memberships.firstOrNull { it.id == cached.currentMembershipId }
                    ?: cached.memberships.firstOrNull(),
            )
        } ?: AuthUiState(),
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _configState = MutableStateFlow(OAuthConfigState())
    val configState: StateFlow<OAuthConfigState> = _configState.asStateFlow()

    val authState: StateFlow<AuthState> = authRepository.isLoggedIn.map {
        if (it) AuthState.LoggedIn else AuthState.LoggedOut
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AuthState.Unknown,
    )
    val isLoggedIn: StateFlow<Boolean> = authState.map { it == AuthState.LoggedIn }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false,
    )
    private val _snapshotHydrated = MutableStateFlow(false)
    val snapshotHydrated: StateFlow<Boolean> = _snapshotHydrated.asStateFlow()

    init {
        // Load OAuth config on init
        loadOAuthConfig()
        // Room is the read source-of-truth now; auth/memberships are (re)loaded from the
        // network via loadUserData() once logged in. Nothing to hydrate synchronously.
        _snapshotHydrated.value = true
    }

    /**
     * Load OAuth configuration from storage
     */
    private fun loadOAuthConfig() {
        viewModelScope.launch {
            authRepository.endpoint.collect { endpoint ->
                _configState.value = _configState.value.copy(endpoint = endpoint)
            }
        }
        viewModelScope.launch {
            authRepository.clientId.collect { clientId ->
                _configState.value = _configState.value.copy(clientId = clientId)
            }
        }
    }

    /**
     * Start the OAuth2 authorization flow
     */
    fun startOAuthFlow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            authRepository.initializeOAuthFlow()
                .onSuccess { authUrl ->
                    Timber.d("OAuth flow started")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        authUrl = authUrl,
                    )
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to start OAuth flow")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to start OAuth flow",
                    )
                }
        }
    }

    /**
     * Handle OAuth callback with authorization code
     */
    fun handleOAuthCallback(code: String?, state: String?) {
        if (code.isNullOrEmpty() || state.isNullOrEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = "Invalid OAuth callback parameters",
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            authRepository.handleOAuthCallback(code, state)
                .onSuccess {
                    Timber.d("OAuth callback handled successfully")
                    // Load user data after successful authentication
                    loadUserData()
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to handle OAuth callback")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to complete OAuth flow",
                    )
                }
        }
    }

    /**
     * Load user data and memberships after login
     */
    fun loadUserData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            coroutineScope {
                val userResult = async { authRepository.getCurrentUser() }
                val membershipsResult = async { authRepository.getMyMemberships() }
                val user = userResult.await().getOrElse { error ->
                    Timber.e(error, "Failed to load user")
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                    return@coroutineScope
                }
                membershipsResult.await().onSuccess { memberships ->
                    val savedMembershipId = authRepository.getCurrentMembershipId()
                    val currentMembership = memberships.firstOrNull { it.id == savedMembershipId }
                        ?: memberships.firstOrNull()
                    _uiState.value = _uiState.value.copy(
                        user = user,
                        memberships = memberships,
                        currentMembership = currentMembership,
                        isLoading = false,
                        hasRevalidated = true,
                    )
                    settingsDataStore.cacheAuth(user, memberships, currentMembership?.id)

                    // Claim legacy (pre-ownership) templates for this account now that the auth
                    // cache is written. Only affects NULL-owner rows and is a no-op for accounts
                    // that already own their templates (org-switch never reaches this path).
                    templateRepository.claimUnowned(authRepository.endpoint.first(), user.id)

                    // Save current membership
                    currentMembership?.let {
                        authRepository.saveCurrentMembershipId(it.id)
                    }
                }
                    .onFailure { error ->
                        Timber.e(error, "Failed to load memberships")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load memberships",
                        )
                    }
            }
        }
    }

    fun selectMembership(membership: Membership) {
        if (membership.id == _uiState.value.currentMembership?.id) return

        _uiState.value = _uiState.value.copy(currentMembership = membership)
        _uiState.value.user?.let { user ->
            settingsDataStore.cacheAuth(user, _uiState.value.memberships, membership.id)
        }
        viewModelScope.launch {
            authRepository.saveCurrentMembershipId(membership.id)
                .onFailure { Timber.e(it, "Failed to save selected membership") }
        }
    }

    /**
     * Save OAuth configuration
     */
    fun saveOAuthConfig(endpoint: String, clientId: String) {
        viewModelScope.launch {
            authRepository.saveOAuthConfig(endpoint, clientId)
                .onSuccess {
                    _configState.value = _configState.value.copy(
                        endpoint = endpoint,
                        clientId = clientId,
                    )
                    Timber.d("OAuth config saved")
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to save OAuth config")
                }
        }
    }

    fun testConnection(endpoint: String, clientId: String) {
        viewModelScope.launch {
            _configState.value = _configState.value.copy(isTesting = true, testSuccess = null, testMessage = null)
            val result = connectionTester.test(endpoint.removeSuffix("/"), clientId)
            _configState.value = _configState.value.copy(
                isTesting = false,
                testSuccess = result.success,
                testMessage = when (result.code) {
                    ConnectionTestCode.READY -> context.getString(R.string.connection_ready)
                    ConnectionTestCode.MISSING_CLIENT_ID -> context.getString(R.string.connection_missing_client_id)
                    ConnectionTestCode.INVALID_URL -> context.getString(R.string.connection_invalid_url)
                    ConnectionTestCode.COMPLETE_URL_REQUIRED -> context.getString(R.string.connection_complete_url_required)
                    ConnectionTestCode.HTTPS_REQUIRED -> context.getString(R.string.connection_https_required)
                    ConnectionTestCode.API_NOT_FOUND -> context.getString(R.string.connection_api_not_found)
                    ConnectionTestCode.SERVER_ERROR -> context.getString(R.string.connection_server_error, result.httpStatus)
                    ConnectionTestCode.UNEXPECTED_RESPONSE -> context.getString(R.string.connection_unexpected_response, result.httpStatus)
                    ConnectionTestCode.TLS_FAILED -> context.getString(R.string.connection_tls_failed)
                    ConnectionTestCode.DNS_FAILED -> context.getString(R.string.connection_dns_failed)
                    ConnectionTestCode.CONNECTION_FAILED -> context.getString(R.string.connection_failed)
                    ConnectionTestCode.TEST_FAILED -> context.getString(R.string.connection_test_failed)
                },
            )
        }
    }

    /**
     * Reset OAuth configuration to defaults
     */
    fun resetOAuthConfig() {
        viewModelScope.launch {
            authRepository.resetOAuthConfig()
                .onSuccess {
                    // Update UI state with default values
                    _configState.value = _configState.value.copy(
                        endpoint = dev.tricked.solidverdant.data.local.AuthDataStore.DEFAULT_ENDPOINT,
                        clientId = dev.tricked.solidverdant.data.local.AuthDataStore.DEFAULT_CLIENT_ID,
                    )
                    Timber.d("OAuth config reset to defaults")
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to reset OAuth config")
                }
        }
    }

    /**
     * Logout the user
     */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun logout() {
        viewModelScope.launch {
            _uiState.value.user?.profilePhotoUrl?.takeIf { it.isNotBlank() }?.let { url ->
                context.imageLoader.memoryCache?.remove(MemoryCache.Key(url))
                withContext(Dispatchers.IO) {
                    context.imageLoader.diskCache?.remove(url)
                }
            }
            // Cancel any in-flight/queued sync before clearing the cache so it can't
            // re-insert the outgoing account's rows into the just-cleared database.
            // Unsynced changes are cancelled by the scheduler before account data is cleared.
            syncScheduler.cancelSync()
            userCacheCleaner.clear()
            // Clear account-owned data before changing auth state. Once auth is cleared,
            // navigation can dispose this ViewModel and cancel any remaining work.
            authRepository.logout()
            _uiState.value = AuthUiState()
            Timber.d("User logged out")
        }
    }

    /**
     * Clear the auth URL after it's been used
     */
    fun clearAuthUrl() {
        _uiState.value = _uiState.value.copy(authUrl = null)
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

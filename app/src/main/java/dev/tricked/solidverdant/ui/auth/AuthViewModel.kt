package dev.tricked.solidverdant.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.local.UserCacheCleaner
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state for authentication screens
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val memberships: List<Membership> = emptyList(),
    val currentMembership: Membership? = null,
    val error: String? = null,
    val authUrl: String? = null,
    val hasRevalidated: Boolean = false
)

/**
 * UI state for OAuth configuration
 */
data class OAuthConfigState(
    val endpoint: String = "",
    val clientId: String = ""
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        settingsDataStore.getCachedAuth()?.let { cached ->
            AuthUiState(
                user = cached.user,
                memberships = cached.memberships,
                currentMembership = cached.memberships.firstOrNull { it.id == cached.currentMembershipId }
                    ?: cached.memberships.firstOrNull(),
            )
        } ?: AuthUiState()
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _configState = MutableStateFlow(OAuthConfigState())
    val configState: StateFlow<OAuthConfigState> = _configState.asStateFlow()

    val authState: StateFlow<AuthState> = authRepository.isLoggedIn.map {
        if (it) AuthState.LoggedIn else AuthState.LoggedOut
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AuthState.Unknown
    )
    val isLoggedIn: StateFlow<Boolean> = authState.map { it == AuthState.LoggedIn }.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
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
                    Timber.d("OAuth flow started, auth URL: $authUrl")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        authUrl = authUrl
                    )
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to start OAuth flow")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to start OAuth flow"
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
                error = "Invalid OAuth callback parameters"
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
                        error = error.message ?: "Failed to complete OAuth flow"
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
                        hasRevalidated = true
                    )
                    settingsDataStore.cacheAuth(user, memberships, currentMembership?.id)

                    // Save current membership
                    currentMembership?.let {
                        authRepository.saveCurrentMembershipId(it.id)
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load memberships")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load memberships"
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
                        clientId = clientId
                    )
                    Timber.d("OAuth config saved")
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to save OAuth config")
                }
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
                        clientId = dev.tricked.solidverdant.data.local.AuthDataStore.DEFAULT_CLIENT_ID
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
    fun logout() {
        viewModelScope.launch {
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

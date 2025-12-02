package dev.tricked.solidverdant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import dev.tricked.solidverdant.ui.auth.AuthViewModel
import dev.tricked.solidverdant.ui.login.LoginScreen
import dev.tricked.solidverdant.ui.theme.SolidVerdantTheme
import dev.tricked.solidverdant.ui.tracking.TrackingScreen
import dev.tricked.solidverdant.ui.tracking.TrackingViewModel
import timber.log.Timber

/**
 * Main activity for SolidVerdant app
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val trackingViewModel: TrackingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Handle deep links on creation
        handleIntent(intent)

        setContent {
            SolidVerdantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SolidVerdantApp(
                        authViewModel = authViewModel,
                        trackingViewModel = trackingViewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Handle incoming intents (including deep links)
     */
    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            handleDeepLink(uri)
        }
    }

    /**
     * Handle OAuth callback deep link
     */
    private fun handleDeepLink(uri: Uri) {
        Timber.d("Handling deep link: $uri")

        if (uri.scheme == "solidtime" && uri.host == "oauth" && uri.path == "/callback") {
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")

            Timber.d("OAuth callback received - code: ${code?.take(10)}..., state: ${state?.take(10)}...")

            authViewModel.handleOAuthCallback(code, state)
        }
    }
}

/**
 * Root composable for the app
 */
@Composable
fun SolidVerdantApp(
    authViewModel: AuthViewModel,
    trackingViewModel: TrackingViewModel
) {
    val authUiState by authViewModel.uiState.collectAsState()
    val configState by authViewModel.configState.collectAsState()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val trackingUiState by trackingViewModel.uiState.collectAsState()

    // Load user data and tracking state when logged in
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            authViewModel.loadUserData()
        }
    }

    // Load tracking state when user and membership are available
    LaunchedEffect(authUiState.currentMembership) {
        authUiState.currentMembership?.let {
            trackingViewModel.loadActiveTimeEntry()
        }
    }

    when {
        isLoggedIn -> {
            TrackingScreen(
                user = authUiState.user,
                uiState = trackingUiState,
                onRefresh = {
                    authUiState.currentMembership?.let {
                        trackingViewModel.loadActiveTimeEntry()
                    }
                },
                onLogout = {
                    authViewModel.logout()
                },
                onStartTracking = {
                    authUiState.currentMembership?.let { membership ->
                        authUiState.user?.let { user ->
                            trackingViewModel.startTimeEntry(
                                organizationId = membership.organizationId,
                                memberId = membership.id,
                                userId = user.id
                            )
                        }
                    }
                },
                onStopTracking = {
                    authUiState.currentMembership?.let { membership ->
                        authUiState.user?.let { user ->
                            trackingViewModel.stopTimeEntry(
                                organizationId = membership.organizationId,
                                userId = user.id
                            )
                        }
                    }
                }
            )
        }

        else -> {
            LoginScreen(
                uiState = authUiState,
                configState = configState,
                onLoginClick = {
                    authViewModel.startOAuthFlow()
                },
                onConfigSave = { endpoint, clientId ->
                    authViewModel.saveOAuthConfig(endpoint, clientId)
                },
                onConfigReset = {
                    authViewModel.resetOAuthConfig()
                },
                onAuthUrlReady = { /* URL is launched in LoginScreen */ },
                onClearAuthUrl = {
                    authViewModel.clearAuthUrl()
                },
                onClearError = {
                    authViewModel.clearError()
                }
            )
        }
    }
}

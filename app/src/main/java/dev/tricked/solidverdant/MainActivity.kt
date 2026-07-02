package dev.tricked.solidverdant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
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
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.local.AppThemeMode
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
    private var stoppedAtElapsedRealtime: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle deep links on creation
        handleIntent(intent)

        setContent {
            val appTheme by trackingViewModel.appTheme.collectAsState(initial = AppThemeMode.SYSTEM)
            SolidVerdantTheme(themeMode = appTheme) {
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

    override fun onStart() {
        super.onStart()

        val backgroundDuration = stoppedAtElapsedRealtime?.let {
            SystemClock.elapsedRealtime() - it
        }
        val membership = authViewModel.uiState.value.currentMembership
        if (membership != null) {
            trackingViewModel.onAppForegrounded(
                organizationId = membership.organizationId,
                memberId = membership.id,
                refreshAll = backgroundDuration != null &&
                    backgroundDuration >= RESUME_REFRESH_THRESHOLD_MS
            )
        }
        stoppedAtElapsedRealtime = null
    }

    override fun onStop() {
        stoppedAtElapsedRealtime = SystemClock.elapsedRealtime()
        trackingViewModel.onAppBackgrounded()
        super.onStop()
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

    private companion object {
        const val RESUME_REFRESH_THRESHOLD_MS = 30_000L
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
    val alwaysShowNotifications by trackingViewModel.alwaysShowNotifications.collectAsState(initial = false)
    val appTheme by trackingViewModel.appTheme.collectAsState(initial = AppThemeMode.SYSTEM)

    // Load user data when logged in
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            authViewModel.loadUserData()
        }
    }

    // Load all tracking data when user and membership are available
    LaunchedEffect(authUiState.currentMembership?.id) {
        val membership = authUiState.currentMembership
        if (membership != null) {
            trackingViewModel.loadAllData(
                organizationId = membership.organizationId,
                memberId = membership.id
            )
        }
    }

    when {
        isLoggedIn -> {
            TrackingScreen(
                user = authUiState.user,
                memberships = authUiState.memberships,
                currentMembership = authUiState.currentMembership,
                serverEndpoint = configState.endpoint,
                clientId = configState.clientId,
                uiState = trackingUiState,
                alwaysShowNotifications = alwaysShowNotifications,
                appTheme = appTheme,
                onAlwaysShowNotificationsChange = { enabled ->
                    trackingViewModel.setAlwaysShowNotifications(enabled)
                },
                onAppThemeChange = trackingViewModel::setAppTheme,
                onRefresh = {
                    authUiState.currentMembership?.let { membership ->
                        trackingViewModel.loadAllData(
                            organizationId = membership.organizationId,
                            memberId = membership.id
                        )
                    }
                },
                onLogout = {
                    authViewModel.logout()
                },
                onMembershipChange = authViewModel::selectMembership,
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
                                memberId = membership.id,
                                userId = user.id
                            )
                        }
                    }
                },
                onPauseTracking = {
                    authUiState.currentMembership?.let { membership ->
                        authUiState.user?.let { user ->
                            trackingViewModel.pauseTimeEntry(
                                organizationId = membership.organizationId,
                                userId = user.id
                            )
                        }
                    }
                },
                onResumeTracking = {
                    authUiState.currentMembership?.let { membership ->
                        authUiState.user?.let { user ->
                            trackingViewModel.resumeTimeEntry(
                                organizationId = membership.organizationId,
                                memberId = membership.id,
                                userId = user.id
                            )
                        }
                    }
                },
                onDescriptionChange = { description ->
                    trackingViewModel.updateDescription(description)
                },
                onProjectChange = { projectId ->
                    trackingViewModel.updateProject(projectId)
                },
                onTaskChange = { taskId ->
                    trackingViewModel.updateTask(taskId)
                },
                onTagsChange = { tags ->
                    trackingViewModel.updateTags(tags)
                },
                onBillableChange = { billable ->
                    trackingViewModel.updateBillable(billable)
                },
                onUpdateCurrentEntry = {
                    authUiState.currentMembership?.let { membership ->
                        trackingViewModel.updateCurrentTimeEntry(
                            organizationId = membership.organizationId
                        )
                    }
                },
                onUpdatePastEntry = { entry: TimeEntry, description: String?, projectId: String?, taskId: String?, tags: List<String>, billable: Boolean ->
                    authUiState.currentMembership?.let { membership ->
                        trackingViewModel.updatePastTimeEntry(
                            organizationId = membership.organizationId,
                            timeEntry = entry,
                            description = description,
                            projectId = projectId,
                            taskId = taskId,
                            tags = tags,
                            billable = billable
                        )
                    }
                },
                onDeleteEntry = { timeEntryId ->
                    authUiState.currentMembership?.let { membership ->
                        trackingViewModel.deleteTimeEntry(
                            organizationId = membership.organizationId,
                            timeEntryId = timeEntryId
                        )
                    }
                },
                getGroupedEntries = {
                    trackingViewModel.getGroupedTimeEntries()
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

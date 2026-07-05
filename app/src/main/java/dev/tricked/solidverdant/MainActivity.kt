package dev.tricked.solidverdant

import android.app.HandoffActivityData
import android.app.HandoffActivityDataRequestInfo
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.AppThemeMode
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.ui.auth.AuthViewModel
import dev.tricked.solidverdant.ui.auth.AuthState
import dev.tricked.solidverdant.ui.login.LoginScreen
import dev.tricked.solidverdant.ui.components.NetworkAwareContent
import dev.tricked.solidverdant.ui.theme.SolidVerdantTheme
import dev.tricked.solidverdant.ui.tracking.TrackingScreen
import dev.tricked.solidverdant.ui.tracking.TrackingViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import timber.log.Timber

/**
 * Main activity for SolidVerdant app
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    private val authViewModel: AuthViewModel by viewModels()
    private val trackingViewModel: TrackingViewModel by viewModels()
    private var stoppedAtElapsedRealtime: Long? = null
    private var handoffOrganizationId by mutableStateOf<String?>(null)
    private val startupTheme = MutableStateFlow<AppThemeMode?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { startupTheme.value = settingsDataStore.getAppTheme() }
        splash.setKeepOnScreenCondition {
            authViewModel.authState.value == dev.tricked.solidverdant.ui.auth.AuthState.Unknown ||
                !authViewModel.snapshotHydrated.value ||
                !trackingViewModel.snapshotHydrated.value ||
                startupTheme.value == null
        }
        enableEdgeToEdge()

        // Handle deep links on creation
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= 37) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    authViewModel.isLoggedIn.collect { isLoggedIn ->
                        setHandoffEnabled(isLoggedIn, null)
                    }
                }
            }
        }

        setContent {
            val appTheme by trackingViewModel.appTheme.collectAsState(initial = startupTheme.value)
            appTheme?.let { resolvedTheme -> SolidVerdantTheme(themeMode = resolvedTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NetworkAwareContent {
                        SolidVerdantApp(
                            authViewModel = authViewModel,
                            trackingViewModel = trackingViewModel,
                            handoffOrganizationId = handoffOrganizationId,
                            onHandoffConsumed = { handoffOrganizationId = null }
                        )
                    }
                }
            } }
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
        handoffOrganizationId = intent?.getStringExtra(EXTRA_HANDOFF_ORGANIZATION_ID)
        intent?.data?.let { uri ->
            handleDeepLink(uri)
        }
    }

    override fun onHandoffActivityDataRequested(
        handoffRequestInfo: HandoffActivityDataRequestInfo
    ): HandoffActivityData {
        val extras = PersistableBundle().apply {
            authViewModel.uiState.value.currentMembership?.organizationId?.let {
                putString(EXTRA_HANDOFF_ORGANIZATION_ID, it)
            }
        }
        val builder = HandoffActivityData.Builder(ComponentName(this, MainActivity::class.java))
            .setExtras(extras)
        authViewModel.configState.value.endpoint
            .takeIf { it.startsWith("https://") }
            ?.let { builder.setFallbackUri(Uri.parse(it)) }
        return builder.build()
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
        const val EXTRA_HANDOFF_ORGANIZATION_ID = "handoff_organization_id"
    }
}

/**
 * Root composable for the app
 */
@Composable
fun SolidVerdantApp(
    authViewModel: AuthViewModel,
    trackingViewModel: TrackingViewModel,
    handoffOrganizationId: String? = null,
    onHandoffConsumed: () -> Unit = {}
) {
    val authUiState by authViewModel.uiState.collectAsState()
    val configState by authViewModel.configState.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val trackingUiState by trackingViewModel.uiState.collectAsState()
    val alwaysShowNotifications by trackingViewModel.alwaysShowNotifications.collectAsState(initial = false)
    val appTheme by trackingViewModel.appTheme.collectAsState(initial = AppThemeMode.SYSTEM)
    val optimisticRefresh by trackingViewModel.optimisticRefresh.collectAsState(initial = true)
    val hasSnapshot by trackingViewModel.hasSnapshot.collectAsState()
    val snapshotHydrated by trackingViewModel.snapshotHydrated.collectAsState()

    // Load user data when logged in
    LaunchedEffect(authState) {
        if (authState == AuthState.LoggedIn) {
            authViewModel.loadUserData()
        }
    }

    LaunchedEffect(handoffOrganizationId, authUiState.memberships) {
        handoffOrganizationId?.let { organizationId ->
            authUiState.memberships
                .firstOrNull { it.organizationId == organizationId }
                ?.let {
                    authViewModel.selectMembership(it)
                    onHandoffConsumed()
                }
        }
    }

    // Load all tracking data when user and membership are available
    LaunchedEffect(
        authUiState.currentMembership?.id,
        authUiState.hasRevalidated,
        optimisticRefresh,
        hasSnapshot,
        snapshotHydrated
    ) {
        val membership = authUiState.currentMembership
        val mayRefresh = snapshotHydrated && membership != null &&
            (!hasSnapshot || optimisticRefresh || authUiState.hasRevalidated)
        if (membership != null && mayRefresh) {
            trackingViewModel.loadAllData(
                organizationId = membership.organizationId,
                memberId = membership.id
            )
        }
    }

    when {
        authState == AuthState.LoggedIn -> {
            TrackingScreen(
                user = authUiState.user,
                memberships = authUiState.memberships,
                currentMembership = authUiState.currentMembership,
                serverEndpoint = configState.endpoint,
                clientId = configState.clientId,
                uiState = trackingUiState,
                alwaysShowNotifications = alwaysShowNotifications,
                appTheme = appTheme,
                optimisticRefresh = optimisticRefresh,
                onAlwaysShowNotificationsChange = { enabled ->
                    trackingViewModel.setAlwaysShowNotifications(enabled)
                },
                onAppThemeChange = trackingViewModel::setAppTheme,
                onOptimisticRefreshChange = trackingViewModel::setOptimisticRefresh,
                onRefresh = {
                    authUiState.currentMembership?.let { membership ->
                        trackingViewModel.loadAllData(
                            organizationId = membership.organizationId,
                            memberId = membership.id,
                            userInitiated = true
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
                onUpdatePastEntry = { entry: TimeEntry, description: String?, projectId: String?, taskId: String?, tags: List<String>, billable: Boolean, start: String, end: String ->
                    authUiState.currentMembership?.let { membership ->
                        trackingViewModel.updatePastTimeEntry(
                            organizationId = membership.organizationId,
                            timeEntry = entry,
                            description = description,
                            projectId = projectId,
                            taskId = taskId,
                            tags = tags,
                            billable = billable,
                            start = start,
                            end = end
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
                onLoadMoreEntries = trackingViewModel::loadMoreTimeEntries,
                onLoadNewerEntries = trackingViewModel::loadNewerTimeEntries,
                onJumpToDate = trackingViewModel::jumpToHistoryDate,
                onHistoryJumpConsumed = trackingViewModel::consumeHistoryJump,
                getGroupedEntries = {
                    trackingViewModel.getGroupedTimeEntries()
                }
            )
        }

        authState == AuthState.LoggedOut -> {
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

        else -> Unit
    }
}

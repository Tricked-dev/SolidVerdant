/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.tricked.solidverdant.data.local.AppThemeMode
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.sync.SyncStatusReporter
import dev.tricked.solidverdant.ui.auth.AuthState
import dev.tricked.solidverdant.ui.auth.AuthViewModel
import dev.tricked.solidverdant.ui.calendar.CalendarScreen
import dev.tricked.solidverdant.ui.components.AppStatusOverlay
import dev.tricked.solidverdant.ui.login.LoginScreen
import dev.tricked.solidverdant.ui.navigation.MainNavHost
import dev.tricked.solidverdant.ui.navigation.ReviewRoutes
import dev.tricked.solidverdant.ui.review.ReviewBadgeViewModel
import dev.tricked.solidverdant.ui.review.ReviewScreen
import dev.tricked.solidverdant.ui.statistics.StatisticsScreen
import dev.tricked.solidverdant.ui.theme.SolidVerdantTheme
import dev.tricked.solidverdant.ui.tracking.TrackingScreen
import dev.tricked.solidverdant.ui.tracking.TrackingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Main activity for SolidVerdant app
 */
@AndroidEntryPoint
open class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    @Inject lateinit var syncStatusReporter: SyncStatusReporter

    protected val authViewModel: AuthViewModel by viewModels()
    private val trackingViewModel: TrackingViewModel by viewModels()
    private var stoppedAtElapsedRealtime: Long? = null
    private var handoffOrganizationId by mutableStateOf<String?>(null)
    private var editActiveEntryRequested by mutableStateOf(false)
    private val startupTheme = MutableStateFlow(AppThemeMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        startupTheme.value = settingsDataStore.getCachedAppTheme()
        lifecycleScope.launch { startupTheme.value = settingsDataStore.getAppTheme() }
        splash.setKeepOnScreenCondition {
            authViewModel.authState.value == dev.tricked.solidverdant.ui.auth.AuthState.Unknown
        }
        enableEdgeToEdge()

        // Handle deep links on creation
        handleIntent(intent)

        setContent {
            val initialTheme by startupTheme.collectAsState()
            val appTheme by trackingViewModel.appTheme.collectAsState(initial = initialTheme)
            val syncStatus by syncStatusReporter.status.collectAsState()
            val resolvedTheme = appTheme ?: initialTheme
            SolidVerdantTheme(themeMode = resolvedTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppStatusOverlay(
                        syncStatus = syncStatus,
                        onRetrySync = { trackingViewModel.retrySync() },
                    ) {
                        SolidVerdantApp(
                            authViewModel = authViewModel,
                            trackingViewModel = trackingViewModel,
                            handoffOrganizationId = handoffOrganizationId,
                            onHandoffConsumed = { handoffOrganizationId = null },
                            editActiveEntryRequested = editActiveEntryRequested,
                            onEditActiveEntryConsumed = { editActiveEntryRequested = false },
                        )
                    }
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
                    backgroundDuration >= RESUME_REFRESH_THRESHOLD_MS,
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
        if (intent?.getBooleanExtra(EXTRA_EDIT_ACTIVE_ENTRY, false) == true) {
            editActiveEntryRequested = true
            intent.removeExtra(EXTRA_EDIT_ACTIVE_ENTRY)
        }
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

    companion object {
        const val RESUME_REFRESH_THRESHOLD_MS = 30_000L
        const val EXTRA_HANDOFF_ORGANIZATION_ID = "handoff_organization_id"
        const val EXTRA_EDIT_ACTIVE_ENTRY = "edit_active_entry"
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
    onHandoffConsumed: () -> Unit = {},
    editActiveEntryRequested: Boolean = false,
    onEditActiveEntryConsumed: () -> Unit = {},
) {
    val authUiState by authViewModel.uiState.collectAsState()
    val configState by authViewModel.configState.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val trackingUiState by trackingViewModel.uiState.collectAsState()
    val alwaysShowNotifications by trackingViewModel.alwaysShowNotifications.collectAsState(initial = false)
    val appTheme by trackingViewModel.appTheme.collectAsState(initial = AppThemeMode.SYSTEM)
    val optimisticRefresh by trackingViewModel.optimisticRefresh.collectAsState(initial = true)
    val longTimerHours by trackingViewModel.longTimerHours.collectAsState(initial = 4)
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
        snapshotHydrated,
    ) {
        val membership = authUiState.currentMembership
        val mayRefresh = snapshotHydrated && membership != null &&
            (!hasSnapshot || optimisticRefresh || authUiState.hasRevalidated)
        if (membership != null && mayRefresh) {
            trackingViewModel.loadAllData(
                organizationId = membership.organizationId,
                memberId = membership.id,
            )
        }
    }

    when {
        authState == AuthState.LoggedIn -> {
            val navController = rememberNavController()
            val currentMembership = authUiState.currentMembership
            val reviewBadgeViewModel: ReviewBadgeViewModel = hiltViewModel()
            LaunchedEffect(currentMembership?.organizationId) {
                reviewBadgeViewModel.setOrganization(currentMembership?.organizationId)
            }
            val inboxBadgeCount by reviewBadgeViewModel.openIssueCount.collectAsState()
            MainNavHost(
                navController = navController,
                inboxBadgeCount = inboxBadgeCount,
                reviewContent = {
                    ReviewScreen(
                        onOpenReminderSettings = {
                            navController.navigate(ReviewRoutes.ReminderSettings)
                        },
                        onOpenManageTemplates = {
                            navController.navigate(ReviewRoutes.ManageTemplates)
                        },
                        onOpenEndOfDayReview = {
                            navController.navigate(ReviewRoutes.EndOfDay)
                        },
                    )
                },
                trackContent = {
                    TrackingScreen(
                        user = authUiState.user,
                        memberships = authUiState.memberships,
                        currentMembership = authUiState.currentMembership,
                        serverEndpoint = configState.endpoint,
                        clientId = configState.clientId,
                        uiState = trackingUiState,
                        elapsedSeconds = trackingViewModel.elapsedSeconds,
                        alwaysShowNotifications = alwaysShowNotifications,
                        appTheme = appTheme,
                        optimisticRefresh = optimisticRefresh,
                        longTimerHours = longTimerHours,
                        editActiveEntryRequested = editActiveEntryRequested,
                        onEditActiveEntryConsumed = onEditActiveEntryConsumed,
                        onAlwaysShowNotificationsChange = { enabled ->
                            trackingViewModel.setAlwaysShowNotifications(enabled)
                        },
                        onAppThemeChange = trackingViewModel::setAppTheme,
                        onOptimisticRefreshChange = trackingViewModel::setOptimisticRefresh,
                        onLongTimerHoursChange = trackingViewModel::setLongTimerHours,
                        onRefresh = {
                            authUiState.currentMembership?.let { membership ->
                                trackingViewModel.loadAllData(
                                    organizationId = membership.organizationId,
                                    memberId = membership.id,
                                    userInitiated = true,
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
                                        userId = user.id,
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
                                        userId = user.id,
                                    )
                                }
                            }
                        },
                        onPauseTracking = {
                            authUiState.currentMembership?.let { membership ->
                                authUiState.user?.let { user ->
                                    trackingViewModel.pauseTimeEntry(
                                        organizationId = membership.organizationId,
                                        userId = user.id,
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
                                        userId = user.id,
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
                                    organizationId = membership.organizationId,
                                )
                            }
                        },
                        onUpdatePastEntry = {
                                entry: TimeEntry,
                                description: String?,
                                projectId: String?,
                                taskId: String?,
                                tags: List<String>,
                                billable: Boolean,
                                start: String,
                                end: String,
                            ->
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
                                    end = end,
                                )
                            }
                        },
                        onCreateEntry = {
                                description: String?,
                                projectId: String?,
                                taskId: String?,
                                tags: List<String>,
                                billable: Boolean,
                                start: String,
                                end: String,
                            ->
                            authUiState.currentMembership?.let { membership ->
                                authUiState.user?.let { user ->
                                    trackingViewModel.createManualTimeEntry(
                                        organizationId = membership.organizationId,
                                        memberId = membership.id,
                                        userId = user.id,
                                        description = description,
                                        projectId = projectId,
                                        taskId = taskId,
                                        tags = tags,
                                        billable = billable,
                                        start = start,
                                        end = end,
                                    )
                                }
                            }
                        },
                        onDeleteEntry = { timeEntryId ->
                            authUiState.currentMembership?.let { membership ->
                                trackingViewModel.deleteTimeEntry(
                                    organizationId = membership.organizationId,
                                    timeEntryId = timeEntryId,
                                )
                            }
                        },
                        onUndoDelete = trackingViewModel::undoDelete,
                        onRetrySync = trackingViewModel::retrySync,
                        onRetrySyncEntry = trackingViewModel::retrySync,
                        onLoadMoreEntries = trackingViewModel::loadMoreTimeEntries,
                        onLoadNewerEntries = trackingViewModel::loadNewerTimeEntries,
                        onJumpToDate = trackingViewModel::jumpToHistoryDate,
                        onHistoryJumpConsumed = trackingViewModel::consumeHistoryJump,
                        getGroupedEntries = {
                            trackingViewModel.getGroupedTimeEntries()
                        },
                    )
                },
                calendarContent = {
                    if (currentMembership != null) {
                        CalendarScreen(
                            organizationId = currentMembership.organizationId,
                            memberId = currentMembership.id,
                            projects = trackingUiState.projects,
                            tasks = trackingUiState.tasks,
                            tags = trackingUiState.tags,
                            onSaveEntry = { entry, description, projectId, taskId, entryTags, billable, start, end ->
                                trackingViewModel.updatePastTimeEntry(
                                    organizationId = currentMembership.organizationId,
                                    timeEntry = entry,
                                    description = description,
                                    projectId = projectId,
                                    taskId = taskId,
                                    tags = entryTags,
                                    billable = billable,
                                    start = start,
                                    end = end,
                                )
                            },
                        )
                    }
                },
                statsContent = {
                    StatisticsScreen()
                },
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
                onTestConnection = authViewModel::testConnection,
                onAuthUrlReady = { /* URL is launched in LoginScreen */ },
                onClearAuthUrl = {
                    authViewModel.clearAuthUrl()
                },
                onClearError = {
                    authViewModel.clearError()
                },
            )
        }

        else -> Unit
    }
}

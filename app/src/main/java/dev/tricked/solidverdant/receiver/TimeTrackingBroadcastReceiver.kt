/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.repository.AuthRepository
import dev.tricked.solidverdant.service.TimeTrackingNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * BroadcastReceiver for handling time tracking actions from notifications
 */
@AndroidEntryPoint
class TimeTrackingBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("TimeTrackingBroadcastReceiver.onReceive called - action=${intent?.action}, context=$context")
        if (context == null || intent == null) {
            Timber.w("TimeTrackingBroadcastReceiver: context or intent is null")
            return
        }

        when (intent.action) {
            TimeTrackingNotificationService.ACTION_STOP_TRACKING_BROADCAST -> {
                Timber.d("Handling stop tracking broadcast")
                handleStopTracking(context)
            }
            else -> {
                Timber.w("TimeTrackingBroadcastReceiver: Unknown action ${intent.action}")
            }
        }
    }

    private fun handleStopTracking(context: Context) {
        Timber.d("Received stop tracking from notification")
        val pendingResult = goAsync()

        scope.launch {
            try {
                // Get active time entry
                authRepository.getActiveTimeEntry()
                    .onSuccess { activeEntry ->
                        if (activeEntry != null) {
                            // Get user info for userId
                            authRepository.getCurrentUser()
                                .onSuccess { user ->
                                    // Stop the active time entry
                                    authRepository.stopTimeEntry(
                                        organizationId = activeEntry.organizationId,
                                        timeEntryId = activeEntry.id,
                                        userId = user.id,
                                        startTime = activeEntry.start
                                    ).onSuccess {
                                        Timber.d("Time entry stopped successfully")

                                        // Update notification state based on settings
                                        val alwaysShow =
                                            settingsDataStore.alwaysShowNotification.first()
                                        if (alwaysShow) {
                                            // Show idle notification
                                            TimeTrackingNotificationService.showIdle(context)
                                        } else {
                                            // Hide notification completely
                                            TimeTrackingNotificationService.hide(context)
                                        }
                                    }.onFailure { error ->
                                        Timber.e(
                                            error,
                                            "Failed to stop time entry from notification"
                                        )
                                    }
                                }.onFailure { error ->
                                    Timber.e(error, "Failed to get user info")
                                }
                        } else {
                            Timber.w("No active time entry to stop")
                            // Still update notification state
                            val alwaysShow = settingsDataStore.alwaysShowNotification.first()
                            if (alwaysShow) {
                                TimeTrackingNotificationService.showIdle(context)
                            } else {
                                TimeTrackingNotificationService.hide(context)
                            }
                        }
                    }.onFailure { error ->
                        Timber.e(error, "Failed to get active time entry")
                    }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

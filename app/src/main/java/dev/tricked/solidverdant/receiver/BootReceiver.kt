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
import java.time.Instant
import javax.inject.Inject

/**
 * Receives boot completed broadcast and restores notification if tracking was active
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Timber.d("Boot completed, checking for active tracking")

        // Use goAsync to allow coroutine to complete
        val pendingResult = goAsync()

        scope.launch {
            try {
                // Check if user is logged in
                val isLoggedIn = authRepository.isLoggedIn.first()
                if (!isLoggedIn) {
                    Timber.d("User not logged in, skipping notification restore")
                    pendingResult.finish()
                    return@launch
                }

                // Check if always show notifications is enabled
                val alwaysShowNotifications = settingsDataStore.alwaysShowNotification.first()
                if (!alwaysShowNotifications) {
                    Timber.d("Always show notifications disabled, skipping notification restore")
                    pendingResult.finish()
                    return@launch
                }

                // Check if there's an active time entry
                val activeEntryResult = authRepository.getActiveTimeEntry()
                activeEntryResult.onSuccess { timeEntry ->
                    if (timeEntry != null) {
                        Timber.d("Active tracking found, restoring tracking notification")
                        TimeTrackingNotificationService.startTracking(
                            context = context,
                            startTime = Instant.parse(timeEntry.start),
                            projectName = null, // Will be updated when app loads
                            taskName = null,
                            description = timeEntry.description
                        )
                    } else {
                        Timber.d("No active tracking, showing idle notification")
                        TimeTrackingNotificationService.showIdle(context)
                    }
                }.onFailure { error ->
                    Timber.e(error, "Failed to check active time entry")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in boot receiver")
            } finally {
                pendingResult.finish()
            }
        }
    }
}

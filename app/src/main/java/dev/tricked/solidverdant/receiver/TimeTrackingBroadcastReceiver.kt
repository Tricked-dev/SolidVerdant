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
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.service.TimeTrackingNotificationService
import dev.tricked.solidverdant.sync.SyncTrigger
import dev.tricked.solidverdant.widget.TimeTrackingWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Handles the "Stop" action fired from the home-screen widget (and any other surface that needs
 * to stop tracking without a live Activity).
 *
 * This runs the same offline-capable stop path the app uses: an optimistic Room write plus an
 * outbox enqueue, then a sync request. Because it is a manifest-declared, Hilt-injected receiver
 * it works even when the app process was dead — the object graph is built on delivery, so the
 * stop happens reliably rather than merely opening the Activity. It always tears down the
 * tracking notification and refreshes the widget so the UI can never get stuck showing a running
 * timer after the user pressed Stop.
 */
@AndroidEntryPoint
class TimeTrackingBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var timeEntryRepository: TimeEntryRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var syncTrigger: SyncTrigger

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP_TRACKING_FROM_NOTIFICATION) {
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                stopActiveTracking()
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop tracking from widget action")
            } finally {
                // Regardless of whether an entry was found/stopped, reconcile the UI: dismiss
                // the tracking foreground notification (or switch to the idle prompt) and clear
                // the widget's tracking state so the elapsed timer notification/clock is gone.
                try {
                    if (settingsDataStore.alwaysShowNotification.first()) {
                        TimeTrackingNotificationService.showIdle(context)
                    } else {
                        TimeTrackingNotificationService.hide(context)
                    }
                    settingsDataStore.setWidgetTrackingState(isTracking = false)
                    TimeTrackingWidget.requestUpdate(context)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to reconcile UI after stop")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun stopActiveTracking() {
        if (!authRepository.isLoggedIn.first()) {
            Timber.d("Stop action ignored: not logged in")
            return
        }
        val membership = authRepository.getCurrentMembership() ?: return
        val activeEntry =
            timeEntryRepository.observeActiveEntry(membership.organizationId).first() ?: run {
                Timber.d("Stop action: no active entry to stop")
                return
            }
        val userId = activeEntry.userId.ifBlank {
            authRepository.getCurrentUser().getOrNull()?.id ?: return
        }
        // Optimistic local stop + outbox enqueue; the sync worker pushes it to the server.
        timeEntryRepository.stopEntry(activeEntry, userId)
        syncTrigger.requestSync()
    }

    companion object {
        const val ACTION_STOP_TRACKING_FROM_NOTIFICATION =
            "dev.tricked.solidverdant.ACTION_STOP_TRACKING_FROM_NOTIFICATION"
    }
}

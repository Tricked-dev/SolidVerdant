/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.tricked.solidverdant.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Durable replacement for the old in-process `serviceScope.launch { delay(...) }` forgotten-timer
 * warning (SV-016): that approach lived entirely in [TimeTrackingNotificationService]'s memory, so
 * it - and any "Keep Running" snooze - was silently lost whenever the service process died, since
 * the service returns START_NOT_STICKY and nothing re-armed the warning.
 *
 * [TimeTrackingNotificationService] now persists the next warning deadline (and the start time of
 * the entry it applies to) in [SettingsDataStore] whenever it (re)schedules the warning, and
 * enqueues this worker as unique WorkManager work with a matching initial delay. WorkManager
 * persists pending work across process death and reboot, so this worker still runs even if the
 * service that scheduled it is long gone; it reads the deadline back, checks it is still due and
 * still applies to the same running entry, and if so asks the service to display the warning via
 * [TimeTrackingNotificationService.showLongTimerWarning].
 */
@HiltWorker
class LongTimerWarningWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsDataStore: SettingsDataStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val deadline = settingsDataStore.longTimerWarningDeadline.first()
        if (deadline == null) {
            Timber.d("Long timer warning worker fired with no persisted deadline; skipping")
            return Result.success()
        }

        val expectedEntryStartEpochMs = inputData.getLong(INPUT_ENTRY_START_EPOCH_MS, -1L)
        if (expectedEntryStartEpochMs >= 0 && deadline.entryStartEpochMs != expectedEntryStartEpochMs) {
            // The persisted deadline has moved on to a newer entry/snooze since this work was
            // enqueued (e.g. superseded by a REPLACE); the newer unique work owns delivery now.
            Timber.d("Long timer warning worker skipped: deadline is for a different entry")
            return Result.success()
        }

        if (System.currentTimeMillis() < deadline.deadlineEpochMs) {
            // Fired early (should not normally happen with WorkManager's initial delay, but guard
            // against clock changes / doze batching). Nothing to do; the deadline is still pending.
            Timber.d("Long timer warning worker fired before its deadline; skipping")
            return Result.success()
        }

        TimeTrackingNotificationService.showLongTimerWarning(appContext, deadline.entryStartEpochMs)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "long-timer-warning"
        private const val INPUT_ENTRY_START_EPOCH_MS = "entry_start_epoch_ms"

        /**
         * Schedule (or replace) the durable warning to fire after [delaySeconds]. Uses
         * [ExistingWorkPolicy.REPLACE] so rescheduling (new timer, refreshed threshold, or a
         * "Keep Running" snooze) always supersedes any previously pending warning for this device.
         */
        fun schedule(context: Context, delaySeconds: Long, entryStartEpochMs: Long) {
            val request = OneTimeWorkRequestBuilder<LongTimerWarningWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setInputData(workDataOf(INPUT_ENTRY_START_EPOCH_MS to entryStartEpochMs))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /** Cancel any pending warning, e.g. when the timer stops, pauses, or is no longer eligible. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}

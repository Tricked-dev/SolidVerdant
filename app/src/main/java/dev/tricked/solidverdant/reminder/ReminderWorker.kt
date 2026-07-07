/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.tricked.solidverdant.MainActivity
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.ui.navigation.ReviewRoutes
import dev.tricked.solidverdant.util.Clock
import dev.tricked.solidverdant.util.NotificationPermissionHelper
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId

/**
 * Posts the daily reminder / end-of-day review nudge and re-anchors the schedule for the next day.
 *
 * Behaviour (gap analysis #4, #18, #78):
 * - Reads the live preferences; if the user turned everything off after this work was enqueued it
 *   cancels itself and posts nothing.
 * - Re-anchors the periodic schedule to the chosen local time first, so delivery keeps tracking
 *   timezone / DST changes even if this particular run is suppressed.
 * - Suppresses duplicates to one nudge per local day (a periodic request can re-run shortly after
 *   reboot / process recreation) using a private last-delivered marker.
 * - Handles POST_NOTIFICATIONS being denied without crashing; the schedule stays in place so it
 *   starts working once the user grants permission.
 * - Does not nag with a pure tracking reminder while a timer is already running.
 *
 * No sensitive work data (descriptions, projects, tags) is ever logged.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsDataStore,
    private val timeEntryRepository: TimeEntryRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reminderOn = settings.reminderEnabled.first()
        val reviewOn = settings.endOfDayReviewEnabled.first()

        // Everything is off (e.g. disabled after enqueue): make sure no stale periodic work lingers.
        if (!reminderOn && !reviewOn) {
            scheduler.cancel()
            return Result.success()
        }

        // Re-anchor the next run to the chosen local time first, so the schedule keeps tracking the
        // wall clock across timezone / DST changes even if delivery below is skipped.
        runCatching { scheduler.schedule(settings.reminderMinuteOfDay.first()) }
            .onFailure { Timber.w(it, "Failed to re-anchor reminder schedule") }

        // One nudge per local day. WorkManager may re-run a periodic request soon after reboot.
        val today = Instant.ofEpochMilli(clock.nowMs()).atZone(ZoneId.systemDefault())
            .toLocalDate().toEpochDay()
        val statePrefs = appContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        if (statePrefs.getLong(KEY_LAST_DELIVERED_DAY, Long.MIN_VALUE) == today) {
            return Result.success()
        }

        // Nothing to nudge about when signed out (the cache is cleared on logout); the schedule is
        // left in place because reminder preferences persist across logout like other settings.
        val orgId = settings.currentOrganizationIdOrNull()
        if (orgId == null) {
            Timber.d("Reminder skipped: no signed-in organization")
            return Result.success()
        }

        // Cannot nudge without notification permission (Android 13+). Leave the schedule in place.
        if (!NotificationPermissionHelper.hasNotificationPermission(appContext)) {
            Timber.d("Reminder skipped: notifications not permitted")
            return Result.success()
        }

        // A pure tracking reminder should not nag while a timer is already running. The end-of-day
        // review is still worth showing while tracking, so it takes precedence.
        if (reminderOn && !reviewOn) {
            val timerRunning = timeEntryRepository.observeActiveEntry(orgId).first() != null
            if (timerRunning) {
                return Result.success()
            }
        }

        postNudge(reviewOn = reviewOn)
        statePrefs.edit { putLong(KEY_LAST_DELIVERED_DAY, today) }
        return Result.success()
    }

    private fun postNudge(reviewOn: Boolean) {
        ensureChannel(appContext)

        val titleRes = if (reviewOn) R.string.reminder_review_title else R.string.reminder_track_title
        val textRes = if (reviewOn) R.string.reminder_review_text else R.string.reminder_track_text
        val body = appContext.getString(textRes)

        val reviewIntent = openReviewPendingIntent()
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(appContext.getString(titleRes))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(if (reviewOn) reviewIntent else openAppPendingIntent())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (reviewOn) {
            builder.addAction(
                R.drawable.ic_timer,
                appContext.getString(R.string.reminder_action_review_now),
                reviewIntent,
            )
        } else {
            builder.addAction(
                R.drawable.ic_timer,
                appContext.getString(R.string.reminder_action_open),
                openAppPendingIntent(),
            )
        }

        // Permission was checked above, but it can be revoked between that check and here;
        // handle the resulting SecurityException explicitly instead of crashing the worker.
        try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Timber.w(e, "Reminder notification suppressed: notification permission revoked")
        }
    }

    /**
     * Launches the app asking it to open the end-of-day review flow. The extra is consumed by
     * [MainActivity] to navigate to [ReviewRoutes.EndOfDay]; tapping the notification always at
     * least brings the user into the app.
     */
    private fun openReviewPendingIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_REVIEW_ROUTE, ReviewRoutes.EndOfDay)
        }
        return PendingIntent.getActivity(
            appContext,
            REQ_REVIEW,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            REQ_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val CHANNEL_ID = "review_reminder"
        private const val NOTIFICATION_ID = 2001
        private const val REQ_REVIEW = 20
        private const val REQ_OPEN = 21
        private const val STATE_PREFS = "reminder_state"
        private const val KEY_LAST_DELIVERED_DAY = "last_delivered_epoch_day"

        /**
         * Extra carried on the [MainActivity] launch intent asking the app to open a review route.
         * MainActivity must read this and navigate to the given route to complete the deep link.
         */
        const val EXTRA_OPEN_REVIEW_ROUTE = "dev.tricked.solidverdant.OPEN_REVIEW_ROUTE"

        /** Create the reminder notification channel. Safe to call repeatedly. */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}

package dev.tricked.solidverdant.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily reminder / end-of-day review nudge as unique periodic WorkManager work.
 *
 * Periodic work is persisted by WorkManager and automatically restored after reboot and process
 * death, which is what makes the reminder "survive Android constraints" (gap analysis #78) without
 * requesting exact alarms. The [ReminderWorker] re-anchors the schedule to the next local
 * occurrence after each run, so the delivery time keeps tracking timezone / DST changes over time.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
    private val clock: Clock,
) {

    /**
     * Re-evaluate the schedule from the current preferences. Enqueues (or updates) the periodic
     * nudge when either the tracking reminder or the end-of-day review is enabled, and cancels it
     * when both are off. Safe to call whenever a relevant preference changes.
     */
    suspend fun reschedule() {
        val reminderOn = settings.reminderEnabled.first()
        val reviewOn = settings.endOfDayReviewEnabled.first()
        if (!reminderOn && !reviewOn) {
            cancel()
            return
        }
        schedule(settings.reminderMinuteOfDay.first())
    }

    /** Enqueue the unique periodic nudge anchored to the next occurrence of [minuteOfDay]. */
    fun schedule(minuteOfDay: Int, zone: ZoneId = ZoneId.systemDefault()) {
        val initialDelay = ReminderSchedule.initialDelayMillis(clock.nowMs(), zone, minuteOfDay)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            // UPDATE keeps a single schedule and simply re-anchors the next run when the time or
            // enabled state changes, instead of stacking duplicate periodic requests.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    companion object {
        const val UNIQUE_NAME = "review-reminder"
        const val TAG = "review-reminder"
    }
}

/**
 * Resolve the currently-selected organization id from the offline auth cache without touching the
 * network. Mirrors [dev.tricked.solidverdant.data.repository.AuthRepository]'s selection rule: the
 * chosen membership, or the first one. Returns null when signed out or when no membership is
 * cached.
 */
internal fun SettingsDataStore.currentOrganizationIdOrNull(): String? {
    val cached = getCachedAuth() ?: return null
    val selected = cached.memberships.firstOrNull { it.id == cached.currentMembershipId }
    return (selected ?: cached.memberships.firstOrNull())?.organizationId
}

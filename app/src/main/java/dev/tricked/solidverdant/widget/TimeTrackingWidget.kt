/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import dev.tricked.solidverdant.MainActivity
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.ui.tile.ProjectSelectionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Duration
import java.time.Instant

/**
 * Home screen widget for time tracking
 *
 * Note: Widgets cannot use Hilt injection as they are instantiated by the system.
 * This widget reads tracking state from DataStore to display current status.
 */
class TimeTrackingWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Timber.d("Widget onUpdate called for ${appWidgetIds.size} widgets")

        // Use goAsync() to handle the coroutine properly
        val pendingResult = goAsync()

        // Launch coroutine to read from DataStore
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Read widget state from DataStore using the singleton instance
                val settingsDataStore = SettingsDataStore.getInstance(context)
                val widgetState = settingsDataStore.widgetState.first()

                Timber.d("Widget state: isTracking=${widgetState.isTracking}, startTime=${widgetState.startTimeEpochMillis}, project=${widgetState.projectName}")

                // Update all widgets with current tracking state
                for (appWidgetId in appWidgetIds) {
                    if (widgetState.isTracking && widgetState.startTimeEpochMillis > 0) {
                        Timber.d("Showing tracking view for widget $appWidgetId")
                        showTrackingView(
                            context,
                            appWidgetManager,
                            appWidgetId,
                            widgetState.startTimeEpochMillis,
                            widgetState.projectName,
                            widgetState.taskName,
                            widgetState.description
                        )
                    } else {
                        Timber.d("Showing idle view for widget $appWidgetId")
                        showIdleView(context, appWidgetManager, appWidgetId)
                    }
                }

                // Schedule periodic updates if tracking
                if (widgetState.isTracking && widgetState.startTimeEpochMillis > 0) {
                    scheduleNextUpdate(context)
                } else {
                    cancelScheduledUpdate(context)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating widget")
                // If there's an error reading state, show idle view for all widgets
                for (appWidgetId in appWidgetIds) {
                    showIdleView(context, appWidgetManager, appWidgetId)
                }
            } finally {
                // Always finish the pending result
                pendingResult.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Cancel any scheduled updates when all widgets are removed
        cancelScheduledUpdate(context)
        Timber.d("Widget disabled, canceled scheduled updates")
    }

    private fun showIdleView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_time_tracking)

        // Set idle background
        views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.widget_background_idle)

        // Show idle state
        views.setTextViewText(R.id.widget_timer, "00:00:00")
        views.setTextViewText(R.id.widget_details, context.getString(R.string.widget_not_tracking))

        // Set click intent to open app
        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, openPendingIntent)

        // Set button to open project selection to start tracking
        views.setTextViewText(R.id.widget_button, context.getString(R.string.start_tracking))
        val startIntent = Intent(context, ProjectSelectionActivity::class.java)
        val startPendingIntent = PendingIntent.getActivity(
            context, 2, startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_button, startPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun showTrackingView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        startTimeMillis: Long,
        projectName: String?,
        taskName: String?,
        description: String?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_time_tracking)

        // Set tracking background with green gradient
        views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.widget_background_tracking)

        // Calculate elapsed time
        val now = Instant.now()
        val startInstant = Instant.ofEpochMilli(startTimeMillis)
        val duration = Duration.between(startInstant, now)
        val hours = duration.toHours()
        val minutes = (duration.toMinutes() % 60)
        val rawSeconds = (duration.seconds % 60)

        val seconds = if (duration.seconds > 60) {
            if (rawSeconds < 15) 0
            else if (rawSeconds < 45) 30
            else 0
        } else {
            rawSeconds
        }

        val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)

        // Build details string
        val details = buildString {
            if (!projectName.isNullOrBlank()) {
                append(projectName)
            }
            if (!taskName.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append(taskName)
            }
            if (!description.isNullOrBlank()) {
                if (isNotEmpty()) append("\n")
                append(description)
            }
            if (isEmpty()) {
                append("Tracking time...")
            }
        }

        views.setTextViewText(R.id.widget_timer, timeString)
        views.setTextViewText(R.id.widget_details, details)

        // Set click intent to open app
        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, openPendingIntent)

        // Set button to stop tracking (opens main activity with stop action)
        views.setTextViewText(R.id.widget_button, context.getString(R.string.stop_tracking))
        val stopIntent = Intent(context, MainActivity::class.java).apply {
            action = "dev.tricked.solidverdant.ACTION_STOP_TRACKING"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val stopPendingIntent = PendingIntent.getActivity(
            context, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_button, stopPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /**
     * Schedule next widget update for tracking timer
     */
    private fun scheduleNextUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TimeTrackingWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, TimeTrackingWidget::class.java)
            )
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use inexact repeating to avoid needing SCHEDULE_EXACT_ALARM permission
        // Updates approximately every 15 seconds when tracking
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 15_000,
            15_000,
            pendingIntent
        )
    }

    /**
     * Cancel scheduled widget updates
     */
    private fun cancelScheduledUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TimeTrackingWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        private const val ALARM_ID = 12345

        /**
         * Request widget update
         */
        fun requestUpdate(context: Context) {
            val intent = Intent(context, TimeTrackingWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(
                    android.content.ComponentName(context, TimeTrackingWidget::class.java)
                )
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}

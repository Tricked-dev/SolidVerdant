/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat

/** Android 16 (API 36) is the first release with promoted ongoing notifications. */
internal const val LIVE_UPDATES_API_LEVEL = 36
internal const val ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS =
    "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"

/**
 * Requests Live Update promotion without changing the notification on older Android releases.
 * The operating system still decides whether promotion is available for the app and device.
 */
@SuppressLint("InlinedApi")
internal fun NotificationCompat.Builder.setLiveUpdateRequested(
    enabled: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): NotificationCompat.Builder = apply {
    if (enabled && sdkInt >= LIVE_UPDATES_API_LEVEL) {
        addExtras(
            Bundle().apply {
                putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
            },
        )
    }
}

/** Returns whether Android will currently accept promoted ongoing notifications for this app. */
internal fun canPostPromotedNotifications(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    if (sdkInt < LIVE_UPDATES_API_LEVEL) return false
    @Suppress("NewApi")
    return context.getSystemService(NotificationManager::class.java)?.canPostPromotedNotifications() == true
}

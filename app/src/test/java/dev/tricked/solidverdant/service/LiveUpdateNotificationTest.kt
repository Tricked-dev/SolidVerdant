/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.service

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveUpdateNotificationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun enabled_live_update_requests_android_16_promotion() {
        val notification = buildNotification(enabled = true, sdkInt = LIVE_UPDATES_API_LEVEL)

        assertTrue(notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    @Test
    fun disabled_live_update_does_not_request_promotion() {
        val notification = buildNotification(enabled = false, sdkInt = LIVE_UPDATES_API_LEVEL)

        assertFalse(notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    @Test
    fun enabled_live_update_is_ignored_before_android_16() {
        val notification = buildNotification(enabled = true, sdkInt = LIVE_UPDATES_API_LEVEL - 1)

        assertFalse(notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    private fun buildNotification(enabled: Boolean, sdkInt: Int): Notification = NotificationCompat.Builder(context, "test")
        .setContentTitle("Working")
        .setOngoing(true)
        .setLiveUpdateRequested(enabled = enabled, sdkInt = sdkInt)
        .build()
}

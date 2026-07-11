/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * SV-013: work-bearing notifications (description, project, task) must never leak onto the lock
 * screen. [TimeTrackingNotificationService] builds them with
 * [NotificationCompat.VISIBILITY_PRIVATE] and a redacted [NotificationCompat.Builder.setPublicVersion]
 * that only carries a generic "Timer running" / "Timer paused" title.
 *
 * The service is a Hilt `@AndroidEntryPoint` with `@Inject lateinit var` fields (authRepository,
 * settingsDataStore), so it cannot be constructed directly and field-injection has to go through
 * Robolectric's real (production) Hilt component for [dev.tricked.solidverdant.SolidVerdantApp].
 * That is safe here because the code path under test - onCreate() -> createNotificationChannel(),
 * and onStartCommand(ACTION_START_TRACKING) -> publishNotification() -> buildTrackingNotification()
 * - never reads authRepository or any of its flows (which would hit the AndroidKeyStore-backed
 * AuthSecretCipher and throw under Robolectric). Only settingsDataStore.alwaysShowNotification is
 * touched, and only on the stop path, which these tests do not exercise.
 *
 * The notification-building methods themselves (buildTrackingNotification,
 * buildRedactedPublicNotification, buildPausedNotification) are private with no internal/public
 * seam, so this test drives them the same way production code does: through the service's public
 * onStartCommand(Intent) entry point, then inspects the notification Robolectric's ShadowService
 * captured from startForeground(...).
 */
@RunWith(RobolectricTestRunner::class)
class NotificationRedactionTest {

    private val secretDescription = "secret client work"
    private val secretProject = "ProjectX"
    private val secretTask = "confidential-task"

    private fun startTrackingIntent() = Intent(
        ApplicationProvider.getApplicationContext(),
        TimeTrackingNotificationService::class.java,
    ).apply {
        action = TimeTrackingNotificationService.ACTION_START_TRACKING
        putExtra(TimeTrackingNotificationService.EXTRA_START_TIME, System.currentTimeMillis())
        putExtra(TimeTrackingNotificationService.EXTRA_PROJECT_NAME, secretProject)
        putExtra(TimeTrackingNotificationService.EXTRA_TASK_NAME, secretTask)
        putExtra(TimeTrackingNotificationService.EXTRA_DESCRIPTION, secretDescription)
    }

    @Test
    fun tracking_notification_is_private_with_redacted_public_version() {
        val controller = Robolectric.buildService(TimeTrackingNotificationService::class.java)
        val service = controller.create().get()

        service.onStartCommand(startTrackingIntent(), 0, 1)

        val notification = shadowOf(service).lastForegroundNotification
        assertNotNull("Expected a foreground notification to have been posted", notification)
        checkNotNull(notification)

        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, notification.visibility)

        val publicVersion = notification.publicVersion
        assertNotNull("publicVersion must be set so the lock screen shows a redacted stand-in", publicVersion)
        checkNotNull(publicVersion)

        assertEquals(Notification.VISIBILITY_PUBLIC, publicVersion.visibility)

        val publicTitle = publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val publicText = publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        // Generic, localized, redacted content only - no description/project/task leakage.
        assertEquals("Timer running", publicTitle)
        assertTrue(publicText.isBlank())
        assertFalse(publicTitle.contains(secretDescription))
        assertFalse(publicTitle.contains(secretProject))
        assertFalse(publicTitle.contains(secretTask))
        assertFalse(publicText.contains(secretDescription))
        assertFalse(publicText.contains(secretProject))
        assertFalse(publicText.contains(secretTask))

        // The private (real) notification is where the work data actually lives.
        val privateText = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertTrue(privateText.contains(secretDescription))
        assertTrue(privateText.contains(secretProject))
    }

    @Test
    fun paused_notification_public_version_is_also_redacted() {
        val controller = Robolectric.buildService(TimeTrackingNotificationService::class.java)
        val service = controller.create().get()

        // Start tracking first so there is an active entry to pause.
        service.onStartCommand(startTrackingIntent(), 0, 1)

        val pausedIntent = Intent(
            ApplicationProvider.getApplicationContext(),
            TimeTrackingNotificationService::class.java,
        ).apply {
            action = TimeTrackingNotificationService.ACTION_SHOW_PAUSED
        }
        service.onStartCommand(pausedIntent, 0, 2)

        val notificationManager = shadowOf(
            ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(NotificationManager::class.java),
        )
        val posted = notificationManager.getNotification(1001)
        assertNotNull("Expected the paused notification to be posted via NotificationManager", posted)
        checkNotNull(posted)

        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, posted.visibility)
        val publicVersion = posted.publicVersion
        assertNotNull(publicVersion)
        checkNotNull(publicVersion)

        val publicTitle = publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        assertEquals("Timer paused", publicTitle)
        assertFalse(publicTitle.contains(secretDescription))
        assertFalse(publicTitle.contains(secretProject))

        val publicText = publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertTrue(publicText.isBlank())
    }
}

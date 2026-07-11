/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.sync

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * SV-022: logout must be able to cancel any queued/in-flight sync work before the account
 * cache is cleared, so a stray sync can't re-insert the outgoing account's rows afterward.
 */
@RunWith(RobolectricTestRunner::class)
class SyncSchedulerCancelTest {
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
    }

    @Test fun cancelSync_cancels_queued_unique_outbox_sync_work() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Enqueue work directly under the same unique name SyncScheduler uses, so this test
        // doesn't depend on requestSync()'s constraints (e.g. network) being satisfiable here.
        // A long initial delay keeps it ENQUEUED (Robolectric's synchronous executor would
        // otherwise run the real SyncWorker to completion before cancelSync() is even called).
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SyncScheduler.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)

        SyncScheduler(context).cancelSync()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(SyncScheduler.UNIQUE_NAME).get()
        assertEquals(1, infos.size)
        assertTrue(infos.first().state.isFinished)
        assertEquals(WorkInfo.State.CANCELLED, infos.first().state)
    }

    @Test fun cancelSync_is_a_no_op_when_nothing_is_queued() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Must not throw when there's no unique work under this name (e.g. logging out
        // without ever having triggered a sync).
        SyncScheduler(context).cancelSync()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(SyncScheduler.UNIQUE_NAME).get()
        assertTrue(infos.isEmpty())
    }
}

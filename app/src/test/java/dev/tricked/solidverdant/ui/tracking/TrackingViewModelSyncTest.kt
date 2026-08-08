/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.remote.FakeRemoteDataSource
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackingViewModelSyncTest {
    @Test fun server_null_is_only_masked_by_an_unsynced_start() {
        val pendingStart = TimeEntryRepository.SyncOperation(
            entryId = "entry",
            type = OutboxOpType.START,
            status = TimeEntryRepository.EntrySyncStatus.PENDING,
            attemptCount = 0,
            error = null,
        )
        val retryingStop = pendingStart.copy(
            type = OutboxOpType.STOP,
            status = TimeEntryRepository.EntrySyncStatus.RETRYING,
            attemptCount = 1,
        )

        assertTrue(shouldPreserveLocallyStartedEntry("entry", listOf(pendingStart)))
        assertFalse(shouldPreserveLocallyStartedEntry("entry", listOf(retryingStop)))
    }

    @Test fun server_active_is_masked_only_while_its_stop_is_in_flight() {
        val pendingStop = TimeEntryRepository.SyncOperation(
            entryId = "entry",
            type = OutboxOpType.STOP,
            status = TimeEntryRepository.EntrySyncStatus.PENDING,
            attemptCount = 0,
            error = null,
        )

        assertTrue(shouldDeferServerActiveWhileStopping("entry", listOf(pendingStop)))
        assertTrue(
            shouldDeferServerActiveWhileStopping(
                "entry",
                listOf(pendingStop.copy(status = TimeEntryRepository.EntrySyncStatus.RETRYING)),
            ),
        )
        assertFalse(
            shouldDeferServerActiveWhileStopping(
                "entry",
                listOf(pendingStop.copy(status = TimeEntryRepository.EntrySyncStatus.FAILED)),
            ),
        )
    }

    @Test fun start_from_repository_yields_optimistic_local_entry() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repo = TimeEntryRepository(
            db.timeEntryDao(),
            db.catalogDao(),
            db.outboxDao(),
            db.syncMetaDao(),
            FakeRemoteDataSource(),
            object : Clock {
                override fun nowMs() = 1L
            },
            Json { encodeDefaults = true },
            db,
        )
        val entry = repo.startEntry("org1", "m", "u", null, null, "hi", emptyList())
        assertTrue(entry.id.startsWith("local-"))
        assertTrue(repo.observeActiveEntry("org1").first()?.id == entry.id)
        db.close()
    }
}

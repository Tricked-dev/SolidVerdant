/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncMetaDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: SyncMetaDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.syncMetaDao()
    }

    @After fun teardown() = db.close()

    @Test fun stampFullSync_inserts_when_absent() = runTest {
        dao.stampFullSync("org1", 100)
        val row = dao.get("org1")!!
        assertEquals(100L, row.lastFullSyncAtMs)
        assertNull(row.lastPushAtMs)
    }

    @Test fun stampPush_inserts_when_absent() = runTest {
        dao.stampPush("org1", 200)
        val row = dao.get("org1")!!
        assertEquals(200L, row.lastPushAtMs)
        // No pull has happened yet; the pull timestamp stays at its sentinel default of 0.
        assertEquals(0L, row.lastFullSyncAtMs)
    }

    @Test fun stampPush_does_not_clobber_full_sync() = runTest {
        dao.stampFullSync("org1", 100)
        dao.stampPush("org1", 200)
        val row = dao.get("org1")!!
        assertEquals(100L, row.lastFullSyncAtMs)
        assertEquals(200L, row.lastPushAtMs)
    }

    @Test fun stampFullSync_does_not_clobber_push() = runTest {
        dao.stampPush("org1", 200)
        dao.stampFullSync("org1", 100)
        val row = dao.get("org1")!!
        assertEquals(100L, row.lastFullSyncAtMs)
        assertEquals(200L, row.lastPushAtMs)
    }

    @Test fun observe_emits_updates() = runTest {
        dao.observe("org1").test {
            assertNull(awaitItem())
            dao.stampFullSync("org1", 100)
            assertEquals(100L, awaitItem()!!.lastFullSyncAtMs)
            dao.stampPush("org1", 200)
            val updated = awaitItem()!!
            assertEquals(100L, updated.lastFullSyncAtMs)
            assertEquals(200L, updated.lastPushAtMs)
        }
    }
}

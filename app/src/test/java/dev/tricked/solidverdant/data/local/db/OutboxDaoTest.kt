/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OutboxDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: OutboxDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.outboxDao()
    }

    @After fun teardown() = db.close()

    private fun op(entryId: String, type: OutboxOpType = OutboxOpType.START) = OutboxEntity(
        opType = type,
        organizationId = "org1",
        timeEntryId = entryId,
        payloadJson = "{}",
        createdAtMs = 1L,
    )

    @Test fun peek_returns_insertion_order() = runTest {
        dao.insert(op("a"))
        dao.insert(op("b"))
        assertEquals(listOf("a", "b"), dao.peekAll().map { it.timeEntryId })
    }

    @Test fun rekey_references_updates_matching_rows() = runTest {
        dao.insert(op("local-1", OutboxOpType.START))
        dao.insert(op("local-1", OutboxOpType.STOP))
        dao.rekeyReferences("local-1", "server-1")
        assertEquals(listOf("server-1", "server-1"), dao.peekAll().map { it.timeEntryId })
    }

    @Test fun reset_failed_for_retry_only_revives_dead_letters_in_selected_organization() = runTest {
        dao.insert(
            op("failed-current").copy(
                attemptCount = 5,
                lastError = "Server rejected this change",
                deadLettered = true,
            ),
        )
        dao.insert(op("pending-current").copy(attemptCount = 2, lastError = "Temporary error"))
        dao.insert(
            op("failed-other").copy(
                organizationId = "org2",
                attemptCount = 5,
                lastError = "Server rejected this change",
                deadLettered = true,
            ),
        )

        assertEquals(1, dao.resetFailedForRetry("org1"))

        val rows = dao.peekAll().associateBy { it.timeEntryId }
        rows.getValue("failed-current").let { revived ->
            assertFalse(revived.deadLettered)
            assertEquals(0, revived.attemptCount)
            assertNull(revived.lastError)
        }
        rows.getValue("pending-current").let { pending ->
            assertFalse(pending.deadLettered)
            assertEquals(2, pending.attemptCount)
            assertEquals("Temporary error", pending.lastError)
        }
        assertTrue(rows.getValue("failed-other").deadLettered)
    }
}

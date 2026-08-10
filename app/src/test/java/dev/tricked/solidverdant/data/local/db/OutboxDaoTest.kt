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

    @Test fun pending_drain_excludes_dead_letters_but_keeps_retryable_operations_in_order() = runTest {
        dao.insert(op("first").copy(attemptCount = 2, lastError = "temporary"))
        dao.insert(op("dead").copy(deadLettered = true, attemptCount = 5))
        dao.insert(op("last"))

        assertEquals(listOf("first", "last"), dao.peekPending().map { it.timeEntryId })
        assertEquals(listOf("first", "dead", "last"), dao.peekAll().map { it.timeEntryId })
    }

    @Test fun cancel_latest_delete_removes_only_the_newest_delete_for_that_entry() = runTest {
        dao.insert(op("entry", OutboxOpType.UPDATE))
        dao.insert(op("entry", OutboxOpType.DELETE))
        dao.insert(op("other", OutboxOpType.DELETE))
        dao.insert(op("entry", OutboxOpType.DELETE))

        assertEquals(1, dao.cancelLatestDelete("entry"))

        val rows = dao.peekAll()
        assertEquals(1, rows.count { it.timeEntryId == "entry" && it.opType == OutboxOpType.DELETE })
        assertEquals(1, rows.count { it.timeEntryId == "entry" && it.opType == OutboxOpType.UPDATE })
        assertEquals(1, rows.count { it.timeEntryId == "other" && it.opType == OutboxOpType.DELETE })
    }

    @Test fun reset_for_retry_revives_every_operation_for_only_the_selected_entry() = runTest {
        dao.insert(op("selected").copy(attemptCount = 5, lastError = "failed", deadLettered = true))
        dao.insert(op("selected", OutboxOpType.STOP).copy(attemptCount = 2, lastError = "temporary"))
        dao.insert(op("other").copy(attemptCount = 5, lastError = "failed", deadLettered = true))

        assertEquals(2, dao.resetForRetry("selected"))

        val selected = dao.peekAll().filter { it.timeEntryId == "selected" }
        assertTrue(selected.all { !it.deadLettered && it.attemptCount == 0 && it.lastError == null })
        assertTrue(dao.peekAll().single { it.timeEntryId == "other" }.deadLettered)
    }

    @Test fun dead_letter_cascade_marks_only_eligible_operations_for_the_entry() = runTest {
        dao.insert(op("selected").copy(attemptCount = 1))
        dao.insert(op("selected", OutboxOpType.STOP).copy(attemptCount = 2))
        dao.insert(op("selected", OutboxOpType.UPDATE).copy(deadLettered = true, lastError = "old"))
        dao.insert(op("other"))

        assertEquals(2, dao.deadLetterByEntryId("selected", "create failed"))

        val rows = dao.peekAll()
        assertTrue(rows.filter { it.timeEntryId == "selected" }.all { it.deadLettered })
        assertEquals(
            "old",
            rows.single { it.timeEntryId == "selected" && it.opType == OutboxOpType.UPDATE }.lastError,
        )
        assertFalse(rows.single { it.timeEntryId == "other" }.deadLettered)
    }

    @Test fun newer_pending_count_ignores_older_and_dead_lettered_operations() = runTest {
        val currentId = dao.insert(op("entry", OutboxOpType.UPDATE))
        dao.insert(op("entry", OutboxOpType.STOP).copy(deadLettered = true))
        dao.insert(op("other", OutboxOpType.UPDATE))
        dao.insert(op("entry", OutboxOpType.DELETE))

        assertEquals(1, dao.countNewerPending("entry", currentId))
        assertEquals(0, dao.countNewerPending("entry", Long.MAX_VALUE))
    }

    @Test fun only_newer_update_or_delete_supersedes_older_metadata_update() = runTest {
        val currentId = dao.insert(op("entry", OutboxOpType.UPDATE))
        dao.insert(op("entry", OutboxOpType.STOP))

        assertEquals(0, dao.countNewerContentMutations("entry", currentId))

        dao.insert(op("entry", OutboxOpType.UPDATE))
        assertEquals(1, dao.countNewerContentMutations("entry", currentId))

        dao.insert(op("entry", OutboxOpType.DELETE))
        assertEquals(2, dao.countNewerContentMutations("entry", currentId))
    }

    @Test fun oldest_base_snapshot_survives_later_operations_and_rekey() = runTest {
        dao.insert(op("local-1").copy(baseSnapshotJson = "oldest"))
        dao.insert(op("local-1", OutboxOpType.STOP).copy(baseSnapshotJson = "newer"))
        dao.insert(op("local-1", OutboxOpType.UPDATE).copy(baseSnapshotJson = null))

        assertEquals("oldest", dao.oldestBaseSnapshot("local-1"))
        dao.rekeyReferences("local-1", "server-1")
        assertEquals("oldest", dao.oldestBaseSnapshot("server-1"))
        assertNull(dao.oldestBaseSnapshot("local-1"))
    }

    @Test fun pending_create_detection_and_entry_deletion_cover_the_whole_local_chain() = runTest {
        dao.insert(op("local-1", OutboxOpType.START))
        dao.insert(op("local-1", OutboxOpType.STOP))
        dao.insert(op("other", OutboxOpType.CREATE))

        assertTrue(dao.hasPendingCreateOrStart("local-1"))
        assertFalse(dao.hasPendingCreateOrStart("missing"))

        dao.deleteByTimeEntryId("local-1")

        assertFalse(dao.hasPendingCreateOrStart("local-1"))
        assertEquals(listOf("other"), dao.peekAll().map { it.timeEntryId })
    }

    @Test fun discarding_failed_sync_keeps_retryable_work_for_the_same_entry() = runTest {
        dao.insert(op("entry").copy(deadLettered = true))
        dao.insert(op("entry", OutboxOpType.STOP).copy(attemptCount = 1))

        assertEquals(1, dao.deleteDeadLetteredByEntryId("entry"))

        val remaining = dao.peekAll().single()
        assertEquals(OutboxOpType.STOP, remaining.opType)
        assertFalse(remaining.deadLettered)
    }
}

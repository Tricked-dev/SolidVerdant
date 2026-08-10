/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.sync.ConflictSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimeEntryDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: TimeEntryDao
    private val json = Json { encodeDefaults = true }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.timeEntryDao()
    }

    @After fun teardown() = db.close()

    private fun entry(id: String, org: String = "org1", end: String? = "2026-01-01T10:00:00Z") = TimeEntryEntity(
        id = id, description = "d", userId = "u", start = "2026-01-01T09:00:00Z",
        end = end, duration = 3600, taskId = null, projectId = null,
        billable = false, organizationId = org, updatedAt = 1L,
        syncState = SyncState.SYNCED, pendingDelete = false,
    )

    @Test fun upsert_and_observe_visible_excludes_pending_delete() = runTest {
        dao.upsert(entry("a"))
        dao.upsert(entry("b").copy(pendingDelete = true))
        val visible = dao.observeVisibleEntries("org1").first()
        assertEquals(listOf("a"), visible.map { it.id })
    }

    @Test fun observe_active_returns_entry_with_null_end() = runTest {
        dao.upsert(entry("a", end = "2026-01-01T10:00:00Z"))
        dao.upsert(entry("b", end = null))
        assertEquals("b", dao.observeActive("org1").first()?.id)
    }

    @Test fun rekey_moves_row_to_new_id() = runTest {
        dao.upsert(entry("local-1"))
        dao.rekey("local-1", "server-1")
        assertNull(dao.getById("local-1"))
        assertEquals("server-1", dao.getById("server-1")?.id)
    }

    @Test fun replace_tag_refs_round_trips() = runTest {
        dao.upsert(entry("a"))
        dao.replaceTagRefs("a", listOf("t1", "t2"))
        assertEquals(setOf("t1", "t2"), dao.tagIdsFor("a").toSet())
        dao.replaceTagRefs("a", listOf("t3"))
        assertEquals(listOf("t3"), dao.tagIdsFor("a"))
    }

    @Test fun active_queries_are_organization_scoped_choose_newest_and_exclude_pending_delete() = runTest {
        dao.upsert(entry("older-active", end = null).copy(start = "2026-01-01T09:00:00Z"))
        dao.upsert(entry("newer-active", end = null).copy(start = "2026-01-01T10:00:00Z"))
        dao.upsert(
            entry("hidden-active", end = null).copy(
                start = "2026-01-01T11:00:00Z",
                pendingDelete = true,
            ),
        )
        dao.upsert(entry("other-org-active", org = "org2", end = null).copy(start = "2026-01-01T12:00:00Z"))

        assertEquals("newer-active", dao.observeActive("org1").first()?.id)
        assertEquals("newer-active", dao.getActive("org1")?.id)
        assertEquals("other-org-active", dao.getActive("org2")?.id)
    }

    @Test fun rekey_moves_tag_references_with_the_entry() = runTest {
        dao.upsert(entry("local-1"))
        dao.replaceTagRefs("local-1", listOf("tag-1", "tag-2"))

        dao.rekey("local-1", "server-1")

        assertTrue(dao.tagIdsFor("local-1").isEmpty())
        assertEquals(setOf("tag-1", "tag-2"), dao.tagIdsFor("server-1").toSet())
    }

    @Test fun server_pull_replaces_synced_row_and_its_tag_set() = runTest {
        dao.upsert(entry("server-1").copy(description = "cached"))
        dao.replaceTagRefs("server-1", listOf("old-tag"))
        val server = entry("server-1").copy(description = "server", updatedAt = 20L)

        dao.applyServerEntries(
            entries = listOf(server),
            tagIdsByEntry = mapOf(server.id to listOf("new-tag-1", "new-tag-2")),
        )

        assertEquals("server", dao.getById(server.id)?.description)
        assertEquals(setOf("new-tag-1", "new-tag-2"), dao.tagIdsFor(server.id).toSet())
    }

    @Test fun server_pull_without_a_base_never_clobbers_pending_local_content_or_tags() = runTest {
        val pending = entry("server-1").copy(description = "local edit", syncState = SyncState.PENDING)
        dao.upsert(pending)
        dao.replaceTagRefs(pending.id, listOf("local-tag"))

        dao.applyServerEntries(
            entries = listOf(entry(pending.id).copy(description = "older server copy")),
            tagIdsByEntry = mapOf(pending.id to listOf("server-tag")),
        )

        assertEquals(pending, dao.getById(pending.id))
        assertEquals(listOf("local-tag"), dao.tagIdsFor(pending.id))
    }

    @Test fun pending_edit_stays_pending_when_server_still_matches_its_base_snapshot() = runTest {
        val pending = entry("server-1").copy(description = "local edit", syncState = SyncState.PENDING)
        val server = entry(pending.id).copy(description = "original server copy")
        dao.upsert(pending)
        dao.replaceTagRefs(pending.id, listOf("local-tag"))
        val base = json.encodeToString(
            ConflictSnapshot.of(
                start = server.start,
                end = server.end,
                description = server.description,
                projectId = server.projectId,
                taskId = server.taskId,
                billable = server.billable,
                tagIds = listOf("server-tag"),
            ),
        )

        dao.applyServerEntries(
            entries = listOf(server),
            tagIdsByEntry = mapOf(server.id to listOf("server-tag")),
            baseSnapshotsByEntry = mapOf(server.id to base),
        )

        assertEquals(pending, dao.getById(pending.id))
        assertEquals(listOf("local-tag"), dao.tagIdsFor(pending.id))
    }

    @Test fun diverged_server_copy_marks_conflict_without_destroying_local_content_or_tags() = runTest {
        val pending = entry("server-1").copy(description = "local edit", syncState = SyncState.PENDING)
        dao.upsert(pending)
        dao.replaceTagRefs(pending.id, listOf("local-tag"))
        val base = json.encodeToString(
            ConflictSnapshot.of(
                start = pending.start,
                end = pending.end,
                description = "original server copy",
                projectId = null,
                taskId = null,
                billable = false,
                tagIds = listOf("old-server-tag"),
            ),
        )
        val changedServer = entry(pending.id).copy(description = "changed elsewhere")

        dao.applyServerEntries(
            entries = listOf(changedServer),
            tagIdsByEntry = mapOf(changedServer.id to listOf("new-server-tag")),
            baseSnapshotsByEntry = mapOf(changedServer.id to base),
        )

        val stored = requireNotNull(dao.getById(pending.id))
        assertEquals("local edit", stored.description)
        assertEquals(SyncState.CONFLICT, stored.syncState)
        assertEquals(listOf("local-tag"), dao.tagIdsFor(pending.id))
        val serverSnapshot = json.decodeFromString<TimeEntry>(requireNotNull(stored.conflictServerJson))
        assertEquals("changed elsewhere", serverSnapshot.description)
        assertEquals(listOf("new-server-tag"), serverSnapshot.tags.map { it.id })
    }

    @Test fun in_flight_pull_does_not_overwrite_a_newer_synced_local_write() = runTest {
        val newerLocal = entry("server-1").copy(description = "newer local ack", updatedAt = 200L)
        dao.upsert(newerLocal)

        dao.applyServerEntries(
            entries = listOf(entry(newerLocal.id).copy(description = "stale response", updatedAt = 201L)),
            tagIdsByEntry = mapOf(newerLocal.id to emptyList()),
            pullStartedAtMs = 100L,
        )

        assertEquals(newerLocal, dao.getById(newerLocal.id))
    }

    @Test fun tombstone_removes_only_missing_synced_rows_owned_by_the_fetched_window() = runTest {
        val insideMissing = entry("inside-missing").copy(start = "2026-01-05T09:00:00Z")
        val insidePresent = entry("inside-present").copy(start = "2026-01-06T09:00:00Z")
        val pending = entry("pending").copy(start = "2026-01-07T09:00:00Z", syncState = SyncState.PENDING)
        val pendingDelete = entry("pending-delete").copy(start = "2026-01-08T09:00:00Z", pendingDelete = true)
        val outboxOwned = entry("outbox-owned").copy(start = "2026-01-09T09:00:00Z")
        val outside = entry("outside").copy(start = "2026-02-01T09:00:00Z")
        val otherOrg = entry("other-org", org = "org2").copy(start = "2026-01-05T09:00:00Z")
        dao.upsertAll(listOf(insideMissing, insidePresent, pending, pendingDelete, outboxOwned, outside, otherOrg))
        db.outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = "org1",
                timeEntryId = outboxOwned.id,
                payloadJson = "{}",
                createdAtMs = 1L,
            ),
        )

        dao.tombstoneMissing(
            orgId = "org1",
            rangeStart = "2026-01-01T00:00:00Z",
            rangeEnd = "2026-01-31T23:59:59Z",
            serverIds = listOf(insidePresent.id),
        )

        assertNull(dao.getById(insideMissing.id))
        listOf(insidePresent, pending, pendingDelete, outboxOwned, outside, otherOrg).forEach {
            assertNotNull("${it.id} must be preserved", dao.getById(it.id))
        }
    }
}

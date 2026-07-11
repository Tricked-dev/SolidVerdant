/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.OutboxEntity
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.local.db.SyncState
import dev.tricked.solidverdant.data.local.db.TemplateEntity
import dev.tricked.solidverdant.data.local.db.TimeEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserCacheCleanerTest {
    private lateinit var db: AppDatabase
    private lateinit var cleaner: UserCacheCleaner

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        cleaner = UserCacheCleaner(context, SettingsDataStore(context), db)
    }

    @After fun teardown() = db.close()

    private fun timeEntry(id: String) = TimeEntryEntity(
        id = id, description = "d", userId = "u", start = "2026-01-01T09:00:00Z",
        end = "2026-01-01T10:00:00Z", duration = 3600, taskId = null, projectId = null,
        billable = false, organizationId = "org", updatedAt = 1L,
        syncState = SyncState.SYNCED, pendingDelete = false,
    )

    private fun outboxOp(entryId: String) = OutboxEntity(
        opType = OutboxOpType.START,
        organizationId = "org",
        timeEntryId = entryId,
        payloadJson = "{}",
        createdAtMs = 1L,
    )

    private fun template(id: String, endpoint: String?, userId: String?) = TemplateEntity(
        id = id, organizationId = "org", name = id, projectId = null, taskId = null,
        description = null, tagIds = "", billable = false, isFavorite = false,
        sortOrder = 0, createdAtMs = 1L, ownerEndpoint = endpoint, ownerUserId = userId,
    )

    @Test fun clear_wipes_other_tables_but_keeps_templates() = runTest {
        db.timeEntryDao().upsert(timeEntry("e1"))
        db.outboxDao().insert(outboxOp("e1"))
        db.templateDao().upsert(template("t1", "https://a.example", "userA"))

        cleaner.clear()

        assertTrue(db.timeEntryDao().observeVisibleEntries("org").first().isEmpty())
        assertTrue(db.outboxDao().peekAll().isEmpty())
        assertNotNull(db.templateDao().getById("t1"))
    }

    @Test fun clear_sweeps_future_tables_by_default() = runTest {
        val raw = db.openHelper.writableDatabase
        raw.execSQL("CREATE TABLE IF NOT EXISTS zz_future(id TEXT PRIMARY KEY)")
        raw.execSQL("INSERT INTO zz_future(id) VALUES ('x')")

        cleaner.clear()

        raw.query("SELECT COUNT(*) FROM zz_future").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test fun clear_preserves_template_visibility_per_account() = runTest {
        val endpoint = "https://a.example"
        db.templateDao().upsert(template("t1", endpoint, "userA"))

        cleaner.clear()

        val visibleToA = db.templateDao().observeTemplates("org", endpoint, "userA").first()
        assertEquals(listOf("t1"), visibleToA.map { it.id })

        val visibleToB = db.templateDao().observeTemplates("org", endpoint, "userB").first()
        assertTrue(visibleToB.isEmpty())
    }
}

package dev.tricked.solidverdant.data.local.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the explicit Room migrations that replaced the previous destructive fallback so that
 * unsynced outbox operations and cached data survive schema upgrades.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun openHelper(version: Int, onCreate: (SupportSQLiteDatabase) -> Unit): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test fun migration_1_2_creates_clients_table() {
        val helper = openHelper(1) { /* v1 had no clients table */ }
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_1_2.migrate(db)

        // Table (and its org index) now usable.
        db.execSQL("INSERT INTO clients (id, name, isArchived, organizationId) VALUES ('c1', 'Acme', 0, 'org1')")
        db.query("SELECT name FROM clients WHERE id = 'c1'").use { c ->
            c.moveToFirst()
            assertEquals("Acme", c.getString(0))
        }
        helper.close()
    }

    @Test fun migration_2_3_adds_idempotency_and_dead_letter_columns_with_defaults() {
        val helper = openHelper(2) { db ->
            db.execSQL(
                "CREATE TABLE outbox (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "opType TEXT NOT NULL, organizationId TEXT NOT NULL, timeEntryId TEXT NOT NULL, " +
                    "payloadJson TEXT NOT NULL, createdAtMs INTEGER NOT NULL, " +
                    "attemptCount INTEGER NOT NULL, lastError TEXT)"
            )
            db.execSQL(
                "INSERT INTO outbox (opType, organizationId, timeEntryId, payloadJson, createdAtMs, attemptCount) " +
                    "VALUES ('START', 'org1', 'local-1', '{}', 1, 0)"
            )
        }
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_2_3.migrate(db)

        // Pre-existing row keeps sane defaults for the new columns.
        db.query("SELECT clientId, deadLettered FROM outbox WHERE timeEntryId = 'local-1'").use { c ->
            c.moveToFirst()
            assertEquals("", c.getString(0))
            assertEquals(0, c.getInt(1))
        }
        helper.close()
    }

    @Test fun migration_3_4_creates_review_loop_tables() {
        val helper = openHelper(3) { /* v3 had neither review-loop table */ }
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_3_4.migrate(db)

        // entry_templates: nullable metadata columns accept NULL; non-null columns round-trip.
        db.execSQL(
            "INSERT INTO entry_templates " +
                "(id, organizationId, name, projectId, taskId, description, tagIds, billable, " +
                "isFavorite, sortOrder, createdAtMs) " +
                "VALUES ('t1', 'org1', NULL, NULL, NULL, NULL, '', 0, 1, 0, 42)"
        )
        db.query(
            "SELECT organizationId, tagIds, isFavorite, createdAtMs FROM entry_templates WHERE id = 't1'"
        ).use { c ->
            c.moveToFirst()
            assertEquals("org1", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals(1, c.getInt(2))
            assertEquals(42L, c.getLong(3))
        }

        // inbox_dismissals keyed by issueKey.
        db.execSQL(
            "INSERT INTO inbox_dismissals (issueKey, organizationId, dismissedAtMs) " +
                "VALUES ('overlap:v1:abc', 'org1', 7)"
        )
        db.query("SELECT organizationId, dismissedAtMs FROM inbox_dismissals WHERE issueKey = 'overlap:v1:abc'").use { c ->
            c.moveToFirst()
            assertEquals("org1", c.getString(0))
            assertEquals(7L, c.getLong(1))
        }
        helper.close()
    }
}

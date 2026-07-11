/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.util.ShortcutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Clears account-specific caches that live outside the main snapshot cache. */
@Singleton
class UserCacheCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val database: AppDatabase,
) {
    suspend fun clear() = withContext(Dispatchers.IO) {
        settingsDataStore.clearCachedData()
        clearAllTablesExceptTemplates()
        // apply() is safe here: nothing after this reads the prefs back, and we're already
        // off the main thread (Dispatchers.IO), so there's no ordering requirement forcing
        // a synchronous commit().
        context.getSharedPreferences(TILE_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        ShortcutManager.clearShortcuts(context)
    }

    /**
     * Wipes every user table on logout EXCEPT `entry_templates`, which is preserved so favorites
     * survive per-account (retention #787).
     *
     * The sweep is list-based on purpose: it enumerates the live tables and deletes everything
     * *not* in the keep-set, so any table added in a future schema is cleared by default — the
     * safe direction. Only `entry_templates` is deliberately kept. Preserving templates across a
     * logout is safe cross-account because [TemplateDao] scopes every read to the owning account
     * (endpoint + userId, Task 3.2): account B never sees account A's surviving templates, while a
     * returning account A sees its own again.
     *
     * Mirrors Room's own [androidx.room.RoomDatabase.clearAllTables]: it defers foreign keys inside
     * a single transaction (via the raw `PRAGMA defer_foreign_keys` rather than Room's
     * `runInTransaction` wrapper, which would fight the PRAGMA) and skips SQLite/Room bookkeeping
     * tables (`sqlite_*` — which also covers `sqlite_sequence` — `android_metadata`,
     * `room_master_table`).
     */
    private fun clearAllTablesExceptTemplates() {
        val db = database.openHelper.writableDatabase
        val keep = setOf("entry_templates")
        val tables = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
        }
        db.beginTransaction()
        try {
            db.execSQL("PRAGMA defer_foreign_keys = TRUE")
            tables
                .filter {
                    it !in keep &&
                        !it.startsWith("sqlite_") &&
                        !it.startsWith("android_") &&
                        it != "room_master_table"
                }
                .forEach { db.execSQL("DELETE FROM `$it`") }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val TILE_STATE_PREFERENCES = "tile_state"
    }
}

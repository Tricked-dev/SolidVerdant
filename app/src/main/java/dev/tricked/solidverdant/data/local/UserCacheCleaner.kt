/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tricked.solidverdant.util.ShortcutManager
import dev.tricked.solidverdant.data.local.db.AppDatabase
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
        database.clearAllTables()
        context.getSharedPreferences(TILE_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ShortcutManager.clearShortcuts(context)
    }

    private companion object {
        const val TILE_STATE_PREFERENCES = "tile_state"
    }
}

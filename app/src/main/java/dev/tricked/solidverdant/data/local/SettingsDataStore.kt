/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore for app settings
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.settingsDataStore

    companion object {
        private val ALWAYS_SHOW_NOTIFICATION = booleanPreferencesKey("always_show_notification")
    }

    /**
     * Flow that emits whether to always show notifications
     */
    val alwaysShowNotification: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ALWAYS_SHOW_NOTIFICATION] ?: false // Default to false
    }

    /**
     * Set whether to always show notifications
     */
    suspend fun setAlwaysShowNotification(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[ALWAYS_SHOW_NOTIFICATION] = enabled
        }
    }
}

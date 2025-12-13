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
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        private val WIDGET_IS_TRACKING = booleanPreferencesKey("widget_is_tracking")
        private val WIDGET_START_TIME = longPreferencesKey("widget_start_time")
        private val WIDGET_PROJECT_NAME = stringPreferencesKey("widget_project_name")
        private val WIDGET_TASK_NAME = stringPreferencesKey("widget_task_name")
        private val WIDGET_DESCRIPTION = stringPreferencesKey("widget_description")

        @Volatile
        private var INSTANCE: SettingsDataStore? = null

        /**
         * Get singleton instance (for use in widgets where Hilt injection is not available)
         */
        fun getInstance(context: Context): SettingsDataStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsDataStore(context.applicationContext).also { INSTANCE = it }
            }
        }
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

    /**
     * Widget state data class
     */
    data class WidgetState(
        val isTracking: Boolean = false,
        val startTimeEpochMillis: Long = 0,
        val projectName: String? = null,
        val taskName: String? = null,
        val description: String? = null
    )

    /**
     * Flow that emits the current widget state
     */
    val widgetState: Flow<WidgetState> = dataStore.data.map { preferences ->
        WidgetState(
            isTracking = preferences[WIDGET_IS_TRACKING] ?: false,
            startTimeEpochMillis = preferences[WIDGET_START_TIME] ?: 0,
            projectName = preferences[WIDGET_PROJECT_NAME],
            taskName = preferences[WIDGET_TASK_NAME],
            description = preferences[WIDGET_DESCRIPTION]
        )
    }

    /**
     * Update widget state when tracking starts
     */
    suspend fun setWidgetTrackingState(
        isTracking: Boolean,
        startTimeEpochMillis: Long = 0,
        projectName: String? = null,
        taskName: String? = null,
        description: String? = null
    ) {
        dataStore.edit { preferences ->
            preferences[WIDGET_IS_TRACKING] = isTracking
            preferences[WIDGET_START_TIME] = startTimeEpochMillis
            if (projectName != null) {
                preferences[WIDGET_PROJECT_NAME] = projectName
            } else {
                preferences.remove(WIDGET_PROJECT_NAME)
            }
            if (taskName != null) {
                preferences[WIDGET_TASK_NAME] = taskName
            } else {
                preferences.remove(WIDGET_TASK_NAME)
            }
            if (description != null) {
                preferences[WIDGET_DESCRIPTION] = description
            } else {
                preferences.remove(WIDGET_DESCRIPTION)
            }
        }
    }
}

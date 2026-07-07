/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    NEO,
}

/**
 * DataStore for app settings
 */
@Singleton
class SettingsDataStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dataStore = context.settingsDataStore
    private val immediateCache = context.getSharedPreferences("immediate_ui_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // Warm the SharedPreferences file on a background thread so the synchronous
        // getCachedAppTheme() read during MainActivity.onCreate (first frame) is served from the
        // in-memory map instead of blocking the main thread on the initial disk load.
        Thread {
            @Suppress("UNUSED_VARIABLE")
            val warm = immediateCache.all
        }.apply {
            name = "settings-cache-warmup"
            priority = Thread.MIN_PRIORITY
            isDaemon = true
            start()
        }
    }

    companion object {
        private const val CONTINUE_ENTRY_JSON = "continue_entry_json"
        private const val USER_JSON = "user_json"
        private const val MEMBERSHIPS_JSON = "memberships_json"
        private const val CURRENT_MEMBERSHIP_ID = "current_membership_id"
        private const val CACHED_APP_THEME = "app_theme"
        private const val TRACKING_STATE_JSON = "tracking_state_json"
        private const val REVIEW_BADGE_COUNT_PREFIX = "review_badge_count_"
        private val ALWAYS_SHOW_NOTIFICATION = booleanPreferencesKey("always_show_notification")
        private val APP_THEME = stringPreferencesKey("app_theme")
        private val OPTIMISTIC_REFRESH = booleanPreferencesKey("optimistic_refresh")
        private val LONG_TIMER_HOURS = intPreferencesKey("long_timer_hours")
        private val WIDGET_IS_TRACKING = booleanPreferencesKey("widget_is_tracking")
        private val WIDGET_START_TIME = longPreferencesKey("widget_start_time")
        private val WIDGET_PROJECT_NAME = stringPreferencesKey("widget_project_name")
        private val WIDGET_TASK_NAME = stringPreferencesKey("widget_task_name")
        private val WIDGET_DESCRIPTION = stringPreferencesKey("widget_description")

        // --- Phase 2 review-loop preferences (local only; survive logout like other prefs) ---
        private val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val REMINDER_MINUTE_OF_DAY = intPreferencesKey("reminder_minute_of_day")
        private val END_OF_DAY_REVIEW_ENABLED = booleanPreferencesKey("end_of_day_review_enabled")
        private val CALENDAR_OVERLAY_ENABLED = booleanPreferencesKey("calendar_overlay_enabled")
        private val SELECTED_CALENDAR_IDS = stringSetPreferencesKey("selected_calendar_ids")

        /** Default reminder time: 17:00 local, expressed as minutes since midnight. */
        const val DEFAULT_REMINDER_MINUTE_OF_DAY: Int = 17 * 60

        @Volatile
        private var instance: SettingsDataStore? = null

        /**
         * Get singleton instance (for use in widgets where Hilt injection is not available)
         */
        fun getInstance(context: Context): SettingsDataStore = instance ?: synchronized(this) {
            instance ?: SettingsDataStore(context.applicationContext).also { instance = it }
        }
    }

    /** Read synchronously so the continue card can exist on the first rendered frame. */
    fun getCachedContinueEntry(): TimeEntry? = immediateCache.getString(CONTINUE_ENTRY_JSON, null)?.let { encoded ->
        runCatching { json.decodeFromString<TimeEntry>(encoded) }.getOrNull()
    }

    fun cacheContinueEntry(entry: TimeEntry?) {
        immediateCache.edit().apply {
            if (entry == null) {
                remove(CONTINUE_ENTRY_JSON)
            } else {
                putString(CONTINUE_ENTRY_JSON, json.encodeToString(entry))
            }
        }.apply()
    }

    data class CachedAuth(val user: User, val memberships: List<Membership>, val currentMembershipId: String?)

    fun getCachedAuth(): CachedAuth? = runCatching {
        val user = json.decodeFromString<User>(immediateCache.getString(USER_JSON, null) ?: return null)
        val memberships = json.decodeFromString<List<Membership>>(
            immediateCache.getString(MEMBERSHIPS_JSON, null) ?: return null,
        )
        CachedAuth(user, memberships, immediateCache.getString(CURRENT_MEMBERSHIP_ID, null))
    }.getOrNull()

    fun cacheAuth(user: User, memberships: List<Membership>, currentMembershipId: String?) {
        immediateCache.edit()
            .putString(USER_JSON, json.encodeToString(user))
            .putString(MEMBERSHIPS_JSON, json.encodeToString(memberships))
            .apply {
                if (currentMembershipId == null) {
                    remove(CURRENT_MEMBERSHIP_ID)
                } else {
                    putString(CURRENT_MEMBERSHIP_ID, currentMembershipId)
                }
            }
            .apply()
    }

    fun cacheCurrentMembership(id: String) {
        immediateCache.edit().putString(CURRENT_MEMBERSHIP_ID, id).apply()
    }

    fun getCachedAppTheme(): AppThemeMode = immediateCache.getString(CACHED_APP_THEME, null)
        ?.let { stored -> AppThemeMode.entries.find { it.name == stored } }
        ?: AppThemeMode.SYSTEM

    @Serializable
    data class CachedTrackingState(
        val organizationId: String,
        val timeEntries: List<TimeEntry>,
        val projects: List<Project>,
        val clients: List<dev.tricked.solidverdant.data.model.Client> = emptyList(),
        val tasks: List<Task>,
        val tags: List<Tag>,
        val activeEntry: TimeEntry?,
        val overlapCount: Int = 0,
    )

    fun getCachedTrackingState(): CachedTrackingState? = immediateCache.getString(TRACKING_STATE_JSON, null)?.let { encoded ->
        runCatching { json.decodeFromString<CachedTrackingState>(encoded) }.getOrNull()
    }

    fun cacheTrackingState(state: CachedTrackingState) {
        immediateCache.edit()
            .putString(TRACKING_STATE_JSON, json.encodeToString(state))
            .apply()
    }

    fun getCachedReviewBadgeCount(organizationId: String): Int = immediateCache.getInt(REVIEW_BADGE_COUNT_PREFIX + organizationId, 0)

    fun cacheReviewBadgeCount(organizationId: String, count: Int) {
        immediateCache.edit()
            .putInt(REVIEW_BADGE_COUNT_PREFIX + organizationId, count.coerceAtLeast(0))
            .apply()
    }

    /** Clear cached account data while preserving the user's app preferences. */
    suspend fun clearCachedData() {
        immediateCache.edit().clear().commit()
        dataStore.edit(::clearWidgetState)
    }

    private fun clearWidgetState(preferences: MutablePreferences) {
        preferences.remove(WIDGET_IS_TRACKING)
        preferences.remove(WIDGET_START_TIME)
        preferences.remove(WIDGET_PROJECT_NAME)
        preferences.remove(WIDGET_TASK_NAME)
        preferences.remove(WIDGET_DESCRIPTION)
    }

    /**
     * Flow that emits whether to always show notifications
     */
    val alwaysShowNotification: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ALWAYS_SHOW_NOTIFICATION] ?: false // Default to false
    }.distinctUntilChanged()

    val appTheme: Flow<AppThemeMode> = dataStore.data.map { preferences ->
        preferences[APP_THEME]
            ?.let { stored -> AppThemeMode.entries.find { it.name == stored } }
            ?: AppThemeMode.SYSTEM
    }.distinctUntilChanged()

    val optimisticRefresh: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[OPTIMISTIC_REFRESH] ?: true
    }.distinctUntilChanged()

    val longTimerHours: Flow<Int> = dataStore.data.map { it[LONG_TIMER_HOURS] ?: 4 }.distinctUntilChanged()

    suspend fun getAppTheme(): AppThemeMode = appTheme.first()

    /**
     * Set whether to always show notifications
     */
    suspend fun setAlwaysShowNotification(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[ALWAYS_SHOW_NOTIFICATION] = enabled
        }
    }

    suspend fun setAppTheme(theme: AppThemeMode) {
        immediateCache.edit().putString(CACHED_APP_THEME, theme.name).apply()
        dataStore.edit { preferences -> preferences[APP_THEME] = theme.name }
    }

    suspend fun setOptimisticRefresh(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[OPTIMISTIC_REFRESH] = enabled }
    }

    suspend fun setLongTimerHours(hours: Int) {
        require(hours in 1..24)
        dataStore.edit { it[LONG_TIMER_HOURS] = hours }
    }

    // --- Phase 2 review-loop preferences ---

    /** Whether tracking reminders are enabled. Defaults to false (opt-in). */
    val reminderEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[REMINDER_ENABLED] ?: false
    }.distinctUntilChanged()

    /** Reminder time expressed as minutes since local midnight (0..1439). */
    val reminderMinuteOfDay: Flow<Int> = dataStore.data.map { preferences ->
        preferences[REMINDER_MINUTE_OF_DAY] ?: DEFAULT_REMINDER_MINUTE_OF_DAY
    }.distinctUntilChanged()

    /** Whether the optional end-of-day review notification is enabled. Defaults to false. */
    val endOfDayReviewEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[END_OF_DAY_REVIEW_ENABLED] ?: false
    }.distinctUntilChanged()

    /** Whether the device-calendar overlay is enabled. Defaults to false (requires permission). */
    val calendarOverlayEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[CALENDAR_OVERLAY_ENABLED] ?: false
    }.distinctUntilChanged()

    /** IDs of the device calendars the user selected for the overlay. Defaults to empty. */
    val selectedCalendarIds: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[SELECTED_CALENDAR_IDS] ?: emptySet()
    }.distinctUntilChanged()

    suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderMinuteOfDay(minuteOfDay: Int) {
        require(minuteOfDay in 0..1439)
        dataStore.edit { it[REMINDER_MINUTE_OF_DAY] = minuteOfDay }
    }

    suspend fun setEndOfDayReviewEnabled(enabled: Boolean) {
        dataStore.edit { it[END_OF_DAY_REVIEW_ENABLED] = enabled }
    }

    suspend fun setCalendarOverlayEnabled(enabled: Boolean) {
        dataStore.edit { it[CALENDAR_OVERLAY_ENABLED] = enabled }
    }

    suspend fun setSelectedCalendarIds(ids: Set<String>) {
        dataStore.edit { it[SELECTED_CALENDAR_IDS] = ids }
    }

    /**
     * Widget state data class
     */
    data class WidgetState(
        val isTracking: Boolean = false,
        val startTimeEpochMillis: Long = 0,
        val projectName: String? = null,
        val taskName: String? = null,
        val description: String? = null,
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
            description = preferences[WIDGET_DESCRIPTION],
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
        description: String? = null,
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

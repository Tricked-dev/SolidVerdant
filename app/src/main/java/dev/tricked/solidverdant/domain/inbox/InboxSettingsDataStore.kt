/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.domain.inbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

private const val MINUTE_OF_DAY_START = 0
private const val MINUTE_OF_DAY_END = 1440
private const val MIN_GAP_MINUTES = 1
private const val MAX_GAP_MINUTES = 24 * 60

private val Context.inboxDataStore: DataStore<Preferences> by preferencesDataStore(name = "inbox_settings")

/**
 * Local, device-scoped configuration for the Time Inbox checks (gap analysis #17).
 *
 * Working hours and the minimum gap are not present in the Solidtime API, so they live here rather
 * than being pushed to the server. These preferences behave like the app's other UI preferences:
 * they are not account data and are intentionally preserved across logout. [maxDurationHours] is not
 * stored here — the inbox reuses the existing "long timer" preference so the running-timer warning
 * and the inbox agree on what "too long" means.
 */
@Singleton
class InboxSettingsDataStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dataStore = context.inboxDataStore

    val settings: Flow<InboxSettings> = dataStore.data.map { prefs ->
        InboxSettings(
            workDays = prefs[WORK_DAYS]
                ?.mapNotNull { it.toIntOrNull()?.let(::dayOrNull) }
                ?.toSet()
                ?: DEFAULT.workDays,
            workStartMinute = (prefs[WORK_START] ?: DEFAULT.workStartMinute).coerceIn(MINUTE_OF_DAY_START, MINUTE_OF_DAY_END),
            workEndMinute = (prefs[WORK_END] ?: DEFAULT.workEndMinute).coerceIn(MINUTE_OF_DAY_START, MINUTE_OF_DAY_END),
            minGapMinutes = (prefs[MIN_GAP] ?: DEFAULT.minGapMinutes).coerceIn(MIN_GAP_MINUTES, MAX_GAP_MINUTES),
            checkGaps = prefs[CHECK_GAPS] ?: DEFAULT.checkGaps,
            checkOverlaps = prefs[CHECK_OVERLAPS] ?: DEFAULT.checkOverlaps,
            checkMissingProject = prefs[CHECK_MISSING_PROJECT] ?: DEFAULT.checkMissingProject,
            checkMissingTask = prefs[CHECK_MISSING_TASK] ?: DEFAULT.checkMissingTask,
            checkMissingDescription = prefs[CHECK_MISSING_DESCRIPTION] ?: DEFAULT.checkMissingDescription,
            checkMissingTags = prefs[CHECK_MISSING_TAGS] ?: DEFAULT.checkMissingTags,
            checkLongDuration = prefs[CHECK_LONG_DURATION] ?: DEFAULT.checkLongDuration,
            // Absent key = null: "Everything" once chosen, or the pre-choice default when not.
            horizonStartMs = prefs[HORIZON_START_MS],
            horizonChosen = prefs[HORIZON_CHOSEN] ?: DEFAULT.horizonChosen,
        )
    }.distinctUntilChanged()

    suspend fun setWorkDays(days: Set<DayOfWeek>) {
        dataStore.edit { it[WORK_DAYS] = days.map { day -> day.value.toString() }.toSet() }
    }

    suspend fun setWorkWindow(startMinute: Int, endMinute: Int) {
        require(startMinute in MINUTE_OF_DAY_START..MINUTE_OF_DAY_END && endMinute in MINUTE_OF_DAY_START..MINUTE_OF_DAY_END)
        dataStore.edit {
            it[WORK_START] = startMinute
            it[WORK_END] = endMinute
        }
    }

    suspend fun setMinGapMinutes(minutes: Int) {
        require(minutes in MIN_GAP_MINUTES..MAX_GAP_MINUTES)
        dataStore.edit { it[MIN_GAP] = minutes }
    }

    suspend fun setCheckEnabled(check: InboxCheck, enabled: Boolean) {
        dataStore.edit { it[check.key] = enabled }
    }

    /**
     * Record the user's inbox horizon choice (SV-005). Marks the horizon as chosen so the analyzer
     * stops clamping to today. A null [startMs] means "Everything" (still bounded by the analyzer's
     * 370-day gap cap) and removes the stored bound rather than persisting a sentinel.
     */
    suspend fun setHorizonStart(startMs: Long?) {
        dataStore.edit { prefs ->
            prefs[HORIZON_CHOSEN] = true
            if (startMs == null) prefs.remove(HORIZON_START_MS) else prefs[HORIZON_START_MS] = startMs
        }
    }

    /** Test-only: wipe all persisted inbox preferences so a case can start from defaults. */
    @androidx.annotation.VisibleForTesting
    internal suspend fun clearForTest() {
        dataStore.edit { it.clear() }
    }

    private fun dayOrNull(value: Int): DayOfWeek? = DayOfWeek.values().firstOrNull { it.value == value }

    /** Identifies each toggleable check so the UI can flip it without a setter per boolean. */
    enum class InboxCheck(internal val key: Preferences.Key<Boolean>) {
        GAPS(CHECK_GAPS),
        OVERLAPS(CHECK_OVERLAPS),
        MISSING_PROJECT(CHECK_MISSING_PROJECT),
        MISSING_TASK(CHECK_MISSING_TASK),
        MISSING_DESCRIPTION(CHECK_MISSING_DESCRIPTION),
        MISSING_TAGS(CHECK_MISSING_TAGS),
        LONG_DURATION(CHECK_LONG_DURATION),
    }

    companion object {
        private val DEFAULT = InboxSettings()

        private val WORK_DAYS = stringSetPreferencesKey("inbox_work_days")
        private val WORK_START = intPreferencesKey("inbox_work_start_minute")
        private val WORK_END = intPreferencesKey("inbox_work_end_minute")
        private val MIN_GAP = intPreferencesKey("inbox_min_gap_minutes")
        private val CHECK_GAPS = booleanPreferencesKey("inbox_check_gaps")
        private val CHECK_OVERLAPS = booleanPreferencesKey("inbox_check_overlaps")
        private val CHECK_MISSING_PROJECT = booleanPreferencesKey("inbox_check_missing_project")
        private val CHECK_MISSING_TASK = booleanPreferencesKey("inbox_check_missing_task")
        private val CHECK_MISSING_DESCRIPTION = booleanPreferencesKey("inbox_check_missing_description")
        private val CHECK_MISSING_TAGS = booleanPreferencesKey("inbox_check_missing_tags")
        private val CHECK_LONG_DURATION = booleanPreferencesKey("inbox_check_long_duration")
        private val HORIZON_START_MS = longPreferencesKey("inbox_horizon_start_ms")
        private val HORIZON_CHOSEN = booleanPreferencesKey("inbox_horizon_chosen")
    }
}

/**
 * The stored slice of inbox configuration. Combined with the shared long-timer preference to form
 * the full [InboxCheckConfig] the analyzer consumes.
 */
data class InboxSettings(
    val workDays: Set<DayOfWeek> = InboxCheckConfig().workDays,
    val workStartMinute: Int = InboxCheckConfig().workStartMinute,
    val workEndMinute: Int = InboxCheckConfig().workEndMinute,
    val minGapMinutes: Int = InboxCheckConfig().minGapMinutes,
    val checkGaps: Boolean = InboxCheckConfig().checkGaps,
    val checkOverlaps: Boolean = InboxCheckConfig().checkOverlaps,
    val checkMissingProject: Boolean = InboxCheckConfig().checkMissingProject,
    val checkMissingTask: Boolean = InboxCheckConfig().checkMissingTask,
    val checkMissingDescription: Boolean = InboxCheckConfig().checkMissingDescription,
    val checkMissingTags: Boolean = InboxCheckConfig().checkMissingTags,
    val checkLongDuration: Boolean = InboxCheckConfig().checkLongDuration,
    /**
     * SV-005 inbox horizon (device-scoped). The effective lower bound is derived by
     * [resolveHorizonStartMs]; these two fields are the raw stored choice only.
     * - [horizonChosen] false (default) → analyzer clamps to today (backlog hidden pre-choice).
     * - [horizonChosen] true, [horizonStartMs] null → "Everything" (bounded by the 370-day gap cap).
     * - [horizonChosen] true, [horizonStartMs] set → issues since that instant.
     */
    val horizonStartMs: Long? = null,
    val horizonChosen: Boolean = false,
) {
    fun toConfig(maxDurationHours: Int): InboxCheckConfig = InboxCheckConfig(
        workDays = workDays,
        workStartMinute = workStartMinute,
        workEndMinute = workEndMinute,
        minGapMinutes = minGapMinutes,
        maxDurationHours = maxDurationHours,
        checkGaps = checkGaps,
        checkOverlaps = checkOverlaps,
        checkMissingProject = checkMissingProject,
        checkMissingTask = checkMissingTask,
        checkMissingDescription = checkMissingDescription,
        checkMissingTags = checkMissingTags,
        checkLongDuration = checkLongDuration,
    )
}

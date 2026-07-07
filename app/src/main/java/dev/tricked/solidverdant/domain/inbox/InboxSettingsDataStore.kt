package dev.tricked.solidverdant.domain.inbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

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
class InboxSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.inboxDataStore

    val settings: Flow<InboxSettings> = dataStore.data.map { prefs ->
        InboxSettings(
            workDays = prefs[WORK_DAYS]
                ?.mapNotNull { it.toIntOrNull()?.let(::dayOrNull) }
                ?.toSet()
                ?: DEFAULT.workDays,
            workStartMinute = (prefs[WORK_START] ?: DEFAULT.workStartMinute).coerceIn(0, 1440),
            workEndMinute = (prefs[WORK_END] ?: DEFAULT.workEndMinute).coerceIn(0, 1440),
            minGapMinutes = (prefs[MIN_GAP] ?: DEFAULT.minGapMinutes).coerceIn(1, 24 * 60),
            checkGaps = prefs[CHECK_GAPS] ?: DEFAULT.checkGaps,
            checkOverlaps = prefs[CHECK_OVERLAPS] ?: DEFAULT.checkOverlaps,
            checkMissingProject = prefs[CHECK_MISSING_PROJECT] ?: DEFAULT.checkMissingProject,
            checkMissingTask = prefs[CHECK_MISSING_TASK] ?: DEFAULT.checkMissingTask,
            checkMissingDescription = prefs[CHECK_MISSING_DESCRIPTION] ?: DEFAULT.checkMissingDescription,
            checkMissingTags = prefs[CHECK_MISSING_TAGS] ?: DEFAULT.checkMissingTags,
            checkLongDuration = prefs[CHECK_LONG_DURATION] ?: DEFAULT.checkLongDuration,
        )
    }.distinctUntilChanged()

    suspend fun setWorkDays(days: Set<DayOfWeek>) {
        dataStore.edit { it[WORK_DAYS] = days.map { day -> day.value.toString() }.toSet() }
    }

    suspend fun setWorkWindow(startMinute: Int, endMinute: Int) {
        require(startMinute in 0..1440 && endMinute in 0..1440)
        dataStore.edit {
            it[WORK_START] = startMinute
            it[WORK_END] = endMinute
        }
    }

    suspend fun setMinGapMinutes(minutes: Int) {
        require(minutes in 1..24 * 60)
        dataStore.edit { it[MIN_GAP] = minutes }
    }

    suspend fun setCheckEnabled(check: InboxCheck, enabled: Boolean) {
        dataStore.edit { it[check.key] = enabled }
    }

    private fun dayOrNull(value: Int): DayOfWeek? =
        DayOfWeek.values().firstOrNull { it.value == value }

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

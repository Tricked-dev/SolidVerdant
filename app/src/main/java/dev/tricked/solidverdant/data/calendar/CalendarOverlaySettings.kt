/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.calendar

import dev.tricked.solidverdant.data.local.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Narrow seam over the calendar-overlay preferences held in [SettingsDataStore]. These are
 * device-local, opt-in preferences that survive logout (see FEATURE_GAP_ANALYSIS.md #77); only the
 * selected calendar IDs and the on/off flag are stored — never event content.
 *
 * Extracting the interface keeps [dev.tricked.solidverdant.ui.calendar.CalendarViewModel] unit
 * testable without a DataStore/Context.
 */
interface CalendarOverlaySettings {
    val calendarOverlayEnabled: Flow<Boolean>
    val selectedCalendarIds: Flow<Set<String>>
    suspend fun setCalendarOverlayEnabled(enabled: Boolean)
    suspend fun setSelectedCalendarIds(ids: Set<String>)

    /** Calendar grid preferences share this local seam so CalendarViewModel remains unit-testable. */
    val calendarSnapMinutes: Flow<Int>
        get() = flowOf(15)
    val calendarStartHour: Flow<Int>
        get() = flowOf(0)
    val calendarEndHour: Flow<Int>
        get() = flowOf(24)
    val calendarDensity: Flow<String>
        get() = flowOf("COMFORTABLE")
    suspend fun setCalendarSnapMinutes(minutes: Int) = Unit
    suspend fun setCalendarHours(startHour: Int, endHour: Int) = Unit
    suspend fun setCalendarDensity(density: String) = Unit
}

/** DataStore-backed [CalendarOverlaySettings] delegating to the shared [SettingsDataStore]. */
class SettingsCalendarOverlaySettings @Inject constructor(private val settings: SettingsDataStore) : CalendarOverlaySettings {
    override val calendarOverlayEnabled: Flow<Boolean> = settings.calendarOverlayEnabled
    override val selectedCalendarIds: Flow<Set<String>> = settings.selectedCalendarIds
    override val calendarSnapMinutes: Flow<Int> = settings.calendarSnapMinutes
    override val calendarStartHour: Flow<Int> = settings.calendarStartHour
    override val calendarEndHour: Flow<Int> = settings.calendarEndHour
    override val calendarDensity: Flow<String> = settings.calendarDensity
    override suspend fun setCalendarOverlayEnabled(enabled: Boolean) = settings.setCalendarOverlayEnabled(enabled)
    override suspend fun setSelectedCalendarIds(ids: Set<String>) = settings.setSelectedCalendarIds(ids)
    override suspend fun setCalendarSnapMinutes(minutes: Int) = settings.setCalendarSnapMinutes(minutes)
    override suspend fun setCalendarHours(startHour: Int, endHour: Int) = settings.setCalendarHours(startHour, endHour)
    override suspend fun setCalendarDensity(density: String) = settings.setCalendarDensity(density)
}

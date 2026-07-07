/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.calendar

import dev.tricked.solidverdant.data.local.SettingsDataStore
import kotlinx.coroutines.flow.Flow
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
}

/** DataStore-backed [CalendarOverlaySettings] delegating to the shared [SettingsDataStore]. */
class SettingsCalendarOverlaySettings @Inject constructor(
    private val settings: SettingsDataStore,
) : CalendarOverlaySettings {
    override val calendarOverlayEnabled: Flow<Boolean> = settings.calendarOverlayEnabled
    override val selectedCalendarIds: Flow<Set<String>> = settings.selectedCalendarIds
    override suspend fun setCalendarOverlayEnabled(enabled: Boolean) =
        settings.setCalendarOverlayEnabled(enabled)
    override suspend fun setSelectedCalendarIds(ids: Set<String>) =
        settings.setSelectedCalendarIds(ids)
}

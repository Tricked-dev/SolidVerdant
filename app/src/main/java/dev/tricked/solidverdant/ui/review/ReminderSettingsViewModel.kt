/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.reminder.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [ReminderSettingsScreen]. Reads/writes the foundation DataStore keys (reminderEnabled,
 * endOfDayReviewEnabled, reminderMinuteOfDay) and re-evaluates the WorkManager schedule after every
 * change so the reminder is enqueued, re-anchored, or cancelled to match (gap analysis #4, #78).
 */
@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(private val settings: SettingsDataStore, private val scheduler: ReminderScheduler) :
    ViewModel() {

    data class State(
        val loading: Boolean = true,
        val reminderEnabled: Boolean = false,
        val endOfDayReviewEnabled: Boolean = false,
        val minuteOfDay: Int = SettingsDataStore.DEFAULT_REMINDER_MINUTE_OF_DAY,
    ) {
        /** Whether any reminder is active, i.e. whether the time setting is meaningful. */
        val anyEnabled: Boolean get() = reminderEnabled || endOfDayReviewEnabled
    }

    val state: StateFlow<State> = combine(
        settings.reminderEnabled,
        settings.endOfDayReviewEnabled,
        settings.reminderMinuteOfDay,
    ) { reminder, review, minute ->
        State(
            loading = false,
            reminderEnabled = reminder,
            endOfDayReviewEnabled = review,
            minuteOfDay = minute,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_STOP_TIMEOUT_MS), State())

    fun setReminderEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setReminderEnabled(enabled)
        scheduler.reschedule()
    }

    fun setEndOfDayReviewEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setEndOfDayReviewEnabled(enabled)
        scheduler.reschedule()
    }

    fun setReminderTime(hour: Int, minute: Int) = viewModelScope.launch {
        val minuteOfDay = (hour * MINUTES_PER_HOUR + minute).coerceIn(MINUTE_OF_DAY_START, MAX_MINUTE_OF_DAY)
        settings.setReminderMinuteOfDay(minuteOfDay)
        scheduler.reschedule()
    }
}

private const val STATE_STOP_TIMEOUT_MS = 5_000L
private const val MINUTES_PER_HOUR = 60
private const val MINUTE_OF_DAY_START = 0
private const val MAX_MINUTE_OF_DAY = 1439

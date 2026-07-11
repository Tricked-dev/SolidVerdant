/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import dev.tricked.solidverdant.data.calendar.CalendarEventSource
import dev.tricked.solidverdant.data.calendar.CalendarOverlaySettings
import dev.tricked.solidverdant.data.calendar.DeviceCalendar
import dev.tricked.solidverdant.data.calendar.DeviceCalendarEvent
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.TimeEntryReader
import dev.tricked.solidverdant.domain.time.TemporalPolicy
import dev.tricked.solidverdant.domain.time.TemporalPolicyProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeReader(private val entries: List<TimeEntry>) : TimeEntryReader {
        override fun observeTimeEntries(organizationId: String): Flow<List<TimeEntry>> = flowOf(entries)
    }

    private class FakeEventSource(
        private val calendars: List<DeviceCalendar> = emptyList(),
        private val events: List<DeviceCalendarEvent> = emptyList(),
    ) : CalendarEventSource {
        var lastRange: Pair<Long, Long>? = null
        var queryCount = 0
        override suspend fun queryCalendars(): List<DeviceCalendar> = calendars
        override suspend fun queryEvents(calendarIds: Set<String>, rangeStartMs: Long, rangeEndMs: Long): List<DeviceCalendarEvent> {
            queryCount++
            lastRange = rangeStartMs to rangeEndMs
            return events.filter { it.calendarId in calendarIds }
        }
    }

    private class FakeOverlaySettings(enabled: Boolean = false, selected: Set<String> = emptySet()) : CalendarOverlaySettings {
        val enabledState = MutableStateFlow(enabled)
        val selectedState = MutableStateFlow(selected)
        override val calendarOverlayEnabled: Flow<Boolean> = enabledState
        override val selectedCalendarIds: Flow<Set<String>> = selectedState
        override suspend fun setCalendarOverlayEnabled(enabled: Boolean) {
            enabledState.value = enabled
        }
        override suspend fun setSelectedCalendarIds(ids: Set<String>) {
            selectedState.value = ids
        }
    }

    private fun entry(id: String, start: String, dur: Int) = TimeEntry(
        id = id,
        userId = "u",
        start = start,
        end = null,
        duration = dur,
        organizationId = "org1",
    )

    // Device-zone + Monday policy, matching the pre-policy ZoneId.systemDefault() /
    // WeekFields(MONDAY) behaviour these bucket/day-count assertions were written against.
    private fun policyProvider(
        policy: TemporalPolicy = TemporalPolicy(java.time.ZoneId.systemDefault(), java.time.DayOfWeek.MONDAY),
    ): TemporalPolicyProvider = mockk {
        every { this@mockk.policy } returns kotlinx.coroutines.flow.flowOf(policy)
        coEvery { current() } returns policy
    }

    private fun vm(
        reader: TimeEntryReader,
        source: CalendarEventSource = FakeEventSource(),
        settings: CalendarOverlaySettings = FakeOverlaySettings(),
        temporalPolicyProvider: TemporalPolicyProvider = policyProvider(),
    ) = CalendarViewModel(reader, source, settings, temporalPolicyProvider)

    @Test
    fun buildsBucketsAndTotalsPerDay() = runTest {
        val model = vm(
            FakeReader(
                listOf(
                    entry("a", "2026-07-06T09:00:00Z", 3600),
                    entry("b", "2026-07-06T11:00:00Z", 1800),
                    entry("c", "2026-07-07T09:00:00Z", 600),
                ),
            ),
        )
        model.setOrganization("org1")
        val loaded = model.uiState.first { it.bucketsByDate.isNotEmpty() }
        val day6 = loaded.bucketsByDate[LocalDate.of(2026, 7, 6)]!!
        assertEquals(2, day6.entries.size)
        assertEquals(5400L, day6.totalSeconds)
    }

    @Test
    fun monthNavigationMovesVisibleMonth() = runTest {
        val model = vm(FakeReader(emptyList()))
        model.setOrganization("org1")
        val start = model.uiState.value.visibleMonth
        model.nextMonth()
        assertEquals(start.plusMonths(1), model.uiState.value.visibleMonth)
        model.previousMonth()
        model.previousMonth()
        assertEquals(start.minusMonths(1), model.uiState.value.visibleMonth)
    }

    @Test
    fun defaultsToWeekViewWithSevenVisibleDays() = runTest {
        val model = vm(FakeReader(emptyList()))
        val state = model.uiState.value
        assertEquals(CalendarViewMode.WEEK, state.viewMode)
        assertEquals(7, state.visibleDays.size)
    }

    @Test
    fun dayViewShowsSingleVisibleDay() = runTest {
        val model = vm(FakeReader(emptyList()))
        model.setViewMode(CalendarViewMode.DAY)
        assertEquals(1, model.uiState.value.visibleDays.size)
    }

    @Test
    fun overlayIsNotQueriedWithoutPermission() = runTest {
        val source = FakeEventSource(events = listOf(event(1, "2026-07-06T09:00:00Z", "2026-07-06T10:00:00Z")))
        val model = vm(
            FakeReader(emptyList()),
            source = source,
            settings = FakeOverlaySettings(enabled = true, selected = setOf("1")),
        )
        model.setOrganization("org1")
        // Overlay enabled + calendar selected, but the READ_CALENDAR grant is still missing.
        assertNull(source.lastRange)
        assertTrue(model.uiState.value.overlayEvents.isEmpty())
    }

    @Test
    fun overlayQueriesEventsWhenEnabledPermittedAndSelected() = runTest {
        val source = FakeEventSource(
            events = listOf(event(1, "2026-07-06T09:00:00Z", "2026-07-06T10:00:00Z", cal = "1")),
        )
        val model = vm(
            FakeReader(emptyList()),
            source = source,
            settings = FakeOverlaySettings(enabled = true, selected = setOf("1")),
        )
        model.setOrganization("org1")
        model.selectDate(LocalDate.of(2026, 7, 6))
        model.onCalendarPermissionChanged(true)
        val state = model.uiState.first { it.overlayEvents.isNotEmpty() }
        assertEquals(1, state.overlayEvents.size)
    }

    private fun event(id: Long, startIso: String, endIso: String, allDay: Boolean = false, cal: String = "1") = DeviceCalendarEvent(
        instanceId = id,
        eventId = id,
        calendarId = cal,
        title = "e$id",
        startUtcMs = java.time.Instant.parse(startIso).toEpochMilli(),
        endUtcMs = java.time.Instant.parse(endIso).toEpochMilli(),
        allDay = allDay,
        colorArgb = null,
    )
}

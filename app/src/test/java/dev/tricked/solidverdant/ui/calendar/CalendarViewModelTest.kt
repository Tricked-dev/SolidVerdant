package dev.tricked.solidverdant.ui.calendar

import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.repository.TimeEntryReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun entry(id: String, start: String, dur: Int) = TimeEntry(
        id = id, userId = "u", start = start, end = null, duration = dur, organizationId = "org1",
    )

    @Test
    fun buildsBucketsAndTotalsPerDay() = runTest {
        val vm = CalendarViewModel(
            FakeReader(
                listOf(
                    entry("a", "2026-07-06T09:00:00Z", 3600),
                    entry("b", "2026-07-06T11:00:00Z", 1800),
                    entry("c", "2026-07-07T09:00:00Z", 600),
                )
            )
        )
        vm.setOrganization("org1")
        // UnconfinedTestDispatcher runs the flowOf collection eagerly.
        val loaded = vm.uiState.value
        val day6 = loaded.bucketsByDate[LocalDate.of(2026, 7, 6)]!!
        assertEquals(2, day6.entries.size)
        assertEquals(5400L, day6.totalSeconds)
    }

    @Test
    fun monthNavigationMovesVisibleMonth() = runTest {
        val vm = CalendarViewModel(FakeReader(emptyList()))
        vm.setOrganization("org1")
        val start = vm.uiState.value.visibleMonth
        vm.nextMonth()
        assertEquals(start.plusMonths(1), vm.uiState.value.visibleMonth)
        vm.previousMonth(); vm.previousMonth()
        assertEquals(start.minusMonths(1), vm.uiState.value.visibleMonth)
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.flows

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.e2e.BackendPortable
import dev.tricked.solidverdant.e2e.E2eFixture
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** User-visible coverage for one entry whose interval contributes to three local calendar days. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MultiDayEntryE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @BackendPortable
    @Test
    fun calendarShowsEntryOnEverySpannedDayWithClippedTotals() {
        val fixture = multiDayFixture(e2e.session.zone)
        e2e.prepare(E2eFixture.Completed(fixture.entry))
        e2e.launchApp()
        TrackRobot(e2e.composeRule).waitForHistory().assertEntryVisible(fixture.entry.description!!)

        e2e.composeRule.onAllNodes(hasTestTag("main_nav_calendar"), useUnmergedTree = true)
            .onFirst()
            .performClick()
        e2e.composeRule.onNodeWithTag(TestTags.CALENDAR_MODE_MONTH, useUnmergedTree = true)
            .performClick()

        assertCalendarCellTotal(fixture.startDate, "1h 00m")
        assertCalendarCellTotal(fixture.startDate.plusDays(1), "24h 00m")
        assertCalendarCellTotal(fixture.startDate.plusDays(2), "1h 00m")
    }

    @BackendPortable
    @Test
    fun trackRangeNamesBothEndpointDates() {
        val fixture = multiDayFixture(e2e.session.zone)
        val handle = e2e.prepare(E2eFixture.Completed(fixture.entry))
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule)
            .waitForHistory()
            .assertEntryVisible(fixture.entry.description!!)

        val formatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
        robot.assertEntryTimeRangeContains(
            entryId = requireNotNull(handle.serverId),
            startText = fixture.startDate.format(formatter),
            endText = fixture.startDate.plusDays(2).format(formatter),
        )
    }

    @Test
    fun editSheetExposesIndependentStartAndEndDates() {
        val fixture = multiDayFixture(e2e.session.zone)
        e2e.prepare(E2eFixture.Completed(fixture.entry))
        e2e.launchApp()
        TrackRobot(e2e.composeRule)
            .waitForHistory()
            .assertEntryVisible(fixture.entry.description!!)
            .tapFirstEntryEdit()

        e2e.composeRule.onNodeWithTag(TestTags.TRACK_SHEET_START_DATE, useUnmergedTree = true)
            .assertIsDisplayed()
        e2e.composeRule.onNodeWithTag(TestTags.TRACK_SHEET_END_DATE, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @BackendPortable
    @Test
    fun shorteningMultiDayEntryToOneDaySyncsWithSolidtimeUtcTimestamps() {
        val zone = e2e.session.zone
        val startDate = testMonth(zone).atDay(1)
        val start = startDate.atTime(10, 0).atZone(zone).toInstant()
        val originalEnd = start.plusSeconds(48 * 60 * 60L)
        val entry = TimeEntry(
            id = ENTRY_ID,
            description = "Two-day deployment",
            userId = e2e.session.userId,
            start = start.toString(),
            end = originalEnd.toString(),
            duration = 48 * 60 * 60,
            organizationId = e2e.session.organizationId,
        )
        val handle = e2e.prepare(E2eFixture.Completed(entry))
        e2e.launchApp()

        TrackRobot(e2e.composeRule)
            .waitForHistory()
            .assertEntryVisible(entry.description!!)
            .tapFirstEntryEdit()
            .changeSheetEndDate(startDate.plusDays(1))
            .saveSheet()

        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            current.entry(handle)?.let { persisted ->
                persisted.start == start.toString() &&
                    persisted.end == start.plusSeconds(24 * 60 * 60L).toString() &&
                    persisted.duration == 24 * 60 * 60
            } == true
        }
        val persisted = requireNotNull(snapshot.entry(handle))
        assertEquals(start.toString(), persisted.start)
        assertEquals(start.plusSeconds(24 * 60 * 60L).toString(), persisted.end)
        assertEquals(24 * 60 * 60, persisted.duration)
    }

    @BackendPortable
    @Test
    fun shorteningOneAndAHalfDayEntryToOneDayByDurationSyncsWithSolidtime() {
        val zone = e2e.session.zone
        val startDate = testMonth(zone).atDay(1)
        val start = startDate.atTime(10, 0).atZone(zone).toInstant()
        val entry = TimeEntry(
            id = ENTRY_ID,
            description = "One-and-a-half-day deployment",
            userId = e2e.session.userId,
            start = start.toString(),
            end = start.plusSeconds(36 * 60 * 60L).toString(),
            duration = 36 * 60 * 60,
            organizationId = e2e.session.organizationId,
        )
        val handle = e2e.prepare(E2eFixture.Completed(entry))
        e2e.launchApp()

        TrackRobot(e2e.composeRule)
            .waitForHistory()
            .assertEntryVisible(entry.description!!)
            .tapFirstEntryEdit()
            .replaceSheetDuration((24 * 60).toString())
            .saveSheet()

        val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
            current.entry(handle)?.let { persisted ->
                persisted.start == start.toString() &&
                    persisted.end == start.plusSeconds(24 * 60 * 60L).toString() &&
                    persisted.duration == 24 * 60 * 60
            } == true
        }
        val persisted = requireNotNull(snapshot.entry(handle))
        assertEquals(start.toString(), persisted.start)
        assertEquals(start.plusSeconds(24 * 60 * 60L).toString(), persisted.end)
        assertEquals(24 * 60 * 60, persisted.duration)
    }

    @Test
    fun delayedRefreshCannotRestoreTheOldDurationAfterEditSyncs() {
        val mock = e2e.requireMockBackend()
        val zone = e2e.session.zone
        val startDate = testMonth(zone).atDay(1)
        val start = startDate.atTime(10, 0).atZone(zone).toInstant()
        val oldEnd = start.plusSeconds(36 * 60 * 60L)
        val newEnd = start.plusSeconds(24 * 60 * 60L)
        val entry = TimeEntry(
            id = ENTRY_ID,
            description = "Refresh race deployment",
            userId = e2e.session.userId,
            start = start.toString(),
            end = oldEnd.toString(),
            duration = 36 * 60 * 60,
            organizationId = e2e.session.organizationId,
        )
        val handle = e2e.prepare(E2eFixture.Completed(entry))
        e2e.launchApp()
        val robot = TrackRobot(e2e.composeRule)
            .waitForHistory()
            .assertEntryVisible(entry.description!!)

        val historyCallsBeforeRefresh = mock.callsMatching("GET", "/time-entries").size
        val delayedResponseGate = mock.delayNextTimeEntriesResponseUntilReleased()
        try {
            robot.tapRefresh()
            e2e.composeRule.waitUntil(WAIT_MS) {
                mock.callsMatching("GET", "/time-entries").size > historyCallsBeforeRefresh
            }

            robot.tapFirstEntryEdit()
                .replaceSheetDuration((24 * 60).toString())
                .saveSheet()

            val snapshot = e2e.awaitServer(WAIT_MS, driveSync = true) { current ->
                current.entry(handle)?.let { persisted ->
                    persisted.end == newEnd.toString() && persisted.duration == 24 * 60 * 60
                } == true
            }
            assertEquals(newEnd.toString(), snapshot.entry(handle)?.end)
            e2e.composeRule.waitUntil(WAIT_MS) { e2e.pendingOutboxCount() == 0 }

            // Release the deliberately stale response only after the server write has completed.
            delayedResponseGate.countDown()
            e2e.composeRule.waitUntil(WAIT_MS) {
                e2e.localEntry(handle)?.end == newEnd.toString()
            }
            val newEndTime = newEnd.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
            e2e.composeRule.onAllNodes(
                hasTestTag(TestTags.trackEntryTimeRange(requireNotNull(handle.serverId))) and
                    hasText(newEndTime, substring = true),
                useUnmergedTree = true,
            ).onFirst().assertIsDisplayed()
        } finally {
            delayedResponseGate.countDown()
        }
    }

    private fun assertCalendarCellTotal(day: LocalDate, total: String) {
        // The clickable day cell merges its descendants into one semantics node on real devices.
        // Match that stable merged node directly instead of walking an unmerged ancestor chain,
        // which can both miss merged text and read SnapshotStateObserver from the test thread.
        val matcher = hasTestTag("day-cell-$day") and hasText(total, substring = true)
        e2e.composeRule.waitUntilAtLeastOneExists(matcher, WAIT_MS)
        e2e.composeRule.onAllNodes(matcher).onFirst().assertIsDisplayed()
    }

    private fun multiDayFixture(zone: ZoneId): MultiDayFixture {
        val startDate = testMonth(zone).atDay(1)
        val start = startDate.atTime(23, 0).atZone(zone).toInstant()
        val end = start.plusSeconds(MULTI_DAY_SECONDS.toLong())
        return MultiDayFixture(
            startDate = startDate,
            entry = TimeEntry(
                id = ENTRY_ID,
                description = "Three-day deployment",
                userId = e2e.session.userId,
                start = start.toString(),
                end = end.toString(),
                duration = MULTI_DAY_SECONDS,
                organizationId = e2e.session.organizationId,
            ),
        )
    }

    private data class MultiDayFixture(val startDate: LocalDate, val entry: TimeEntry)

    private fun testMonth(zone: ZoneId): YearMonth = YearMonth.from(
        Instant.ofEpochMilli(e2e.testClock.nowMs).atZone(zone),
    )

    companion object {
        private const val ENTRY_ID = "multi-day-entry"
        private const val MULTI_DAY_SECONDS = 26 * 60 * 60
        private const val WAIT_MS = 15_000L
    }
}

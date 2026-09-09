/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.domain.time.formatTimeEntryInstant
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HistoryWindowTest {
    private fun entry(id: String, description: String? = null) = TimeEntry(
        id = id,
        userId = "user",
        start = "2026-07-06T08:00:00Z",
        end = "2026-07-06T09:00:00Z",
        description = description,
        organizationId = "org",
    )

    @Test fun `recent mode replaces displayed list with the collected window`() {
        val displayed = listOf(entry("old-1"), entry("old-2"))
        val collected = listOf(entry("new-1"))
        val merged = HistoryWindow.merge(HistoryWindowMode.RECENT, displayed, collected)
        assertEquals(collected, merged)
    }

    @Test fun `paginated window survives a poll emission of the recent slice`() {
        // User has jumped to / paged in an older window not present in the recent Room slice.
        val jumped = listOf(entry("jump-1"), entry("jump-2"), entry("jump-3"))
        val pollEmission = listOf(
            entry("recent-a").copy(start = "2026-07-08T08:00:00Z"),
            entry("recent-b").copy(start = "2026-07-07T08:00:00Z"),
        )
        val merged = HistoryWindow.merge(
            HistoryWindowMode.PAGINATED,
            jumped,
            pollEmission,
            includesNewestHistory = false,
        )
        // The jumped window and its order/scroll anchor are preserved intact.
        assertEquals(jumped.map { it.id }, merged.map { it.id })
    }

    @Test fun `paginated mode overlays fresher copies of still-visible entries in place`() {
        val displayed = listOf(entry("a", "old"), entry("b", "old"))
        val collected = listOf(entry("a", "edited"))
        val merged = HistoryWindow.merge(HistoryWindowMode.PAGINATED, displayed, collected)
        assertEquals(listOf("a", "b"), merged.map { it.id })
        assertEquals("edited", merged.first { it.id == "a" }.description)
        assertEquals("old", merged.first { it.id == "b" }.description)
    }

    @Test fun `membership change resolves only after Room contains the completed stop`() {
        val running = entry("timer").copy(end = null)
        val stopped = running.copy(end = "2026-07-06T09:00:00Z")
        val changes = mapOf("timer" to HistoryMembershipChange.COMPLETED_ENTRY_PRESENT)

        assertEquals(emptySet<String>(), resolvedHistoryMembershipChangeIds(changes, listOf(running)))
        assertEquals(setOf("timer"), resolvedHistoryMembershipChangeIds(changes, listOf(stopped)))
    }

    @Test fun `delete membership change resolves only after Room hides the entry`() {
        val deleted = entry("deleted")
        val changes = mapOf("deleted" to HistoryMembershipChange.ENTRY_ABSENT)

        assertEquals(emptySet<String>(), resolvedHistoryMembershipChangeIds(changes, listOf(deleted)))
        assertEquals(setOf("deleted"), resolvedHistoryMembershipChangeIds(changes, emptyList()))
    }

    @Test fun `paginated mode adds a locally stopped entry from Room`() {
        val displayed = listOf(entry("existing"))
        val stopped = entry("stopped").copy(start = "2026-07-06T10:00:00Z")
        val collected = listOf(stopped, displayed.single())

        val merged = HistoryWindow.merge(
            HistoryWindowMode.PAGINATED,
            displayed,
            collected,
            locallyMutatedEntryIds = setOf(stopped.id),
        )

        assertEquals(listOf("stopped", "existing"), merged.map { it.id })
    }

    @Test fun `paginated mode does not add a locally started entry before it is stopped`() {
        val displayed = listOf(entry("existing"))
        val running = entry("running").copy(end = null)

        val merged = HistoryWindow.merge(
            HistoryWindowMode.PAGINATED,
            displayed,
            listOf(running, displayed.single()),
            locallyMutatedEntryIds = setOf(running.id),
        )

        assertEquals(listOf("existing"), merged.map { it.id })
    }

    @Test fun `paginated mode removes a locally deleted entry missing from Room`() {
        val deleted = entry("deleted")
        val remaining = entry("remaining")

        val merged = HistoryWindow.merge(
            HistoryWindowMode.PAGINATED,
            listOf(deleted, remaining),
            listOf(remaining),
            locallyMutatedEntryIds = setOf(deleted.id),
        )

        assertEquals(listOf("remaining"), merged.map { it.id })
    }

    @Test fun `paginated mode restores an undone delete in chronological position`() {
        val newest = entry("newest").copy(start = "2026-07-06T10:00:00Z")
        val restored = entry("restored").copy(start = "2026-07-06T09:00:00Z")
        val oldest = entry("oldest").copy(start = "2026-07-06T08:00:00Z")

        val merged = HistoryWindow.merge(
            HistoryWindowMode.PAGINATED,
            listOf(newest, oldest),
            listOf(newest, restored, oldest),
            locallyMutatedEntryIds = setOf(restored.id),
        )

        assertEquals(listOf("newest", "restored", "oldest"), merged.map { it.id })
    }

    @Test fun `paginated mode swaps a rekeyed manual entry for its server copy`() {
        // A manual entry is shown under its local id; sync then rekeys the Room row to the
        // server id. The stale local row must go and the server row must take its place.
        val existing = entry("existing")
        val local = entry("local-create-1", "draft").copy(start = "2026-07-06T10:00:00Z")
        val server = local.copy(id = "server-1", description = "synced")

        val merged = HistoryWindow.merge(
            HistoryWindowMode.PAGINATED,
            listOf(local, existing),
            listOf(server, existing),
        )

        assertEquals(listOf("server-1", "existing"), merged.map { it.id })
        assertEquals("synced", merged.first().description)
    }

    @Test fun `paginated mode adds a Room entry inside the displayed range`() {
        val newest = entry("newest").copy(start = "2026-07-06T10:00:00Z")
        val oldest = entry("oldest").copy(start = "2026-07-06T08:00:00Z")
        val pulled = entry("pulled").copy(start = "2026-07-06T09:00:00Z")
        val older = entry("older").copy(start = "2026-07-05T09:00:00Z")

        val merged = HistoryWindow.merge(
            HistoryWindowMode.PAGINATED,
            listOf(newest, oldest),
            listOf(newest, pulled, oldest, older),
            includesNewestHistory = false,
        )

        assertEquals(listOf("newest", "pulled", "oldest"), merged.map { it.id })
    }

    @Test fun `paginated mode adds newer Room entries only when the window reaches the newest history`() {
        val displayed = listOf(entry("existing"))
        val newer = entry("newer").copy(start = "2026-07-06T12:00:00Z")
        val collected = listOf(newer, displayed.single())

        val reachesNewest = HistoryWindow.merge(
            HistoryWindowMode.PAGINATED,
            displayed,
            collected,
            includesNewestHistory = true,
        )
        val jumpedBack = HistoryWindow.merge(
            HistoryWindowMode.PAGINATED,
            displayed,
            collected,
            includesNewestHistory = false,
        )

        assertEquals(listOf("newer", "existing"), reachesNewest.map { it.id })
        assertEquals(listOf("existing"), jumpedBack.map { it.id })
    }

    @Test fun `paginated mode drops a local-only row that Room no longer holds`() {
        val existing = entry("existing")
        val deadLettered = entry("local-create-dead").copy(start = "2026-07-06T10:00:00Z")

        val merged = HistoryWindow.merge(
            HistoryWindowMode.PAGINATED,
            listOf(deadLettered, existing),
            listOf(existing),
        )

        assertEquals(listOf("existing"), merged.map { it.id })
    }

    @Test fun `entry saved from the editor slots chronologically among server timestamps`() {
        val displayed = listOf(
            entry("later").copy(start = "2026-07-06T09:00:00Z", end = "2026-07-06T09:30:00Z"),
            entry("earlier").copy(start = "2026-07-06T08:00:00Z", end = "2026-07-06T08:30:00Z"),
        )
        val localStart = ZonedDateTime.of(2026, 7, 6, 10, 30, 0, 0, ZoneId.of("Europe/Amsterdam"))
        val saved = entry("local-create-1").copy(
            start = formatTimeEntryInstant(localStart),
            end = formatTimeEntryInstant(localStart.plusMinutes(10)),
        )
        val merged = HistoryWindow.merge(HistoryWindowMode.PAGINATED, displayed, displayed + saved)
        assertEquals(listOf("later", "local-create-1", "earlier"), merged.map { it.id })
    }
}

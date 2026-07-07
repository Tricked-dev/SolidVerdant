package dev.tricked.solidverdant.ui.tracking

import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Test

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
        val pollEmission = listOf(entry("recent-a"), entry("recent-b"))
        val merged = HistoryWindow.merge(HistoryWindowMode.PAGINATED, jumped, pollEmission)
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
}

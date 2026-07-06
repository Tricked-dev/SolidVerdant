package dev.tricked.solidverdant.ui.calendar

import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TimelineLayoutTest {
    @Test
    fun entrySpanningNineToTenAmMapsToExpectedFractions() {
        val e = TimeEntry(id = "1", userId = "u", start = "2026-07-06T09:00:00Z",
            end = "2026-07-06T10:00:00Z", duration = 3600, organizationId = "o")
        val (top, height) = timelineOffsets(
            e,
            LocalDate.of(2026, 7, 6),
            Instant.parse("2026-07-06T12:00:00Z"),
            ZoneOffset.UTC,
        )
        // 9/24 = 0.375, 1h/24h = ~0.0417.
        assertEquals(0.375f, top, 0.05f)
        assertEquals(0.0417f, height, 0.02f)
    }
}

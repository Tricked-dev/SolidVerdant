package dev.tricked.solidverdant.ui.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatElapsedTimeTest {
    @Test fun `formats positive elapsed seconds as HH MM SS`() {
        assertEquals("01:02:03", formatElapsedTime(3723))
    }

    @Test fun `zero renders as all zeroes`() {
        assertEquals("00:00:00", formatElapsedTime(0))
    }

    @Test fun `negative elapsed is clamped to zero rather than rendering garbage`() {
        // A device clock behind the entry's start previously produced e.g. "-1:-5:-3".
        assertEquals("00:00:00", formatElapsedTime(-3723))
    }
}

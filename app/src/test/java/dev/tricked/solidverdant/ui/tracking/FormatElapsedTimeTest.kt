/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

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

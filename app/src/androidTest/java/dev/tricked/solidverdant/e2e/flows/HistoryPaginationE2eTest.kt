/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import dev.tricked.solidverdant.e2e.robots.TrackRobot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * History paging against a mock that (now) honors limit/offset like the real backend: the app
 * must request follow-up pages with increasing offsets as the user digs into history, rather
 * than assuming the first response contained everything.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HistoryPaginationE2eTest {

    @get:Rule
    val e2e = E2eRule(this)

    @Test
    fun deepHistoryScrollRequestsOlderPagesFromTheServer() {
        // 600 entries: beyond both the 250-entry refresh window AND the 500-entry quick-fill
        // page, so the oldest history can only arrive via a follow-up request with offset > 0.
        e2e.mockServer.presetStressWorld(entryCount = 600)
        e2e.launchApp()
        TrackRobot(e2e.composeRule).waitForHistory()

        val history = e2e.composeRule.onAllNodes(hasTestTag(TestTags.TRACK_HISTORY_LIST)).onFirst()

        // A real touch swipe first: load-more is gated on the user having actually scrolled.
        history.performTouchInput { swipeUp(durationMillis = 200) }
        e2e.composeRule.waitForIdle()

        // Then walk toward the end of the loaded window until a paged request (offset > 0)
        // appears. The probe grows after a successful jump and shrinks after overshooting the
        // composed item count, so it converges on the list's real bottom (load-more triggers
        // within 75 items of it).
        var scrollTarget = 300
        e2e.composeRule.waitUntil(WAIT_MS) {
            runCatching { history.performScrollToIndex(scrollTarget) }
                .onSuccess { scrollTarget += 60 }
                .onFailure { scrollTarget = (scrollTarget - 40).coerceAtLeast(0) }
            history.performTouchInput { swipeUp(durationMillis = 200) }
            e2e.composeRule.waitForIdle()
            pagedRequestHappened()
        }

        assertTrue(
            "Expected at least one follow-up page with a non-zero offset; saw: " +
                e2e.mockServer.callsMatching("GET", "/time-entries").map { it.path },
            pagedRequestHappened(),
        )
    }

    private fun pagedRequestHappened(): Boolean = e2e.mockServer.callsMatching("GET", "/time-entries").any { call ->
        val offset = Regex("""offset=(\d+)""").find(call.path)?.groupValues?.get(1)?.toIntOrNull()
        offset != null && offset > 0
    }

    companion object {
        // Generous: each poll iteration performs a programmatic scroll plus a swipe and waits
        // for idle against a 500-entry interpreted debug build.
        private const val WAIT_MS = 45_000L
    }
}

package dev.tricked.solidverdant.e2e.perf

import android.util.SparseIntArray
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.core.app.FrameMetricsAggregator
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dagger.hilt.android.testing.HiltAndroidTest
import dev.tricked.solidverdant.e2e.E2eRule
import dev.tricked.solidverdant.e2e.TestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** Real rendered-frame guardrail for the high-volume History and primary navigation paths. */
@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ScrollingFramePerformanceTest {

    @get:Rule
    val e2e = E2eRule(this)

    @Test
    fun largeHistoryAndTabSwitchesStayWithinFrameBudget() {
        e2e.mockServer.presetStressWorld()
        e2e.seedTemplates()
        val scenario = e2e.launchApp()
        val rule = e2e.composeRule
        rule.waitUntil(15_000) {
            rule.onAllNodes(hasTestTag(TestTags.TRACK_HISTORY_LIST)).fetchSemanticsNodes().isNotEmpty()
        }
        val history = rule.onAllNodes(hasTestTag(TestTags.TRACK_HISTORY_LIST)).onFirst()
        history.performScrollToIndex(0)
        rule.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2_000)

        val down = measureFrames(scenario) {
            repeat(6) { history.performTouchInput { swipeUp(durationMillis = 300) } }
        }
        history.performScrollToIndex(40)
        rule.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2_000)
        val up = measureFrames(scenario) {
            repeat(6) { history.performTouchInput { swipeDown(durationMillis = 300) } }
        }
        val tabs = measureFrames(scenario) {
            listOf("stats", "track", "calendar", "track", "review", "track").forEach { route ->
                rule.onAllNodes(hasTestTag("main_nav_$route")).onFirst().performClick()
                rule.waitForIdle()
            }
        }

        println("PERF history_down=$down history_up=$up tab_switches=$tabs")
        assertFrameBudget("history down", down)
        assertFrameBudget("history up", up)
        assertFrameBudget("tab switches", tabs)
    }

    private fun measureFrames(
        scenario: androidx.test.core.app.ActivityScenario<dev.tricked.solidverdant.MainActivity>,
        block: () -> Unit,
    ): FrameResult {
        val aggregator = FrameMetricsAggregator(FrameMetricsAggregator.TOTAL_DURATION)
        scenario.onActivity(aggregator::add)
        block()
        e2e.composeRule.waitForIdle()
        val histogram = AtomicReference<SparseIntArray?>()
        scenario.onActivity {
            histogram.set(aggregator.remove(it)?.get(FrameMetricsAggregator.TOTAL_INDEX))
        }
        return histogram.get().toFrameResult()
    }

    private fun SparseIntArray?.toFrameResult(): FrameResult {
        var total = 0
        var over16Ms = 0
        var worstMs = 0
        if (this != null) {
            for (index in 0 until size()) {
                val durationMs = keyAt(index)
                val frames = valueAt(index)
                total += frames
                if (durationMs > 16) over16Ms += frames
                if (frames > 0) worstMs = maxOf(worstMs, durationMs)
            }
        }
        return FrameResult(total, over16Ms, worstMs)
    }

    private fun assertFrameBudget(label: String, result: FrameResult) {
        assertTrue("$label produced too few frame metrics: $result", result.total >= 20)
        assertTrue(
            "$label exceeded 60% frames over 16ms in an instrumented debug build: $result",
            result.over16Ms * 100 <= result.total * 60,
        )
    }
}

private data class FrameResult(val total: Int, val over16Ms: Int, val worstMs: Int) {
    override fun toString(): String =
        "$over16Ms/$total over16ms (${if (total == 0) 0 else over16Ms * 100 / total}%), worst=${worstMs}ms"
}

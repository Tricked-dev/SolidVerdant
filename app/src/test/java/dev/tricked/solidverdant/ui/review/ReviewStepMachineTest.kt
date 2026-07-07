/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewStepMachineTest {

    private fun item(id: String, type: ReviewItemType = ReviewItemType.UNCATEGORIZED) = ReviewItem(id = id, type = type, entryId = id)

    private val items = listOf(item("a"), item("b"), item("c"))

    @Test fun `current is the first unhandled item`() {
        assertEquals("a", ReviewStepMachine.current(items, emptySet())?.id)
        assertEquals("b", ReviewStepMachine.current(items, setOf("a"))?.id)
        assertEquals("c", ReviewStepMachine.current(items, setOf("a", "b"))?.id)
    }

    @Test fun `current is null and complete when all handled`() {
        val handled = setOf("a", "b", "c")
        assertNull(ReviewStepMachine.current(items, handled))
        assertTrue(ReviewStepMachine.isComplete(items, handled))
    }

    @Test fun `empty item list is complete`() {
        assertTrue(ReviewStepMachine.isComplete(emptyList(), emptySet()))
        assertNull(ReviewStepMachine.current(emptyList(), emptySet()))
    }

    @Test fun `not complete while any item is unhandled`() {
        assertFalse(ReviewStepMachine.isComplete(items, setOf("a", "b")))
    }

    @Test fun `progress counts handled over total`() {
        val p0 = ReviewStepMachine.progress(items, emptySet())
        assertEquals(0, p0.completed)
        assertEquals(3, p0.total)
        assertEquals(1, p0.position)
        assertEquals(0f, p0.fraction, 0.0001f)

        val p1 = ReviewStepMachine.progress(items, setOf("a"))
        assertEquals(1, p1.completed)
        assertEquals(3, p1.total)
        assertEquals(2, p1.position)
        assertEquals(1f / 3f, p1.fraction, 0.0001f)

        val p3 = ReviewStepMachine.progress(items, setOf("a", "b", "c"))
        assertTrue(p3.isComplete)
        assertEquals(1f, p3.fraction, 0.0001f)
    }

    @Test fun `total stays stable when a handled item drops out of the list`() {
        // Simulate a fix: "a" was handled and its underlying data changed, so it is gone from the
        // live list. The denominator must not shrink mid-session.
        val shrunk = listOf(item("b"), item("c"))
        val p = ReviewStepMachine.progress(shrunk, setOf("a"))
        assertEquals(1, p.completed)
        assertEquals(3, p.total)
        assertEquals("b", ReviewStepMachine.current(shrunk, setOf("a"))?.id)
    }

    @Test fun `a newly appearing item is not complete even after handling the originals`() {
        val handled = setOf("a", "b", "c")
        val withNew = items + item("d", ReviewItemType.FAILED_SYNC)
        assertFalse(ReviewStepMachine.isComplete(withNew, handled))
        assertEquals("d", ReviewStepMachine.current(withNew, handled)?.id)
    }

    @Test fun `progress with empty items reports complete`() {
        val p = ReviewStepMachine.progress(emptyList(), emptySet())
        assertTrue(p.isComplete)
        assertEquals(1f, p.fraction, 0.0001f)
    }

    // ---- ReviewDayViewModel.largestGap (pure "missing periods" math) ----

    @Test fun `largest gap is zero for fewer than two intervals`() {
        assertEquals(0L, ReviewDayViewModel.largestGap(emptyList()))
        assertEquals(0L, ReviewDayViewModel.largestGap(listOf(0L..100L)))
    }

    @Test fun `largest gap is the widest untracked span between intervals`() {
        val intervals = listOf(0L..100L, 300L..400L, 1000L..1200L)
        // gaps: 300-100 = 200, 1000-400 = 600 -> 600 is largest
        assertEquals(600L, ReviewDayViewModel.largestGap(intervals))
    }

    @Test fun `overlapping and nested intervals have no gap`() {
        assertEquals(0L, ReviewDayViewModel.largestGap(listOf(0L..500L, 100L..200L, 200L..400L)))
        assertEquals(0L, ReviewDayViewModel.largestGap(listOf(0L..300L, 250L..600L)))
    }

    @Test fun `unsorted intervals are handled`() {
        val intervals = listOf(1000L..1200L, 0L..100L, 300L..400L)
        assertEquals(600L, ReviewDayViewModel.largestGap(intervals))
    }
}

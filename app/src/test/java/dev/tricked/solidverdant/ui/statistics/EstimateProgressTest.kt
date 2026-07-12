/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import dev.tricked.solidverdant.data.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimateProgressTest {

    private fun project(
        id: String,
        name: String = id,
        color: String = "#f00",
        clientId: String? = null,
        estimatedTime: Int? = null,
        spentTime: Int = 0,
        isBillable: Boolean = false,
    ) = Project(
        id = id,
        name = name,
        color = color,
        clientId = clientId,
        estimatedTime = estimatedTime,
        spentTime = spentTime,
        isBillable = isBillable,
    )

    // ----- math on the data class -----

    @Test
    fun `remaining fraction and flags for an under-budget project`() {
        val p = EstimateProgress(id = "p", name = "P", colorHex = "#f00", estimatedSeconds = 8 * 3600, spentSeconds = 2 * 3600)
        assertEquals(6 * 3600, p.remainingSeconds)
        assertEquals(0.25f, p.fraction, 0.0001f)
        assertFalse(p.isOverBudget)
        assertFalse(p.isNearEstimate)
    }

    @Test
    fun `near-estimate when fraction crosses the threshold but not over`() {
        val p = EstimateProgress(id = "p", name = "P", colorHex = null, estimatedSeconds = 100, spentSeconds = 95)
        assertTrue(p.isNearEstimate)
        assertFalse(p.isOverBudget)
    }

    @Test
    fun `over-budget yields negative remaining and clears near-estimate`() {
        val p = EstimateProgress(id = "p", name = "P", colorHex = null, estimatedSeconds = 100, spentSeconds = 130)
        assertTrue(p.isOverBudget)
        assertFalse(p.isNearEstimate)
        assertEquals(-30, p.remainingSeconds)
        assertEquals(1.3f, p.fraction, 0.0001f)
    }

    @Test
    fun `zero estimate yields zero fraction and no over-budget`() {
        val p = EstimateProgress(id = "p", name = "P", colorHex = null, estimatedSeconds = 0, spentSeconds = 5)
        assertEquals(0f, p.fraction, 0.0001f)
    }

    // ----- projectEstimateProgress selection / scoping / sort -----

    @Test
    fun `projects without a positive estimate are excluded`() {
        val projects = listOf(
            project("a", estimatedTime = null, spentTime = 10),
            project("b", estimatedTime = 0, spentTime = 10),
            project("c", estimatedTime = 100, spentTime = 10),
        )
        val out = StatisticsAggregator.projectEstimateProgress(projects, StatFilters())
        assertEquals(listOf("c"), out.map { it.id })
    }

    @Test
    fun `project filter restricts to selected projects`() {
        val projects = listOf(
            project("a", estimatedTime = 100, spentTime = 10),
            project("b", estimatedTime = 100, spentTime = 10),
        )
        val out = StatisticsAggregator.projectEstimateProgress(projects, StatFilters(projectIds = setOf("b")))
        assertEquals(listOf("b"), out.map { it.id })
    }

    @Test
    fun `client filter restricts through the project client`() {
        val projects = listOf(
            project("a", clientId = "c1", estimatedTime = 100, spentTime = 10),
            project("b", clientId = "c2", estimatedTime = 100, spentTime = 10),
        )
        val out = StatisticsAggregator.projectEstimateProgress(projects, StatFilters(clientIds = setOf("c2")))
        assertEquals(listOf("b"), out.map { it.id })
    }

    @Test
    fun `billable filter restricts to billable projects`() {
        val projects = listOf(
            project("a", estimatedTime = 100, spentTime = 10, isBillable = true),
            project("b", estimatedTime = 100, spentTime = 10, isBillable = false),
        )
        val out = StatisticsAggregator.projectEstimateProgress(projects, StatFilters(billable = BillableFilter.Billable))
        assertEquals(listOf("a"), out.map { it.id })
    }

    @Test
    fun `over-budget projects sort first then by fraction descending`() {
        val projects = listOf(
            project("low", estimatedTime = 100, spentTime = 20), // 0.2
            project("over", estimatedTime = 100, spentTime = 150), // over
            project("high", estimatedTime = 100, spentTime = 80), // 0.8
        )
        val out = StatisticsAggregator.projectEstimateProgress(projects, StatFilters())
        assertEquals(listOf("over", "high", "low"), out.map { it.id })
    }

    @Test
    fun `archived projects are excluded`() {
        val projects = listOf(
            project("a", estimatedTime = 100, spentTime = 10).copy(isArchived = true),
            project("b", estimatedTime = 100, spentTime = 10),
        )
        val out = StatisticsAggregator.projectEstimateProgress(projects, StatFilters())
        assertEquals(listOf("b"), out.map { it.id })
    }
}

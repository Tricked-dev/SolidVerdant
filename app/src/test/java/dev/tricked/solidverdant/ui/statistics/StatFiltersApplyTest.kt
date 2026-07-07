package dev.tricked.solidverdant.ui.statistics

import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StatFiltersApplyTest {

    private fun entry(
        id: String,
        projectId: String? = null,
        taskId: String? = null,
        tags: List<Tag> = emptyList(),
        billable: Boolean = false,
    ) = TimeEntry(
        id = id, userId = "u", start = "2026-07-01T09:00:00Z", duration = 60,
        projectId = projectId, taskId = taskId, tags = tags, billable = billable,
        organizationId = "org",
    )

    private val projects = listOf(
        Project(id = "p1", name = "Alpha", color = "#f00", clientId = "c1"),
        Project(id = "p2", name = "Beta", color = "#0f0", clientId = "c2"),
    )

    private val entries = listOf(
        entry("a", projectId = "p1", taskId = "t1", tags = listOf(Tag("g1")), billable = true),
        entry("b", projectId = "p2", taskId = "t2", tags = listOf(Tag("g2")), billable = false),
        entry("c", projectId = null, tags = listOf(Tag("g1"), Tag("g2")), billable = true),
    )

    private fun ids(list: List<TimeEntry>) = list.map { it.id }

    @Test
    fun `inactive filter returns the same list instance`() {
        val out = StatisticsAggregator.applyFilters(entries, projects, StatFilters())
        assertSame(entries, out)
    }

    @Test
    fun `project filter keeps only matching entries`() {
        val out = StatisticsAggregator.applyFilters(entries, projects, StatFilters(projectIds = setOf("p1")))
        assertEquals(listOf("a"), ids(out))
    }

    @Test
    fun `client filter resolves through the entry project`() {
        val out = StatisticsAggregator.applyFilters(entries, projects, StatFilters(clientIds = setOf("c2")))
        assertEquals(listOf("b"), ids(out))
    }

    @Test
    fun `client filter excludes entries without a project`() {
        val out = StatisticsAggregator.applyFilters(entries, projects, StatFilters(clientIds = setOf("c1", "c2")))
        assertEquals(listOf("a", "b"), ids(out))
    }

    @Test
    fun `task filter keeps matching task`() {
        val out = StatisticsAggregator.applyFilters(entries, projects, StatFilters(taskIds = setOf("t2")))
        assertEquals(listOf("b"), ids(out))
    }

    @Test
    fun `tag filter matches any of the selected tags`() {
        val out = StatisticsAggregator.applyFilters(entries, projects, StatFilters(tagIds = setOf("g1")))
        assertEquals(listOf("a", "c"), ids(out))
    }

    @Test
    fun `billable filter splits both directions`() {
        assertEquals(
            listOf("a", "c"),
            ids(StatisticsAggregator.applyFilters(entries, projects, StatFilters(billable = BillableFilter.Billable))),
        )
        assertEquals(
            listOf("b"),
            ids(StatisticsAggregator.applyFilters(entries, projects, StatFilters(billable = BillableFilter.NonBillable))),
        )
    }

    @Test
    fun `combined dimensions are ANDed together`() {
        val filters = StatFilters(projectIds = setOf("p1"), billable = BillableFilter.NonBillable)
        assertTrue(StatisticsAggregator.applyFilters(entries, projects, filters).isEmpty())
    }

    @Test
    fun `active count and flag reflect selected constraints`() {
        val filters = StatFilters(
            projectIds = setOf("p1", "p2"),
            tagIds = setOf("g1"),
            billable = BillableFilter.Billable,
        )
        assertTrue(filters.isActive)
        assertEquals(4, filters.activeCount)
        assertEquals(3, filters.toggleProject("p1").activeCount)
    }
}

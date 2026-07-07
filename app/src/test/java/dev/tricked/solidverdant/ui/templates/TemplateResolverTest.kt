package dev.tricked.solidverdant.ui.templates

import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.repository.EntryTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateResolverTest {

    private fun project(id: String, archived: Boolean = false) =
        Project(id = id, name = "Project $id", color = "#123456", isArchived = archived)

    private fun task(id: String, projectId: String, done: Boolean = false) =
        Task(id = id, name = "Task $id", isDone = done, projectId = projectId, createdAt = "", updatedAt = "")

    private fun tag(id: String) = Tag(id = id, name = "Tag $id")

    private fun template(
        projectId: String? = null,
        taskId: String? = null,
        tagIds: List<String> = emptyList(),
        description: String? = null,
        billable: Boolean = false,
    ) = EntryTemplate(
        id = "tmpl",
        organizationId = "org",
        name = null,
        projectId = projectId,
        taskId = taskId,
        description = description,
        tagIds = tagIds,
        billable = billable,
        isFavorite = false,
        sortOrder = 0,
        createdAtMs = 0L,
    )

    // --- project / task availability ------------------------------------------------------------

    @Test fun `fully available template maps every field`() {
        val resolution = TemplateResolver.resolve(
            template(projectId = "p1", taskId = "t1", tagIds = listOf("g1"), billable = true, description = "Standup"),
            projects = listOf(project("p1")),
            tasks = listOf(task("t1", "p1")),
            tags = listOf(tag("g1")),
        )
        assertEquals(RefStatus.OK, resolution.projectStatus)
        assertEquals(RefStatus.OK, resolution.taskStatus)
        assertFalse(resolution.hasIssues)
        val start = resolution.toStart()
        assertEquals("p1", start.projectId)
        assertEquals("t1", start.taskId)
        assertEquals(listOf("g1"), start.tagIds)
        assertTrue(start.billable)
        assertEquals("Standup", start.description)
    }

    @Test fun `archived project is dropped and flagged`() {
        val resolution = TemplateResolver.resolve(
            template(projectId = "p1"),
            projects = listOf(project("p1", archived = true)),
            tasks = emptyList(),
            tags = emptyList(),
        )
        assertEquals(RefStatus.ARCHIVED, resolution.projectStatus)
        assertEquals(null, resolution.projectId)
        assertTrue(resolution.hasIssues)
    }

    @Test fun `missing project is dropped and flagged`() {
        val resolution = TemplateResolver.resolve(
            template(projectId = "gone"),
            projects = emptyList(),
            tasks = emptyList(),
            tags = emptyList(),
        )
        assertEquals(RefStatus.MISSING, resolution.projectStatus)
        assertEquals(null, resolution.projectId)
        assertTrue(resolution.hasIssues)
    }

    @Test fun `no project reference is not an issue`() {
        val resolution = TemplateResolver.resolve(
            template(projectId = null),
            projects = emptyList(),
            tasks = emptyList(),
            tags = emptyList(),
        )
        assertEquals(RefStatus.NONE, resolution.projectStatus)
        assertFalse(resolution.hasIssues)
    }

    @Test fun `completed task is dropped and flagged`() {
        val resolution = TemplateResolver.resolve(
            template(projectId = "p1", taskId = "t1"),
            projects = listOf(project("p1")),
            tasks = listOf(task("t1", "p1", done = true)),
            tags = emptyList(),
        )
        assertEquals(RefStatus.OK, resolution.projectStatus)
        assertEquals(RefStatus.ARCHIVED, resolution.taskStatus)
        assertEquals(null, resolution.taskId)
        assertTrue(resolution.hasIssues)
    }

    @Test fun `task is dropped when its project is unavailable`() {
        val resolution = TemplateResolver.resolve(
            template(projectId = "p1", taskId = "t1"),
            projects = listOf(project("p1", archived = true)),
            tasks = listOf(task("t1", "p1")),
            tags = emptyList(),
        )
        assertEquals(RefStatus.ARCHIVED, resolution.projectStatus)
        assertEquals(RefStatus.MISSING, resolution.taskStatus)
        assertEquals(null, resolution.projectId)
        assertEquals(null, resolution.taskId)
    }

    @Test fun `task belonging to another project is rejected`() {
        val resolution = TemplateResolver.resolve(
            template(projectId = "p1", taskId = "t1"),
            projects = listOf(project("p1")),
            tasks = listOf(task("t1", "p2")),
            tags = emptyList(),
        )
        assertEquals(RefStatus.OK, resolution.projectStatus)
        assertEquals(RefStatus.MISSING, resolution.taskStatus)
        assertEquals(null, resolution.taskId)
    }

    // --- tags -----------------------------------------------------------------------------------

    @Test fun `deleted tags are dropped and counted`() {
        val resolution = TemplateResolver.resolve(
            template(tagIds = listOf("g1", "g2", "gone")),
            projects = emptyList(),
            tasks = emptyList(),
            tags = listOf(tag("g1"), tag("g2")),
        )
        assertEquals(listOf("g1", "g2"), resolution.tagIds)
        assertEquals(1, resolution.droppedTagCount)
        assertTrue(resolution.hasIssues)
    }

    @Test fun `toStart can override the description with filled placeholders`() {
        val resolution = TemplateResolver.resolve(
            template(description = "Review: {topic}"),
            projects = emptyList(),
            tasks = emptyList(),
            tags = emptyList(),
        )
        assertEquals("Review: Q3", resolution.toStart(description = "Review: Q3").description)
    }
}

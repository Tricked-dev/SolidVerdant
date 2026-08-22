/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectTaskDropdownMenuTest {
    private val matchingProject = project("project-1", "M5337RW - 62201")
    private val otherProject = project("project-2", "Internal work")
    private val turningTask = task("task-1", "Turning - Running", matchingProject.id)
    private val setupTask = task("task-2", "Setup and F/O", matchingProject.id)
    private val otherTask = task("task-3", "Planning", otherProject.id)
    private val projects = listOf(matchingProject, otherProject)
    private val tasks = listOf(turningTask, setupTask, otherTask)

    @Test
    fun `project search is case insensitive and trims whitespace`() {
        val results = filterProjects(projects, "  m5337  ")

        assertEquals(listOf(matchingProject), results)
    }

    @Test
    fun `task search is case insensitive and trims whitespace`() {
        val results = filterTasks(listOf(turningTask, setupTask), "  TURNING - RUNNING  ")

        assertEquals(listOf(turningTask), results)
    }

    @Test
    fun `blank searches preserve their available items`() {
        assertEquals(projects, filterProjects(projects, "   "))
        assertEquals(listOf(turningTask, setupTask), filterTasks(listOf(turningTask, setupTask), "   "))
    }

    @Test
    fun `tasks are grouped once and scoped to their selected project`() {
        val groupedTasks = groupTasksByProject(tasks)

        assertEquals(listOf(turningTask, setupTask), groupedTasks[matchingProject.id])
        assertEquals(listOf(otherTask), groupedTasks[otherProject.id])
    }

    @Test
    fun `unmatched searches return no items`() {
        val filteredProjects = filterProjects(projects, "not present")
        val filteredTasks = filterTasks(tasks, "not present")

        assertEquals(emptyList<Project>(), filteredProjects)
        assertEquals(emptyList<Task>(), filteredTasks)
    }

    private fun project(id: String, name: String) = Project(id = id, name = name, color = "#000000")

    private fun task(id: String, name: String, projectId: String) = Task(
        id = id,
        name = name,
        projectId = projectId,
        createdAt = "2026-08-18T00:00:00Z",
        updatedAt = "2026-08-18T00:00:00Z",
    )
}

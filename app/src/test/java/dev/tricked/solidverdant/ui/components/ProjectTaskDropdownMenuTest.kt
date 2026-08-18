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
    fun `project name match includes every task in that project`() {
        val results = filterProjectsAndTasks(projects, tasks, "  m5337  ")

        assertEquals(listOf(matchingProject), results.projects)
        assertEquals(listOf(turningTask, setupTask), results.tasksByProject[matchingProject.id])
    }

    @Test
    fun `task name match includes only matching tasks and their project`() {
        val results = filterProjectsAndTasks(projects, tasks, "TURNING - RUNNING")

        assertEquals(listOf(matchingProject), results.projects)
        assertEquals(listOf(turningTask), results.tasksByProject[matchingProject.id])
    }

    @Test
    fun `blank query preserves all projects and tasks`() {
        val results = filterProjectsAndTasks(projects, tasks, "   ")

        assertEquals(projects, results.projects)
        assertEquals(listOf(turningTask, setupTask), results.tasksByProject[matchingProject.id])
        assertEquals(listOf(otherTask), results.tasksByProject[otherProject.id])
    }

    @Test
    fun `unmatched query returns no projects or tasks`() {
        val results = filterProjectsAndTasks(projects, tasks, "not present")

        assertEquals(emptyList<Project>(), results.projects)
        assertEquals(emptyMap<String, List<Task>>(), results.tasksByProject)
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

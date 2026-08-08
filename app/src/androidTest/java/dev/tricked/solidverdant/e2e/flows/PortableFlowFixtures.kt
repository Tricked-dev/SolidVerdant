/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.e2e.E2eCatalog
import dev.tricked.solidverdant.e2e.E2eRule
import java.time.Instant

/** Build a completed entry from the selected backend's account instead of mock-owned constants. */
internal fun E2eRule.completedFixtureEntry(
    logicalId: String = "seed-entry-1",
    description: String = "Seeded work",
    start: Instant = Instant.ofEpochMilli(testClock.nowMs).minusSeconds(7_200),
    durationSeconds: Int = 3_600,
): TimeEntry = TimeEntry(
    id = logicalId,
    description = description,
    userId = session.userId,
    start = start.toString(),
    end = start.plusSeconds(durationSeconds.toLong()).toString(),
    duration = durationSeconds,
    organizationId = session.organizationId,
)

internal data class TrackCatalogFixture(val project: Project, val task: Task, val tag: Tag)

/** Select the deterministic disposable catalogue shared by the mock and live reset account. */
internal fun E2eRule.catalogFixture(): TrackCatalogFixture {
    val catalog: E2eCatalog = catalogSnapshot()
    val project = catalog.projects.firstOrNull { it.name == LIVE_TEST_PROJECT_NAME }
        ?: error("Live E2E account has no project fixture named $LIVE_TEST_PROJECT_NAME")
    val task = catalog.tasks.firstOrNull { it.projectId == project.id && it.name == LIVE_TEST_TASK_NAME }
        ?: error("Live E2E account has no task fixture named $LIVE_TEST_TASK_NAME for project ${project.id}")
    val tag = catalog.tags.firstOrNull { it.name == LIVE_TEST_TAG_NAME }
        ?: error("Live E2E account has no tag fixture named $LIVE_TEST_TAG_NAME")
    return TrackCatalogFixture(project, task, tag)
}

private const val LIVE_TEST_PROJECT_NAME = "Live Test Project"
private const val LIVE_TEST_TASK_NAME = "Live Test Task"
private const val LIVE_TEST_TAG_NAME = "Live Test Tag"

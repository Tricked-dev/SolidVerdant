/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.sync

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictSnapshotTest {
    private val json = Json { encodeDefaults = true }

    private fun baseline() = ConflictSnapshot.of(
        start = "2026-07-11T09:00:00Z",
        end = "2026-07-11T10:00:00Z",
        description = "work",
        projectId = "p1",
        taskId = "t1",
        billable = true,
        tagIds = listOf("a", "b"),
    )

    @Test fun `identical content matches`() {
        val a = baseline()
        val b = baseline()
        assertTrue(a.matches(b))
    }

    @Test fun `timestamp format variance does not fabricate a conflict`() {
        val a = ConflictSnapshot.of(
            start = "2026-07-11T09:00:00Z",
            end = "2026-07-11T10:00:00Z",
            description = "work",
            projectId = "p1",
            taskId = "t1",
            billable = true,
            tagIds = listOf("a", "b"),
        )
        val b = ConflictSnapshot.of(
            start = "2026-07-11T11:00:00+02:00",
            end = "2026-07-11T10:00:00Z",
            description = "work",
            projectId = "p1",
            taskId = "t1",
            billable = true,
            tagIds = listOf("a", "b"),
        )
        val c = ConflictSnapshot.of(
            start = "2026-07-11T09:00:00.000Z",
            end = "2026-07-11T10:00:00Z",
            description = "work",
            projectId = "p1",
            taskId = "t1",
            billable = true,
            tagIds = listOf("a", "b"),
        )
        assertTrue(a.matches(b))
        assertTrue(a.matches(c))
        assertTrue(b.matches(c))
    }

    @Test fun `null description equals blank description`() {
        val a = ConflictSnapshot.of(
            start = "2026-07-11T09:00:00Z",
            end = null,
            description = null,
            projectId = null,
            taskId = null,
            billable = false,
            tagIds = emptyList(),
        )
        val b = ConflictSnapshot.of(
            start = "2026-07-11T09:00:00Z",
            end = null,
            description = "",
            projectId = null,
            taskId = null,
            billable = false,
            tagIds = emptyList(),
        )
        assertTrue(a.matches(b))
    }

    @Test fun `tag order is irrelevant`() {
        val a = ConflictSnapshot.of(
            start = "2026-07-11T09:00:00Z",
            end = null,
            description = "work",
            projectId = null,
            taskId = null,
            billable = false,
            tagIds = listOf("b", "a"),
        )
        val b = ConflictSnapshot.of(
            start = "2026-07-11T09:00:00Z",
            end = null,
            description = "work",
            projectId = null,
            taskId = null,
            billable = false,
            tagIds = listOf("a", "b"),
        )
        assertTrue(a.matches(b))
    }

    @Test fun `each conflict-relevant field diverges`() {
        val base = baseline()

        val descriptionDiff = base.copy(description = "other")
        val projectDiff = base.copy(projectId = "p2")
        val taskDiff = base.copy(taskId = "t2")
        val billableDiff = base.copy(billable = false)
        val tagsDiff = base.copy(tagIds = listOf("a"))
        val startDiff = ConflictSnapshot.of(
            start = "2026-07-11T09:30:00Z",
            end = "2026-07-11T10:00:00Z",
            description = "work",
            projectId = "p1",
            taskId = "t1",
            billable = true,
            tagIds = listOf("a", "b"),
        )
        val endDiff = ConflictSnapshot.of(
            start = "2026-07-11T09:00:00Z",
            end = "2026-07-11T10:30:00Z",
            description = "work",
            projectId = "p1",
            taskId = "t1",
            billable = true,
            tagIds = listOf("a", "b"),
        )

        assertFalse(base.matches(descriptionDiff))
        assertFalse(base.matches(projectDiff))
        assertFalse(base.matches(taskDiff))
        assertFalse(base.matches(billableDiff))
        assertFalse(base.matches(tagsDiff))
        assertFalse(base.matches(startDiff))
        assertFalse(base.matches(endDiff))
    }

    @Test fun `round-trips through json`() {
        val original = baseline()
        val encoded = json.encodeToString(ConflictSnapshot.serializer(), original)
        val decoded = json.decodeFromString(ConflictSnapshot.serializer(), encoded)
        assertEquals(original, decoded)
        assertTrue(original.matches(decoded))
    }

    @Test fun `directly-constructed snapshot with unsorted tags still matches its sorted twin`() {
        val sorted = baseline()
        val unsorted = sorted.copy(tagIds = listOf("b", "a"))
        assertTrue(sorted.matches(unsorted))
        assertTrue(unsorted.matches(sorted))
    }

    @Test fun `parseable and unparseable timestamps never match`() {
        val parseable = ConflictSnapshot.of(
            start = "2026-07-11T09:00:00Z",
            end = null,
            description = "work",
            projectId = null,
            taskId = null,
            billable = false,
            tagIds = emptyList(),
        )
        val unparseable = ConflictSnapshot.of(
            start = "not-a-real-timestamp",
            end = null,
            description = "work",
            projectId = null,
            taskId = null,
            billable = false,
            tagIds = emptyList(),
        )
        assertFalse(parseable.matches(unparseable))
        assertFalse(unparseable.matches(parseable))
    }

    @Test fun `unparseable timestamp falls back to raw-string comparison`() {
        val a = ConflictSnapshot.of(
            start = "not-a-real-timestamp",
            end = null,
            description = "work",
            projectId = null,
            taskId = null,
            billable = false,
            tagIds = emptyList(),
        )
        val b = ConflictSnapshot.of(
            start = "not-a-real-timestamp",
            end = null,
            description = "work",
            projectId = null,
            taskId = null,
            billable = false,
            tagIds = emptyList(),
        )
        val c = ConflictSnapshot.of(
            start = "still-not-a-real-timestamp",
            end = null,
            description = "work",
            projectId = null,
            taskId = null,
            billable = false,
            tagIds = emptyList(),
        )

        assertTrue(a.matches(b))
        assertFalse(a.matches(c))
    }
}

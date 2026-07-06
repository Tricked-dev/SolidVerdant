package dev.tricked.solidverdant.data.local.db

import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityMappingTest {
    @Test fun time_entry_round_trips_through_entity() {
        val model = TimeEntry(
            id = "e1", description = "work", userId = "u1",
            start = "2026-01-01T09:00:00Z", end = "2026-01-01T10:00:00Z",
            duration = 3600, taskId = "t1", projectId = "p1",
            tags = listOf(Tag("tag1", "urgent")), billable = true,
            organizationId = "org1"
        )
        val entity = model.toEntity(updatedAt = 42L)
        val back = entity.toModel(tags = listOf(Tag("tag1", "urgent")))
        assertEquals(model.copy(tags = listOf(Tag("tag1", "urgent"))), back)
        assertEquals(SyncState.SYNCED, entity.syncState)
        assertEquals(42L, entity.updatedAt)
    }
}

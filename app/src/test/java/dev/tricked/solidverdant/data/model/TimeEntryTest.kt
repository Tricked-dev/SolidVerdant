package dev.tricked.solidverdant.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeEntryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun acceptsTagIdsFromTimeEntryList() {
        val response = json.decodeFromString<TimeEntriesResponse>(
            """{"data":[{"id":"entry","user_id":"user","start":"2026-07-03T16:40:00Z","tags":["tag-id"],"organization_id":"org"}]}"""
        )

        assertEquals(listOf(Tag(id = "tag-id")), response.data.single().tags)
    }

    @Test fun acceptsExpandedTagObjects() {
        val response = json.decodeFromString<TimeEntriesResponse>(
            """{"data":[{"id":"entry","user_id":"user","start":"2026-07-03T16:40:00Z","tags":[{"id":"tag-id","name":"focus"}],"organization_id":"org"}]}"""
        )

        assertEquals(listOf(Tag(id = "tag-id", name = "focus")), response.data.single().tags)
    }
}

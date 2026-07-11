/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("MaxLineLength")
class TimeEntryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun acceptsTagIdsFromTimeEntryList() {
        val response = json.decodeFromString<TimeEntriesResponse>(
            """{"data":[{"id":"entry","user_id":"user","start":"2026-07-03T16:40:00Z","tags":["tag-id"],"organization_id":"org"}]}""",
        )

        assertEquals(listOf(Tag(id = "tag-id")), response.data.single().tags)
    }

    @Test fun acceptsExpandedTagObjects() {
        val response = json.decodeFromString<TimeEntriesResponse>(
            """{"data":[{"id":"entry","user_id":"user","start":"2026-07-03T16:40:00Z","tags":[{"id":"tag-id","name":"focus"}],"organization_id":"org"}]}""",
        )

        assertEquals(listOf(Tag(id = "tag-id", name = "focus")), response.data.single().tags)
    }

    @Test fun startRequestSerializesEndWhenProvided() {
        // encodeDefaults = true matches the Json instance provided by NetworkModule
        val encodingJson = Json { encodeDefaults = true }
        val request = StartTimeEntryRequest(
            memberId = "member",
            start = "2026-07-06T08:00:00Z",
            end = "2026-07-06T09:30:00Z",
        )

        val encoded = encodingJson.encodeToString(request)

        assertTrue(encoded.contains(""""end":"2026-07-06T09:30:00Z""""))
        assertTrue(encoded.contains(""""member_id":"member""""))
    }

    @Test fun userProfileIncludesSolidtimePresentationAndLocaleFields() {
        val user = json.decodeFromString<User>(
            """{"id":"u","name":"Ada Lovelace","email":"ada@example.test","profile_photo_url":"https://example.test/ada.png","timezone":"Europe/Amsterdam","week_start":"monday"}""",
        )
        assertEquals("https://example.test/ada.png", user.profilePhotoUrl)
        assertEquals("Europe/Amsterdam", user.timezone)
        assertEquals("monday", user.weekStart)
    }
}

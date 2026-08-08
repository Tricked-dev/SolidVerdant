/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun acceptsTagIdsFromSingleActiveOrWriteResponse() {
        val response = json.decodeFromString<TimeEntryResponse>(
            """{"data":{"id":"entry","user_id":"user","start":"2026-07-03T16:40:00Z","end":null,"duration":0,"description":"Running","task_id":null,"project_id":null,"tags":["tag-id"],"billable":false,"organization_id":"org","type":"work"}}""",
        )

        assertEquals(listOf(Tag(id = "tag-id")), response.data?.tags)
    }

    @Test fun acceptsExpandedTagObjects() {
        val response = json.decodeFromString<TimeEntriesResponse>(
            """{"data":[{"id":"entry","user_id":"user","start":"2026-07-03T16:40:00Z","tags":[{"id":"tag-id","name":"focus"}],"organization_id":"org"}]}""",
        )

        assertEquals(listOf(Tag(id = "tag-id", name = "focus")), response.data.single().tags)
    }

    @Test fun acceptsOfficialWorkAndBreakEntriesWithNullableFields() {
        val response = json.decodeFromString<TimeEntriesResponse>(
            """
            {
              "data": [
                {
                  "id": "work-entry",
                  "start": "2026-08-08T08:00:00Z",
                  "end": "2026-08-08T09:00:00Z",
                  "duration": 3600,
                  "description": "Focused work",
                  "task_id": null,
                  "project_id": null,
                  "organization_id": "org",
                  "user_id": "user",
                  "tags": ["tag-id"],
                  "billable": true,
                  "type": "work"
                },
                {
                  "id": "break-entry",
                  "start": "2026-08-08T09:00:00Z",
                  "end": null,
                  "duration": 0,
                  "description": null,
                  "task_id": null,
                  "project_id": null,
                  "organization_id": "org",
                  "user_id": "user",
                  "tags": [],
                  "billable": false,
                  "type": "break"
                }
              ],
              "meta": {"total": 2}
            }
            """.trimIndent(),
        )

        assertEquals(listOf("work-entry", "break-entry"), response.data.map { it.id })
        assertEquals(listOf(Tag("tag-id")), response.data.first().tags)
        assertNull(response.data.last().end)
        assertNull(response.data.last().description)
        assertEquals(2, response.meta?.total)
    }

    @Test fun acceptsOfficialMinimalPersonalMembershipOrganization() {
        val response = json.decodeFromString<MembershipsResponse>(
            """{"data":[{"id":"member","organization":{"id":"org","name":"Acme","currency":"EUR"},"role":"owner"}]}""",
        )

        assertEquals("org", response.data.single().organizationId)
        assertEquals(false, response.data.single().organization.preventOverlappingTimeEntries)
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

    @Test fun stopRequestOnlyChangesTheEndTimestamp() {
        val encoded = Json.encodeToString(StopTimeEntryRequest(end = "2026-08-08T12:00:00Z"))

        assertEquals("{\"end\":\"2026-08-08T12:00:00Z\"}", encoded)
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

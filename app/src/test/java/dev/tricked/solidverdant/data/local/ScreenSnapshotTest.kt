package dev.tricked.solidverdant.data.local

import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Organization
import dev.tricked.solidverdant.data.model.User
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenSnapshotTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val snapshot = ScreenSnapshot(
        organizationId = "org",
        user = User("user", "User", "user@example.com"),
        memberships = listOf(Membership("member", "admin", Organization("org", "Org", "EUR"))),
        currentMembershipId = "member",
        timeEntries = emptyList(),
        activeEntry = null,
        projects = emptyList(),
        tasks = emptyList(),
        tags = emptyList(),
        savedAtEpochMs = 42
    )

    @Test fun roundTrip() {
        assertEquals(snapshot, decodeScreenSnapshot(json.encodeToString(snapshot), json))
    }

    @Test fun rejectsVersionMismatch() {
        val encoded = json.encodeToString(snapshot.copy(version = ScreenSnapshot.CURRENT_VERSION + 1))
        assertNull(decodeScreenSnapshot(encoded, json))
    }

    @Test fun rejectsCorruptJson() {
        assertNull(decodeScreenSnapshot("not-json", json))
    }
}

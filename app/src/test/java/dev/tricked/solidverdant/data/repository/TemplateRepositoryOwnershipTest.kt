/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.TemplateDao
import dev.tricked.solidverdant.data.local.db.TemplateEntity
import dev.tricked.solidverdant.data.model.User
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TemplateRepositoryOwnershipTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: TemplateDao
    private lateinit var authDataStore: AuthDataStore
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var repo: TemplateRepository
    private val clock = object : Clock {
        override fun nowMs() = 1234L
    }

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.templateDao()
        authDataStore = AuthDataStore(context)
        settingsDataStore = SettingsDataStore(context)
        repo = TemplateRepository(dao, clock, authDataStore, settingsDataStore)
    }

    @After fun teardown() = db.close()

    private fun user(id: String) = User(id = id, name = "n", email = "e@e")

    private fun row(id: String, org: String, endpoint: String?, userId: String?) = TemplateEntity(
        id = id, organizationId = org, name = id, projectId = null, taskId = null,
        description = null, tagIds = "", billable = false, isFavorite = false,
        sortOrder = 0, createdAtMs = 1L, ownerEndpoint = endpoint, ownerUserId = userId,
    )

    private suspend fun loginAs(endpoint: String, userId: String) {
        authDataStore.saveOAuthConfig(endpoint, "client")
        settingsDataStore.cacheAuth(user(userId), emptyList(), null)
    }

    @Test fun observe_filters_by_owner_endpoint_and_user() = runTest {
        val epA = "https://a.example"
        val epB = "https://b.example"
        dao.upsert(row("a-userA", "org", epA, "userA"))
        dao.upsert(row("a-userB", "org", epA, "userB"))
        dao.upsert(row("b-userA", "org", epB, "userA"))

        loginAs(epA, "userA")

        val visible = repo.observeTemplates("org").first().map { it.id }
        assertEquals(listOf("a-userA"), visible)
    }

    @Test fun create_stamps_current_owner() = runTest {
        val ep = "https://a.example"
        loginAs(ep, "userA")

        val created = repo.createTemplate(
            organizationId = "org",
            name = "T",
            projectId = null,
            taskId = null,
            description = null,
            tagIds = emptyList(),
            billable = false,
            isFavorite = false,
        )

        val persisted = dao.getById(created.id)!!
        assertEquals(ep, persisted.ownerEndpoint)
        assertEquals("userA", persisted.ownerUserId)
    }

    @Test fun claim_stamps_null_rows_exactly_once() = runTest {
        val ep = "https://a.example"
        dao.upsert(row("legacy", "org", null, null))

        val firstClaim = repo.claimUnowned(ep, "userA")
        assertEquals(1, firstClaim)
        assertEquals("userA", dao.getById("legacy")!!.ownerUserId)
        assertEquals(ep, dao.getById("legacy")!!.ownerEndpoint)

        val secondClaim = repo.claimUnowned(ep, "userB")
        assertEquals(0, secondClaim)
        assertEquals("userA", dao.getById("legacy")!!.ownerUserId)
    }
}

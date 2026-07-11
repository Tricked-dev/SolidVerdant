/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.repository

import dev.tricked.solidverdant.data.local.db.TemplateEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateMappingTest {

    @Test fun `tag codec round-trips a list`() {
        val ids = listOf("a", "b", "c")
        assertEquals(ids, TemplateTagCodec.decode(TemplateTagCodec.encode(ids)))
    }

    @Test fun `tag codec encodes empty as blank string`() {
        assertEquals("", TemplateTagCodec.encode(emptyList()))
    }

    @Test fun `tag codec decodes blank as empty and trims noise`() {
        assertEquals(emptyList<String>(), TemplateTagCodec.decode(""))
        assertEquals(emptyList<String>(), TemplateTagCodec.decode("   "))
        assertEquals(listOf("a", "b"), TemplateTagCodec.decode(" a , ,b "))
    }

    @Test fun `entity maps to model with decoded tags`() {
        val entity = TemplateEntity(
            id = "t1",
            organizationId = "org",
            name = "Standup",
            projectId = "p1",
            taskId = "k1",
            description = "Daily",
            tagIds = "g1,g2",
            billable = true,
            isFavorite = true,
            sortOrder = 3,
            createdAtMs = 42L,
        )
        val model = entity.toModel()
        assertEquals("t1", model.id)
        assertEquals(listOf("g1", "g2"), model.tagIds)
        assertEquals(true, model.isFavorite)
        assertEquals(3, model.sortOrder)
        // Round-trip back to entity preserves the serialized tag column.
        assertEquals(entity, model.toEntity())
    }

    @Test fun `model with no tags maps to empty entity column`() {
        val entity = EntryTemplate(
            id = "t2",
            organizationId = "org",
            name = null,
            projectId = null,
            taskId = null,
            description = null,
            tagIds = emptyList(),
            billable = false,
            isFavorite = false,
            sortOrder = 0,
            createdAtMs = 0L,
        ).toEntity()
        assertEquals("", entity.tagIds)
    }
}

package dev.tricked.solidverdant.ui.templates

import dev.tricked.solidverdant.data.repository.EntryTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateOrderingTest {

    private fun template(
        id: String,
        isFavorite: Boolean = false,
        sortOrder: Int = 0,
        createdAtMs: Long = 0L,
    ) = EntryTemplate(
        id = id,
        organizationId = "org",
        name = null,
        projectId = null,
        taskId = null,
        description = null,
        tagIds = emptyList(),
        billable = false,
        isFavorite = isFavorite,
        sortOrder = sortOrder,
        createdAtMs = createdAtMs,
    )

    @Test fun `quick start puts favorites first in their pinned order`() {
        val templates = listOf(
            template("fav-b", isFavorite = true, sortOrder = 1, createdAtMs = 10),
            template("fav-a", isFavorite = true, sortOrder = 0, createdAtMs = 20),
            template("recent", isFavorite = false, sortOrder = 0, createdAtMs = 5),
        )
        assertEquals(
            listOf("fav-a", "fav-b", "recent"),
            TemplateOrdering.forQuickStart(templates).map { it.id },
        )
    }

    @Test fun `quick start orders non-favorites as most-recent-first`() {
        val templates = listOf(
            template("old", createdAtMs = 100),
            template("newest", createdAtMs = 300),
            template("middle", createdAtMs = 200),
        )
        assertEquals(
            listOf("newest", "middle", "old"),
            TemplateOrdering.forQuickStart(templates).map { it.id },
        )
    }

    @Test fun `quick start keeps favorites ahead of newer recents`() {
        val templates = listOf(
            template("recent-new", isFavorite = false, createdAtMs = 999),
            template("fav-old", isFavorite = true, sortOrder = 0, createdAtMs = 1),
        )
        assertEquals(
            listOf("fav-old", "recent-new"),
            TemplateOrdering.forQuickStart(templates).map { it.id },
        )
    }

    @Test fun `quick start respects the limit`() {
        val templates = (1..20).map { template("t$it", createdAtMs = it.toLong()) }
        val result = TemplateOrdering.forQuickStart(templates, limit = 3)
        assertEquals(3, result.size)
        // Most-recent-first, so the three newest.
        assertEquals(listOf("t20", "t19", "t18"), result.map { it.id })
    }

    @Test fun `quick start is deterministic when timestamps tie`() {
        val templates = listOf(
            template("b", createdAtMs = 5),
            template("a", createdAtMs = 5),
        )
        // Ties fall back to id so ordering never flickers.
        assertEquals(listOf("a", "b"), TemplateOrdering.forQuickStart(templates).map { it.id })
    }

    @Test fun `manage order mirrors the dao - favorites then sortOrder then created`() {
        val templates = listOf(
            template("n2", isFavorite = false, sortOrder = 5, createdAtMs = 2),
            template("f2", isFavorite = true, sortOrder = 1, createdAtMs = 9),
            template("f1", isFavorite = true, sortOrder = 0, createdAtMs = 9),
            template("n1", isFavorite = false, sortOrder = 5, createdAtMs = 1),
        )
        assertEquals(
            listOf("f1", "f2", "n1", "n2"),
            TemplateOrdering.forManage(templates).map { it.id },
        )
    }
}

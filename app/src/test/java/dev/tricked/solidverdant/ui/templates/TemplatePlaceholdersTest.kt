/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.templates

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplatePlaceholdersTest {

    @Test fun `extract returns distinct tokens in order`() {
        assertEquals(
            listOf("topic", "client"),
            TemplatePlaceholders.extract("Review: {topic} for {client} ({topic})"),
        )
    }

    @Test fun `extract ignores blank and empty descriptions`() {
        assertEquals(emptyList<String>(), TemplatePlaceholders.extract(null))
        assertEquals(emptyList<String>(), TemplatePlaceholders.extract(""))
        assertEquals(emptyList<String>(), TemplatePlaceholders.extract("no placeholders here"))
    }

    @Test fun `fill substitutes provided values`() {
        assertEquals(
            "Review: Q3 for Acme",
            TemplatePlaceholders.fill("Review: {topic} for {client}", mapOf("topic" to "Q3", "client" to "Acme")),
        )
    }

    @Test fun `fill leaves unfilled tokens verbatim`() {
        assertEquals(
            "Review: {topic}",
            TemplatePlaceholders.fill("Review: {topic}", mapOf("topic" to "  ")),
        )
        assertEquals(
            "Review: {topic}",
            TemplatePlaceholders.fill("Review: {topic}", emptyMap()),
        )
    }

    @Test fun `fill returns non-placeholder descriptions unchanged`() {
        assertEquals("plain text", TemplatePlaceholders.fill("plain text", mapOf("x" to "y")))
        assertEquals(null, TemplatePlaceholders.fill(null, mapOf("x" to "y")))
    }
}

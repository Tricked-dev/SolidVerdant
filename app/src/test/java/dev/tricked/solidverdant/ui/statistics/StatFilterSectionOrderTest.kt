/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Test

class StatFilterSectionOrderTest {
    @Test
    fun high_value_filters_precede_long_catalogue_lists() {
        assertEquals(
            listOf(
                StatFilterSection.BILLABLE,
                StatFilterSection.TASKS,
                StatFilterSection.TAGS,
                StatFilterSection.CLIENTS,
                StatFilterSection.PROJECTS,
            ),
            statFilterSectionOrder,
        )
    }

    @Test
    fun project_search_is_case_insensitive_and_keeps_only_matches() {
        val projects = listOf("1" to "Turning", "2" to "Milling")

        assertEquals(listOf("1" to "Turning"), filterProjectOptions(projects, " turn "))
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.repository

import dev.tricked.solidverdant.data.model.TimeEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.YearMonth
import java.time.ZoneId

/**
 * Read-only, cache-backed stream of time entries.
 *
 * Implemented by the Room-backed [TimeEntryRepository].
 */
interface TimeEntryReader {
    fun observeTimeEntries(organizationId: String): Flow<List<TimeEntry>>

    /**
     * Per-entry local sync state for surfaces that render cached time entries.
     *
     * The default keeps lightweight readers (and isolated UI tests) source-compatible; the Room
     * repository overrides it with the durable outbox/conflict stream.
     */
    fun observeSyncOperations(organizationId: String): Flow<List<TimeEntryRepository.SyncOperation>> = flowOf(emptyList())

    suspend fun loadMonth(organizationId: String, memberId: String, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()) = Unit
}

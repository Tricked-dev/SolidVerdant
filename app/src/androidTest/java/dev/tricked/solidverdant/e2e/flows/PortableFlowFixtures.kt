/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.flows

import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.e2e.E2eRule
import java.time.Instant

/** Build a completed entry from the selected backend's account instead of mock-owned constants. */
internal fun E2eRule.completedFixtureEntry(
    logicalId: String = "seed-entry-1",
    description: String = "Seeded work",
    start: Instant = Instant.ofEpochMilli(testClock.nowMs).minusSeconds(7_200),
    durationSeconds: Int = 3_600,
): TimeEntry = TimeEntry(
    id = logicalId,
    description = description,
    userId = session.userId,
    start = start.toString(),
    end = start.plusSeconds(durationSeconds.toLong()).toString(),
    duration = durationSeconds,
    organizationId = session.organizationId,
)

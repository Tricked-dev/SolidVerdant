/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.sync

import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.local.db.RateLimitMarker
import dev.tricked.solidverdant.data.repository.TimeEntryRepository.EntrySyncStatus
import dev.tricked.solidverdant.data.repository.TimeEntryRepository.SyncOperation
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncReasonMappingTest {
    private fun op(status: EntrySyncStatus, error: String?) =
        SyncOperation(entryId = "e", type = OutboxOpType.UPDATE, status = status, attemptCount = 0, error = error)

    @Test fun rate_limit_marker_maps_to_slow_down_copy() {
        assertEquals(R.string.sync_reason_rate_limited, failureReasonRes(RateLimitMarker.encode(null)))
        assertEquals(R.string.sync_reason_rate_limited, failureReasonRes(RateLimitMarker.encode(30)))
        assertEquals(R.string.sync_reason_offline, failureReasonRes("Temporary server or network error; retry scheduled"))
    }

    @Test fun pending_rows_explain_only_after_the_worker_wrote_a_reason() {
        assertEquals(R.string.sync_pending_item, pendingReasonRes(op(EntrySyncStatus.PENDING, null)))
        assertEquals(R.string.sync_pending_item, pendingReasonRes(op(EntrySyncStatus.RETRYING, null)))
        assertEquals(R.string.sync_reason_rate_limited, pendingReasonRes(op(EntrySyncStatus.RETRYING, RateLimitMarker.encode(60))))
    }
}

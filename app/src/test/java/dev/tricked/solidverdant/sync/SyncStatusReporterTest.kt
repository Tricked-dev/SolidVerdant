/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.sync

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusReporterTest {
    @Test fun set_emits_new_status() = runTest {
        val reporter = SyncStatusReporter()
        reporter.status.test {
            assertEquals(SyncStatus.Idle, awaitItem())
            reporter.set(SyncStatus.Error("nope"))
            assertEquals(SyncStatus.Error("nope"), awaitItem())
        }
    }
}

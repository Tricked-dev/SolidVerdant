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

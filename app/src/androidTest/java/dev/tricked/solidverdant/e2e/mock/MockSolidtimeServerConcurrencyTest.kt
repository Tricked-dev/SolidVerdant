/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.mock

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
class MockSolidtimeServerConcurrencyTest {

    @Test
    fun callsMatchingCanReadWhileRequestsAreRecorded() {
        val server = MockSolidtimeServer()
        repeat(INITIAL_CALL_COUNT) { index ->
            server.recordedCalls += RecordedCall("GET", "/time-entries/$index", "")
        }

        val writerReady = CountDownLatch(1)
        val startRace = CountDownLatch(1)
        val writer = Thread {
            writerReady.countDown()
            startRace.await()
            repeat(WRITE_COUNT) { offset ->
                server.recordedCalls += RecordedCall("GET", "/time-entries/${INITIAL_CALL_COUNT + offset}", "")
                Thread.yield()
            }
        }

        writer.start()
        writerReady.await()
        startRace.countDown()
        try {
            repeat(READ_COUNT) {
                assertTrue(server.callsMatching("GET", "/time-entries").isNotEmpty())
            }
        } finally {
            writer.join()
        }
    }

    private companion object {
        const val INITIAL_CALL_COUNT = 10_000
        const val READ_COUNT = 25
        const val WRITE_COUNT = 10_000
    }
}

package dev.tricked.solidverdant.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.OutboxEntity
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.remote.FakeRemoteDataSource
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {
    private lateinit var db: AppDatabase
    private lateinit var remote: FakeRemoteDataSource
    private val json = Json { encodeDefaults = true }
    private val clock = object : Clock { override fun nowMs() = 1L }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        remote = FakeRemoteDataSource()
    }
    @After fun teardown() = db.close()

    private fun buildWorker(status: SyncStatusReporter = SyncStatusReporter()) =
        TestListenableWorkerBuilder<SyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(appContext: android.content.Context, workerClassName: String, params: androidx.work.WorkerParameters) =
                    SyncWorker(appContext, params, db.outboxDao(), db.timeEntryDao(), remote, json, clock, status)
            }).build()

    @Test fun start_op_reconciles_temp_id_to_server_id() = runTest {
        db.outboxDao().insert(OutboxEntity(
            opType = OutboxOpType.START, organizationId = "org1", timeEntryId = "local-1",
            createdAtMs = 1L, payloadJson = json.encodeToString(
                StartPayload("m1", "u1", "p1", null, "work", emptyList()))))
        remote.startResult = { it.copy(id = "server-1") }

        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(db.outboxDao().peekAll().isEmpty())
        // temp id row was rekeyed
        assertNull(db.timeEntryDao().getById("local-1"))
    }

    @Test fun transient_failure_returns_retry_and_keeps_op() = runTest {
        remote.failNextWrite = true
        db.outboxDao().insert(OutboxEntity(
            opType = OutboxOpType.DELETE, organizationId = "org1", timeEntryId = "server-1",
            createdAtMs = 1L, payloadJson = "{}"))
        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(1, db.outboxDao().peekAll().size)
    }
}

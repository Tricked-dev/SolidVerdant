/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e

import android.content.Context
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.components.SingletonComponent
import dev.tricked.solidverdant.MainActivity
import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.OutboxEntity
import dev.tricked.solidverdant.data.local.db.OutboxOpType
import dev.tricked.solidverdant.data.local.db.TemplateEntity
import dev.tricked.solidverdant.data.local.db.TimeEntryEntity
import dev.tricked.solidverdant.data.local.db.toModel
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.remote.RemoteDataSource
import dev.tricked.solidverdant.e2e.di.TestClock
import dev.tricked.solidverdant.e2e.mock.MockSolidtimeServer
import dev.tricked.solidverdant.sync.SyncScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Composed on-device E2E harness. Add to a `@HiltAndroidTest` test as:
 *
 * ```
 * @get:Rule val e2e = E2eRule(this)
 * ```
 *
 * Before the app launches it: selects the mock or real backend, seeds [AuthDataStore] so the app
 * boots LOGGED IN and pointed at that backend, and initializes a test [WorkManager] backed by the app's
 * [HiltWorkerFactory] and a [SynchronousExecutor] so [dev.tricked.solidverdant.sync.SyncWorker] runs
 * deterministically.
 *
 * Because the app graph (Hilt DI + real ViewModels) is what we want to exercise, the harness uses an
 * empty Compose rule and launches the real [MainActivity] via [ActivityScenario] in [launchApp] —
 * this lets a test preset the mock's catalogue BEFORE the activity reads it.
 *
 * Exposes [testClock] and the WorkManager [testDriver] plus [runPendingSync] so tests can drive sync.
 * State (DataStore + WorkManager) is reset between tests.
 */
class E2eRule(private val test: Any) : TestRule {

    val composeRule: ComposeTestRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext<Context>()

    private val hiltRule = HiltAndroidRule(test)

    private val entryPoint: HarnessEntryPoint
        get() = EntryPointAccessors.fromApplication(context, HarnessEntryPoint::class.java)

    val testClock: TestClock get() = entryPoint.testClock()

    private lateinit var authDataStore: AuthDataStore
    private lateinit var backend: E2eBackend
    private lateinit var currentSession: E2eSession
    private var prepared = false
    private var scenario: ActivityScenario<MainActivity>? = null

    val session: E2eSession get() = currentSession

    val testDriver get() = WorkManagerTestInitHelper.getTestDriver(context)

    /** JUnit @EntryPoint to reach singletons that aren't @Inject-able into the test directly. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HarnessEntryPoint {
        fun authDataStore(): AuthDataStore
        fun workerFactory(): HiltWorkerFactory
        fun testClock(): TestClock
        fun database(): AppDatabase
        fun settingsDataStore(): SettingsDataStore
        fun remoteDataSource(): RemoteDataSource
    }

    /** Configuration that must run after Hilt is up but before the activity launches. */
    private val setupRule = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                hiltRule.inject()
                authDataStore = entryPoint.authDataStore()
                backend = when (InstrumentationRegistry.getArguments().getString(BACKEND_ARGUMENT)?.lowercase() ?: MOCK_BACKEND) {
                    MOCK_BACKEND -> MockE2eBackend()
                    REAL_BACKEND -> RealSolidtimeE2eBackend(context, entryPoint.remoteDataSource())
                    else -> error("Unknown E2E backend; use -e $BACKEND_ARGUMENT $MOCK_BACKEND|$REAL_BACKEND")
                }
                currentSession = backend.open()
                testClock.reset()

                // Boot the app logged-in and aimed at the mock backend.
                runBlocking {
                    // Room is process/package persistent and may contain a previous stress run.
                    // Every E2E test owns its complete world, so clear account data before the
                    // test seeds local-only fixtures or launches the activity.
                    entryPoint.database().clearAllTables()
                    entryPoint.settingsDataStore().clearCachedData()
                    authDataStore.clearAll()
                    authDataStore.saveOAuthConfig(currentSession.baseUrl, currentSession.clientId)
                    authDataStore.saveTokens(currentSession.accessToken, currentSession.refreshToken)
                    authDataStore.saveCurrentMembershipId(currentSession.membershipId)
                }

                // Deterministic WorkManager: HiltWorkerFactory so SyncWorker's deps resolve, and
                // a synchronous executor so enqueued work runs inline once constraints are met.
                WorkManagerTestInitHelper.initializeTestWorkManager(
                    context,
                    Configuration.Builder()
                        .setWorkerFactory(entryPoint.workerFactory())
                        .setExecutor(SynchronousExecutor())
                        .build(),
                )

                try {
                    base.evaluate()
                } finally {
                    scenario?.close()
                    runBlocking { authDataStore.clearAll() }
                    // WorkManager is process-global. Close its test database so the next test
                    // can initialize it with that test's fresh Hilt graph and worker factory.
                    // Without this, workers in later tests retain dependencies from the first
                    // test component and can tear down the activity mid-flow.
                    WorkManagerTestInitHelper.closeWorkDatabase()
                    backend.close()
                }
            }
        }
    }

    override fun apply(base: Statement, description: Description): Statement = RuleChain.outerRule(hiltRule)
        .around(setupRule)
        .around(composeRule)
        .apply(base, description)

    // ---- Test-facing helpers ------------------------------------------------------------------

    /** Replace the complete server world before launching the real app. */
    fun prepare(fixture: E2eFixture): E2eFixtureHandle = runBlocking {
        check(scenario == null) { "Prepare the E2E fixture before launching the app" }
        backend.prepare(fixture).also { prepared = true }
    }

    fun serverSnapshot(): E2eServerSnapshot = runBlocking { backend.snapshot() }

    /** Read the selected account's project/task/tag catalogue before a metadata flow starts. */
    fun catalogSnapshot(): E2eCatalog = runBlocking { backend.catalog() }

    /**
     * Poll server state at a bounded cadence outside Compose's tight [ComposeTestRule.waitUntil]
     * loop. The satisfying snapshot is returned so callers do not immediately spend another
     * control-plane request fetching the state they just observed.
     */
    fun awaitServer(
        timeoutMs: Long,
        pollDelayMs: Long = DEFAULT_SERVER_POLL_DELAY_MS,
        driveSync: Boolean = false,
        predicate: (E2eServerSnapshot) -> Boolean,
    ): E2eServerSnapshot = runBlocking {
        require(timeoutMs > 0L) { "timeoutMs must be positive" }
        require(pollDelayMs > 0L) { "pollDelayMs must be positive" }
        val startedAt = System.nanoTime()
        var elapsedMs = 0L
        while (elapsedMs <= timeoutMs) {
            if (driveSync) runPendingSync()
            val snapshot = backend.snapshot()
            if (predicate(snapshot)) return@runBlocking snapshot

            elapsedMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND
            val remainingMs = timeoutMs - elapsedMs
            if (remainingMs > 0L) delay(minOf(pollDelayMs, remainingMs))
        }
        throw AssertionError("Server state did not satisfy the predicate within $timeoutMs ms")
    }

    fun createOnServer(entry: TimeEntry): E2eFixtureHandle = runBlocking { backend.create(entry) }

    fun updateOnServer(logicalId: String, transform: (TimeEntry) -> TimeEntry): E2eFixtureHandle = runBlocking {
        val existing = backend.snapshot().entry(logicalId)
            ?: error("No server entry is mapped to logical fixture '$logicalId'")
        backend.update(logicalId, transform(existing))
    }

    /** Explicit escape hatch for mock-only paging, stress, and fault-injection tests. */
    fun requireMockBackend(): MockSolidtimeServer {
        prepared = true
        return backend.mockServerOrNull() ?: error("This E2E flow requires the mock backend")
    }

    /** Launch the real [MainActivity]. Call after [prepare] or [requireMockBackend]. */
    fun launchApp(): ActivityScenario<MainActivity> {
        check(prepared) { "Call prepare(E2eFixture...) before launchApp()" }
        return ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
    }

    /**
     * Deterministically run any sync work the app enqueued: meet the constraints on the unique
     * outbox-sync work so the [SynchronousExecutor] drains the outbox against the mock backend.
     */
    fun runPendingSync() {
        val wm = WorkManager.getInstance(context)
        val infos = wm.getWorkInfosForUniqueWork(SyncScheduler.UNIQUE_NAME).get()
        infos.forEach { info -> testDriver?.setAllConstraintsMet(info.id) }
    }

    fun pendingOutboxCount(): Int = runBlocking { entryPoint.database().outboxDao().peekPending().size }

    fun localEntry(handle: E2eFixtureHandle): TimeEntry? = runBlocking {
        val id = handle.serverId ?: return@runBlocking null
        val dao = entryPoint.database().timeEntryDao()
        dao.getById(id)?.let { entity -> entity.toModel(dao.tagIdsFor(entity.id).map(::Tag)) }
    }

    /** Poll the real Room row so cross-surface tests can prove eventual server -> DAO convergence. */
    fun awaitLocalEntry(entryId: String, timeoutMs: Long, predicate: (TimeEntryEntity) -> Boolean): TimeEntryEntity = runBlocking {
        require(timeoutMs > 0L) { "timeoutMs must be positive" }
        val startedAt = System.nanoTime()
        var last: TimeEntryEntity? = null
        while ((System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND <= timeoutMs) {
            last = entryPoint.database().timeEntryDao().getById(entryId)
            if (last != null && predicate(last)) return@runBlocking last
            delay(LOCAL_ENTRY_POLL_DELAY_MS)
        }
        throw AssertionError(
            "Local entry $entryId did not reach the expected state; " +
                "present=${last != null}, syncState=${last?.syncState}, pendingDelete=${last?.pendingDelete}",
        )
    }

    /** Whether the optimistic local STOP has committed before the worker is allowed to run. */
    fun hasPendingStop(entryId: String): Boolean = runBlocking {
        entryPoint.database().outboxDao().peekAll().any {
            it.timeEntryId == entryId && it.opType == OutboxOpType.STOP
        }
    }

    /** Seed a large local-only collection that has no server endpoint. */
    fun seedTemplates(count: Int = 180) = runBlocking {
        entryPoint.database().templateDao().upsertAll(
            (0 until count).map { index ->
                TemplateEntity(
                    id = "stress-template-$index",
                    organizationId = MockSolidtimeServer.DEFAULT_ORG_ID,
                    name = "Template ${index.toString().padStart(3, '0')}",
                    projectId = "project-${index % 120}",
                    taskId = "task-${index % 480}",
                    description = "Stress template $index",
                    tagIds = "tag-${index % 160}",
                    billable = index % 2 == 0,
                    isFavorite = index < 20,
                    sortOrder = index,
                    createdAtMs = index.toLong(),
                )
            },
        )
    }

    /** Seed one visible terminal sync failure without running a worker or touching the mock server. */
    fun seedFailedSync(entryId: String = "failed-entry") = runBlocking {
        entryPoint.database().outboxDao().insert(
            OutboxEntity(
                opType = OutboxOpType.UPDATE,
                organizationId = MockSolidtimeServer.DEFAULT_ORG_ID,
                timeEntryId = entryId,
                payloadJson = "{}",
                createdAtMs = testClock.nowMs(),
                attemptCount = 1,
                lastError = "422 rejected",
                deadLettered = true,
            ),
        )
    }

    companion object {
        private const val BACKEND_ARGUMENT = "e2eBackend"
        private const val MOCK_BACKEND = "mock"
        private const val REAL_BACKEND = "real"
        private const val DEFAULT_SERVER_POLL_DELAY_MS = 500L
        private const val LOCAL_ENTRY_POLL_DELAY_MS = 100L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

package dev.tricked.solidverdant.e2e

import android.content.Context
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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
import dev.tricked.solidverdant.data.local.db.TemplateEntity
import dev.tricked.solidverdant.e2e.di.TestClock
import dev.tricked.solidverdant.e2e.mock.MockSolidtimeServer
import dev.tricked.solidverdant.sync.SyncScheduler
import kotlinx.coroutines.runBlocking
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import androidx.hilt.work.HiltWorkerFactory

/**
 * Composed on-device E2E harness. Add to a `@HiltAndroidTest` test as:
 *
 * ```
 * @get:Rule val e2e = E2eRule(this)
 * ```
 *
 * Before the app launches it: starts [MockSolidtimeServer], seeds [AuthDataStore] so the app boots
 * LOGGED IN and pointed at the mock, and initializes a test [WorkManager] backed by the app's
 * [HiltWorkerFactory] and a [SynchronousExecutor] so [dev.tricked.solidverdant.sync.SyncWorker] runs
 * deterministically.
 *
 * Because the app graph (Hilt DI + real ViewModels) is what we want to exercise, the harness uses an
 * empty Compose rule and launches the real [MainActivity] via [ActivityScenario] in [launchApp] —
 * this lets a test preset the mock's catalogue BEFORE the activity reads it.
 *
 * Exposes [mockServer], [testClock], and the WorkManager [testDriver] plus [runPendingSync] so tests
 * can drive sync. State (DataStore + WorkManager) is reset between tests.
 */
class E2eRule(private val test: Any) : TestRule {

    val mockServer = MockSolidtimeServer()

    val composeRule: ComposeTestRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext<Context>()

    private val hiltRule = HiltAndroidRule(test)

    private val entryPoint: HarnessEntryPoint
        get() = EntryPointAccessors.fromApplication(context, HarnessEntryPoint::class.java)

    val testClock: TestClock get() = entryPoint.testClock()

    private lateinit var authDataStore: AuthDataStore
    private var scenario: ActivityScenario<MainActivity>? = null

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
    }

    /** Configuration that must run after Hilt is up but before the activity launches. */
    private val setupRule = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement =
            object : Statement() {
                override fun evaluate() {
                    hiltRule.inject()
                    authDataStore = entryPoint.authDataStore()

                    mockServer.start()
                    testClock.reset()

                    // Boot the app logged-in and aimed at the mock backend.
                    runBlocking {
                        // Room is process/package persistent and may contain a previous stress run.
                        // Every E2E test owns its complete world, so clear account data before the
                        // test seeds local-only fixtures or launches the activity.
                        entryPoint.database().clearAllTables()
                        entryPoint.settingsDataStore().clearCachedData()
                        authDataStore.clearAll()
                        authDataStore.saveOAuthConfig(mockServer.baseUrl(), "test-client")
                        authDataStore.saveTokens("test-access-token", "test-refresh-token")
                        authDataStore.saveCurrentMembershipId(MockSolidtimeServer.DEFAULT_MEMBERSHIP_ID)
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
                        mockServer.shutdown()
                    }
                }
            }
    }

    override fun apply(base: Statement, description: Description): Statement =
        RuleChain.outerRule(hiltRule)
            .around(setupRule)
            .around(composeRule)
            .apply(base, description)

    // ---- Test-facing helpers ------------------------------------------------------------------

    /** Launch the real [MainActivity]. Call AFTER presetting [mockServer] catalogue. */
    fun launchApp(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { scenario = it }

    /**
     * Deterministically run any sync work the app enqueued: meet the constraints on the unique
     * outbox-sync work so the [SynchronousExecutor] drains the outbox against the mock backend.
     */
    fun runPendingSync() {
        val wm = WorkManager.getInstance(context)
        val infos = wm.getWorkInfosForUniqueWork(SyncScheduler.UNIQUE_NAME).get()
        infos.forEach { info -> testDriver?.setAllConstraintsMet(info.id) }
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
}

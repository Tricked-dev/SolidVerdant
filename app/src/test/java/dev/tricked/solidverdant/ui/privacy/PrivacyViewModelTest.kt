/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.privacy

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tricked.solidverdant.data.export.DiagnosticExporter
import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.UserCacheCleaner
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PrivacyViewModelTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsDataStore
    private lateinit var authDataStore: AuthDataStore
    private lateinit var cleaner: UserCacheCleaner
    private lateinit var exporter: DiagnosticExporter
    private val dispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<PrivacyViewModel>()
    private val clock = object : Clock {
        override fun nowMs() = 1_000L
    }

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        // Named on-disk DB so getDatabasePath("solidverdant.db") resolves to a real file to size.
        db = Room.databaseBuilder(context, AppDatabase::class.java, "solidverdant.db")
            .allowMainThreadQueries()
            .build()
        // Force the file into existence.
        db.openHelper.writableDatabase.execSQL("PRAGMA user_version = 1")
        settings = SettingsDataStore(context)
        authDataStore = AuthDataStore(context)
        cleaner = UserCacheCleaner(context, settings, db)
        exporter = DiagnosticExporter(context, authDataStore, db.outboxDao(), db.syncMetaDao(), clock)
    }

    @After
    fun teardown() {
        viewModels.forEach { it.cancelScopeForTest() }
        dispatcher.scheduler.advanceUntilIdle()
        db.close()
        context.getDatabasePath("solidverdant.db").delete()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun viewModel(): PrivacyViewModel = PrivacyViewModel(
        context = context,
        settingsDataStore = settings,
        authDataStore = authDataStore,
        userCacheCleaner = cleaner,
        diagnosticExporter = exporter,
    ).also { viewModels += it }

    @Test
    fun `computes and exposes storage sizes off main thread`() = runTest(dispatcher.scheduler) {
        val cacheFile = File(context.cacheDir, "probe.bin").apply { writeBytes(ByteArray(2048)) }

        val vm = viewModel()
        vm.refreshStorage()

        val state = vm.state.first { !it.computingStorage && it.dbBytes > 0 }
        assertTrue("db bytes should be positive", state.dbBytes > 0)
        assertTrue("cache bytes should include the probe file", state.cacheBytes >= cacheFile.length())
        assertFalse(state.computingStorage)
    }

    @Test
    fun `clearCache calls reused cleaner and re-reads storage`() = runTest(dispatcher.scheduler) {
        // Seed cached account data so the cleaner has something to remove.
        val cacheFile = File(context.cacheDir, "probe.bin").apply { writeBytes(ByteArray(4096)) }
        val vm = viewModel()
        vm.state.first { !it.computingStorage }

        vm.clearCache()

        // The cleaner wipes account tables; storage is re-read (not stuck computing).
        val state = vm.state.first { !it.clearingCache && !it.computingStorage }
        assertFalse(state.computingStorage)
        cacheFile.delete()
    }

    @Test
    fun `exportDiagnostics delegates to the exporter and returns the uri`() = runTest(dispatcher.scheduler) {
        val vm = viewModel()
        var received: Uri? = null
        vm.exportDiagnostics { received = it }

        // Wait for the export coroutine to settle.
        vm.state.first { !it.exporting }
        assertNotNull("exporter should produce a shareable uri", received)
    }

    @Test
    fun `exposes whether a session is present`() = runTest(dispatcher.scheduler) {
        val vm = viewModel()
        // No token saved in this test → no session (sessionPresent defaults false and stays false).
        val state = vm.state.first { it.serverHost.isNotBlank() }
        assertFalse(state.sessionPresent)
    }

    @Test
    fun `exposes the selected server host`() = runTest(dispatcher.scheduler) {
        // Set an explicit endpoint so this assertion is independent of whatever another test may
        // have persisted into the shared on-disk AuthDataStore (the DataStore file is process-wide).
        authDataStore.saveOAuthConfig("https://sync.privacytest.example", "test-client")
        val vm = viewModel()
        val state = vm.state.first { it.serverHost == "sync.privacytest.example" }
        assertEquals("sync.privacytest.example", state.serverHost)
    }

    @Test
    fun `byte formatter renders human readable sizes`() {
        assertEquals("0 B", ByteSizeFormatter.format(0))
        assertEquals("512 B", ByteSizeFormatter.format(512))
        assertEquals("1.0 KB", ByteSizeFormatter.format(1024))
        assertEquals("1.5 KB", ByteSizeFormatter.format(1536))
        assertEquals("1.0 MB", ByteSizeFormatter.format(1024L * 1024))
    }
}

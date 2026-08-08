/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.privacy

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
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
    private val dispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<PrivacyViewModel>()
    private var endpoint = "https://app.solidtime.io"
    private var sessionPresent = false
    private var cacheProbe: File? = null
    private var clearCalls = 0
    private var exportCalls = 0
    private var shareIntentCalls = 0
    private val exportedUri = Uri.parse("content://solidverdant.test/diagnostics.zip")

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getDatabasePath("solidverdant.db").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(1024))
        }
    }

    @After
    fun teardown() {
        viewModels.forEach { it.cancelScopeForTest() }
        dispatcher.scheduler.advanceUntilIdle()
        cacheProbe?.delete()
        val database = context.getDatabasePath("solidverdant.db")
        listOf(database, File(database.path + "-wal"), File(database.path + "-shm"), File(database.path + "-journal"))
            .forEach { it.delete() }
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun viewModel(): PrivacyViewModel = PrivacyViewModel(
        context = context,
        readEndpoint = { endpoint },
        readSessionPresent = { sessionPresent },
        clearUserCache = {
            clearCalls += 1
            cacheProbe?.delete()
        },
        exportDiagnosticBundle = {
            exportCalls += 1
            exportedUri
        },
        buildShareIntent = { uri ->
            shareIntentCalls += 1
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri)
        },
        storageDispatcher = dispatcher,
    ).also { viewModels += it }

    @Test
    fun `computes and exposes storage sizes off main thread`() = runTest(dispatcher.scheduler) {
        val cacheFile = File(context.cacheDir, "privacy-probe.bin").apply { writeBytes(ByteArray(2048)) }
        cacheProbe = cacheFile

        val vm = viewModel()

        val state = vm.state.first { !it.computingStorage && it.dbBytes > 0 }
        assertTrue("db bytes should be positive", state.dbBytes > 0)
        assertTrue("cache bytes should include the probe file", state.cacheBytes >= cacheFile.length())
        assertFalse(state.computingStorage)
    }

    @Test
    fun `clearCache calls reused cleaner and re-reads storage`() = runTest(dispatcher.scheduler) {
        val cacheFile = File(context.cacheDir, "privacy-probe.bin").apply { writeBytes(ByteArray(4096)) }
        cacheProbe = cacheFile
        val vm = viewModel()
        val before = vm.state.first { !it.computingStorage }

        vm.clearCache()

        val state = vm.state.first {
            !it.clearingCache && !it.computingStorage && it.cacheBytes < before.cacheBytes
        }
        assertEquals(1, clearCalls)
        assertFalse(cacheFile.exists())
        assertFalse(state.computingStorage)
    }

    @Test
    fun `exportDiagnostics delegates to the exporter and returns the uri`() = runTest(dispatcher.scheduler) {
        val vm = viewModel()
        var received: Uri? = null
        vm.exportDiagnostics { received = it }

        vm.state.first { !it.exporting }
        assertEquals(1, exportCalls)
        assertEquals(exportedUri, received)
        assertNotNull("exporter should produce a shareable uri", received)
    }

    @Test
    fun `shareIntentFor delegates to the exporter intent builder`() {
        val vm = viewModel()

        val intent = vm.shareIntentFor(exportedUri)

        assertEquals(1, shareIntentCalls)
        assertEquals(Intent.ACTION_SEND, intent.action)
    }

    @Test
    fun `exposes whether a session is present`() = runTest(dispatcher.scheduler) {
        sessionPresent = true
        val vm = viewModel()
        val state = vm.state.first { it.sessionPresent }
        assertTrue(state.sessionPresent)
    }

    @Test
    fun `exposes the selected server host`() = runTest(dispatcher.scheduler) {
        endpoint = "https://sync.privacytest.example"
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

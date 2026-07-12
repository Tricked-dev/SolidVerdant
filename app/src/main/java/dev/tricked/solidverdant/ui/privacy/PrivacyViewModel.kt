/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.privacy

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tricked.solidverdant.data.export.DiagnosticExporter
import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.local.SettingsDataStore
import dev.tricked.solidverdant.data.local.UserCacheCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Backs [PrivacyScreen] (roadmap #48). This is a *surfacing* view model: it explains what is stored
 * and reuses the existing safe primitives for the destructive actions rather than reimplementing
 * them — [UserCacheCleaner.clear] for the re-syncable cache wipe, [DiagnosticExporter.export] for the
 * privacy-reviewed bundle (#49), and the host's existing logout path (routed via a callback in the
 * screen) for full session revocation.
 *
 * Storage usage is approximate and cheap: it sums the Room DB file(s) and the cache directory off
 * the main thread. It is refreshed after a cache clear so the numbers reflect the wipe.
 */
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val authDataStore: AuthDataStore,
    private val userCacheCleaner: UserCacheCleaner,
    private val diagnosticExporter: DiagnosticExporter,
) : ViewModel() {

    @Stable
    data class State(
        val serverHost: String = "",
        val sessionPresent: Boolean = false,
        val dbBytes: Long = 0L,
        val cacheBytes: Long = 0L,
        val computingStorage: Boolean = true,
        val clearingCache: Boolean = false,
        val exporting: Boolean = false,
    ) {
        val totalBytes: Long get() = dbBytes + cacheBytes
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val endpoint = authDataStore.endpoint.first()
            _state.value = _state.value.copy(serverHost = hostOf(endpoint))
        }
        viewModelScope.launch {
            val present = !authDataStore.accessToken.first().isNullOrEmpty()
            _state.value = _state.value.copy(sessionPresent = present)
        }
        refreshStorage()
    }

    /** Recompute the Room DB + cache directory sizes off the main thread. */
    fun refreshStorage() {
        viewModelScope.launch {
            _state.value = _state.value.copy(computingStorage = true)
            val (dbBytes, cacheBytes) = withContext(Dispatchers.IO) {
                databaseBytes() to directoryBytes(context.cacheDir)
            }
            _state.value = _state.value.copy(
                dbBytes = dbBytes,
                cacheBytes = cacheBytes,
                computingStorage = false,
            )
        }
    }

    /**
     * Clears the re-syncable account cache via the existing [UserCacheCleaner] (preserves templates
     * per SV-011, keeps the user logged in), then re-reads storage. Does NOT touch auth.
     */
    fun clearCache() {
        viewModelScope.launch {
            _state.value = _state.value.copy(clearingCache = true)
            runCatching { userCacheCleaner.clear() }
                .onFailure { Timber.e(it, "Failed to clear cached data") }
            _state.value = _state.value.copy(clearingCache = false)
            refreshStorage()
        }
    }

    /**
     * Builds the diagnostic bundle (#49) and hands the shareable [Uri] back to the screen, which
     * launches the system share sheet. The bundle contains no tokens or work content.
     */
    fun exportDiagnostics(onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(exporting = true)
            runCatching { diagnosticExporter.export() }
                .onSuccess { onReady(it) }
                .onFailure { Timber.e(it, "Failed to export diagnostics") }
            _state.value = _state.value.copy(exporting = false)
        }
    }

    /** Share-sheet intent for a diagnostic bundle [uri], delegating to the exporter (#49). */
    fun shareIntentFor(uri: Uri): Intent = diagnosticExporter.shareIntent(uri)

    /** Sum of the main Room DB file plus its `-wal`/`-shm`/`-journal` sidecars, if present. */
    private fun databaseBytes(): Long {
        val main = context.getDatabasePath(DB_NAME)
        return listOf(main, File(main.path + "-wal"), File(main.path + "-shm"), File(main.path + "-journal"))
            .filter { it.exists() }
            .sumOf { it.length() }
    }

    /** Shallow-recursive sum of file lengths under [dir]; symlinks are followed by File.length only. */
    private fun directoryBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun hostOf(endpoint: String): String {
        val host = runCatching { Uri.parse(endpoint).host }.getOrNull()
        if (!host.isNullOrBlank()) return host
        return endpoint.substringAfter("://").substringBefore("/").ifBlank { endpoint }
    }

    /**
     * Cancels [viewModelScope] for unit tests that install a test Main dispatcher; mirrors the sync
     * VM's teardown so no Main-bound continuation straggles past `Dispatchers.resetMain()`.
     */
    @VisibleForTesting
    internal fun cancelScopeForTest() {
        viewModelScope.coroutineContext[Job]?.cancel()
    }

    private companion object {
        /** Must match the name used in [dev.tricked.solidverdant.di.DatabaseModule]. */
        const val DB_NAME = "solidverdant.db"
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tricked.solidverdant.BuildConfig
import dev.tricked.solidverdant.data.local.AuthDataStore
import dev.tricked.solidverdant.data.local.db.OutboxDao
import dev.tricked.solidverdant.data.local.db.OutboxEntity
import dev.tricked.solidverdant.data.local.db.SyncMetaDao
import dev.tricked.solidverdant.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Android-facing assembler for the privacy-reviewed diagnostic export bundle (roadmap #49). It
 * gathers the build identity, platform, sanitized server host and *categorized* recent sync
 * failures, hands them to the pure [DiagnosticBundle] core, writes the text to a FileProvider-exposed
 * cache file, and returns a shareable [Uri] — mirroring [CsvExporter]'s file/share mechanics.
 *
 * Redaction is enforced *before* the pure core: raw outbox error strings are classified into a safe
 * [DiagnosticErrorCategory] here and the raw text is never passed onward, so the formatter cannot
 * emit descriptions, project names, tokens or any other work content.
 */
@Singleton
class DiagnosticExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authDataStore: AuthDataStore,
    private val outboxDao: OutboxDao,
    private val syncMetaDao: SyncMetaDao,
    private val clock: Clock,
) {

    /** Builds the diagnostic text bundle from live app state, sanitizing every input. */
    suspend fun buildBundleText(): String {
        val endpoint = authDataStore.endpoint.first()
        val host = hostOf(endpoint)
        val isSelfHosted = normalize(endpoint) != normalize(AuthDataStore.DEFAULT_ENDPOINT)

        val ops = outboxDao.peekAll()
        val failures = ops
            .filter { it.deadLettered || it.attemptCount > 0 || it.lastError != null }
            .map { it.toFailure(clock.nowMs()) }
        val failedCount = ops.count { it.deadLettered }
        val pendingCount = ops.count { !it.deadLettered }

        val meta = distinctOrgIds(ops).mapNotNull { syncMetaDao.get(it) }
        val lastFullSyncAtMs = meta.mapNotNull { it.lastFullSyncAtMs.takeIf { ms -> ms > 0L } }.maxOrNull()
        val lastPushAtMs = meta.mapNotNull { it.lastPushAtMs }.maxOrNull()

        return DiagnosticBundle.build(
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            androidRelease = Build.VERSION.RELEASE,
            androidSdk = Build.VERSION.SDK_INT,
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            serverHost = host,
            isSelfHosted = isSelfHosted,
            capabilities = CAPABILITIES,
            pendingCount = pendingCount,
            failedCount = failedCount,
            failures = failures,
            lastFullSyncAtMs = lastFullSyncAtMs,
            lastPushAtMs = lastPushAtMs,
        )
    }

    /**
     * Writes the bundle into `cacheDir/exports/diagnostics-<now>.txt` and returns a content:// URI
     * granted to receiving apps via the app's FileProvider authority (same setup as [CsvExporter]).
     */
    suspend fun export(): Uri = withContext(Dispatchers.IO) {
        val text = buildBundleText()
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "diagnostics-${clock.nowMs()}.txt")
        file.writeText(text, Charsets.UTF_8)
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    /**
     * Share sheet intent for a bundle [uri]: ACTION_SEND, text/plain, with read permission granted
     * to the chosen target. Mirrors the CSV share intent; the bundle holds no tokens or work content.
     */
    fun shareIntent(uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun OutboxEntity.toFailure(nowMs: Long): DiagnosticFailure = DiagnosticFailure(
        opType = opType.name,
        category = DiagnosticBundle.classifyOutboxError(lastError),
        ageDays = max(0L, (nowMs - createdAtMs)) / MILLIS_PER_DAY,
        attempts = attemptCount,
    )

    private fun distinctOrgIds(ops: List<OutboxEntity>): Set<String> = ops.map { it.organizationId }.toSet()

    /**
     * The endpoint host only (e.g. `app.solidtime.io`) — never the scheme, path, query or any token
     * that a full URL could carry. Falls back to the raw string stripped of an obvious scheme if it
     * cannot be parsed as a URI.
     */
    private fun hostOf(endpoint: String): String {
        val host = runCatching { Uri.parse(endpoint).host }.getOrNull()
        if (!host.isNullOrBlank()) return host
        return endpoint.substringAfter("://").substringBefore("/").ifBlank { "unknown" }
    }

    private fun normalize(endpoint: String): String = hostOf(endpoint).lowercase()

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

        /**
         * Static, factual summary of what this client integrates. Kept intentionally simple — a
         * real capability-detection system is a separate feature (#47); these are constants that do
         * not reveal any account-specific data.
         */
        val CAPABILITIES = listOf(
            "solidtime API client",
            "offline outbox with retry and dead-lettering",
            "background sync worker",
            "conflict detection and recovery",
        )
    }
}

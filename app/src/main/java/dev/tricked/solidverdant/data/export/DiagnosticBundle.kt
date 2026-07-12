/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.export

/**
 * Coarse, non-sensitive classification of a sync failure. The raw outbox error string may embed a
 * server response body containing the user's own work content (descriptions, project names) or, in
 * pathological cases, a token echoed back in an error; the diagnostic bundle therefore only ever
 * records the *category*, never the raw text. See [DiagnosticBundle.classifyOutboxError].
 */
enum class DiagnosticErrorCategory { OFFLINE, SERVER_PROBLEM, REJECTED, UNKNOWN }

/**
 * One already-sanitized sync failure. Every field is safe to share: an operation type enum name, a
 * coarse category, a whole-day age, and an attempt count. There is deliberately no field capable of
 * carrying free text, so the formatter *cannot* leak work content — redaction is by construction.
 */
data class DiagnosticFailure(val opType: String, val category: DiagnosticErrorCategory, val ageDays: Long, val attempts: Int)

/**
 * Pure, Android-free core of the privacy-reviewed diagnostic export (roadmap #49). It receives only
 * primitives and already-categorized [DiagnosticFailure]s — never a raw error string, an endpoint
 * URL with credentials, a token, or any entry/project/description text — and renders a
 * human-readable text bundle used to make self-hosting support easier while staying trustworthy.
 *
 * The safety contract is structural: [build] has no parameter through which raw work content or a
 * secret could reach the output, and the only place that touches raw error text is
 * [classifyOutboxError], which discards everything except a bucket.
 */
object DiagnosticBundle {

    /**
     * Classify a raw outbox error into a safe [DiagnosticErrorCategory] and discard the text.
     * Mirrors the Sync Center reason classifier (#33) so the two surfaces agree, but returns a
     * resource-free enum so it is usable from the pure bundle core.
     *
     * Network/timeout/host-resolution failures -> [DiagnosticErrorCategory.OFFLINE]; 4xx / client
     * rejections -> [DiagnosticErrorCategory.REJECTED]; 5xx / gateway / unavailable ->
     * [DiagnosticErrorCategory.SERVER_PROBLEM]; blank or unrecognized -> [DiagnosticErrorCategory.UNKNOWN].
     */
    fun classifyOutboxError(raw: String?): DiagnosticErrorCategory {
        val lower = raw?.lowercase().orEmpty()
        return when {
            lower.isBlank() -> DiagnosticErrorCategory.UNKNOWN
            OFFLINE_KEYWORDS.any { it in lower } -> DiagnosticErrorCategory.OFFLINE
            REJECTED_KEYWORDS.any { it in lower } -> DiagnosticErrorCategory.REJECTED
            SERVER_KEYWORDS.any { it in lower } -> DiagnosticErrorCategory.SERVER_PROBLEM
            else -> DiagnosticErrorCategory.UNKNOWN
        }
    }

    /**
     * Render the diagnostic text bundle. All inputs are non-sensitive:
     * - [appVersion]/[appVersionCode]: the app build identity.
     * - [androidRelease]/[androidSdk]/[deviceManufacturer]/[deviceModel]: the platform.
     * - [serverHost]: the endpoint *host only* (e.g. `app.solidtime.io`), never a full URL, path,
     *   query or token; [isSelfHosted] states whether it differs from the default instance.
     * - [capabilities]: static, factual client capability lines.
     * - [pendingCount]/[failedCount]: outbox counts.
     * - [failures]: per-failure summaries already stripped to safe fields.
     * - [lastFullSyncAtMs]/[lastPushAtMs]: sync freshness timestamps (may be null).
     */
    @Suppress("LongParameterList")
    fun build(
        appVersion: String,
        appVersionCode: Int,
        androidRelease: String,
        androidSdk: Int,
        deviceManufacturer: String,
        deviceModel: String,
        serverHost: String,
        isSelfHosted: Boolean,
        capabilities: List<String>,
        pendingCount: Int,
        failedCount: Int,
        failures: List<DiagnosticFailure>,
        lastFullSyncAtMs: Long?,
        lastPushAtMs: Long?,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("SolidVerdant diagnostic bundle")
        sb.appendLine("(privacy-reviewed: no tokens, descriptions, project names or work content)")
        sb.appendLine()

        sb.appendLine("== App ==")
        sb.appendLine("version: $appVersion ($appVersionCode)")
        sb.appendLine()

        sb.appendLine("== Device ==")
        sb.appendLine("android: $androidRelease (SDK $androidSdk)")
        sb.appendLine("device: $deviceManufacturer $deviceModel")
        sb.appendLine()

        sb.appendLine("== Server ==")
        sb.appendLine("host: $serverHost")
        sb.appendLine("self-hosted: ${if (isSelfHosted) "yes" else "no"}")
        sb.appendLine()

        sb.appendLine("== Capabilities ==")
        if (capabilities.isEmpty()) {
            sb.appendLine("(none reported)")
        } else {
            capabilities.forEach { sb.appendLine("- $it") }
        }
        sb.appendLine()

        sb.appendLine("== Sync ==")
        sb.appendLine("pending: $pendingCount")
        sb.appendLine("failed: $failedCount")
        sb.appendLine("last full sync: ${formatTimestamp(lastFullSyncAtMs)}")
        sb.appendLine("last push: ${formatTimestamp(lastPushAtMs)}")
        sb.appendLine()

        sb.appendLine("== Recent sync failures ==")
        if (failures.isEmpty()) {
            sb.appendLine("(none)")
        } else {
            val byCategory = failures.groupingBy { it.category }.eachCount()
            val summary = DiagnosticErrorCategory.entries
                .filter { byCategory.containsKey(it) }
                .joinToString(", ") { "${byCategory[it]} ${it.label}" }
            sb.appendLine("${failures.size} failures: $summary")
            failures.forEach { f ->
                sb.appendLine(
                    "- ${f.opType}: ${f.category.label}, age ${f.ageDays}d, attempts ${f.attempts}",
                )
            }
        }
        return sb.toString()
    }

    /** Whole-epoch-millis timestamp rendered as a plain non-localized marker, or a placeholder. */
    private fun formatTimestamp(atMs: Long?): String = atMs?.let { "${it}ms" } ?: "never"

    /** Lowercase, hyphen-free label used in the human-readable summary. */
    private val DiagnosticErrorCategory.label: String
        get() = when (this) {
            DiagnosticErrorCategory.OFFLINE -> "offline"
            DiagnosticErrorCategory.SERVER_PROBLEM -> "server-problem"
            DiagnosticErrorCategory.REJECTED -> "rejected"
            DiagnosticErrorCategory.UNKNOWN -> "unknown"
        }

    private val OFFLINE_KEYWORDS =
        listOf("offline", "timeout", "unable to resolve host", "connect", "unreachable", "network")
    private val REJECTED_KEYWORDS =
        listOf("400", "401", "403", "404", "409", "422", "unprocessable", "forbidden", "unauthorized", "bad request")
    private val SERVER_KEYWORDS =
        listOf("500", "502", "503", "504", "server", "gateway", "unavailable")
}

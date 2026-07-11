/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Builds a CSV of the currently filtered time entries and hands back a shareable [Uri].
 *
 * The pure formatting ([buildCsv]/[buildRows]/[escapeField]) lives in the companion so it is unit
 * testable without a Context, while the instance methods resolve localized column headers and write
 * the file into the FileProvider-exposed cache directory. The CSV intentionally carries the user's
 * own work data (descriptions, project/task/client names, tags) — that is the point of the export —
 * but never any token, credential or endpoint.
 */
class CsvExporter @Inject constructor(@ApplicationContext private val context: Context) {

    /** Formats [entries] into a full CSV document using localized headers and yes/no labels. */
    fun formatCsv(
        entries: List<TimeEntry>,
        projects: List<Project>,
        clients: List<Client>,
        tasks: List<Task>,
        tags: List<Tag>,
        zone: ZoneId,
        organizationName: String,
    ): String {
        val header = listOf(
            context.getString(R.string.stats2_csv_col_entry_id),
            context.getString(R.string.stats2_csv_col_start),
            context.getString(R.string.stats2_csv_col_end),
            context.getString(R.string.stats2_csv_col_timezone),
            context.getString(R.string.stats2_csv_col_duration_seconds),
            context.getString(R.string.stats2_csv_col_duration),
            context.getString(R.string.stats2_csv_col_organization),
            context.getString(R.string.stats2_csv_col_project),
            context.getString(R.string.stats2_csv_col_client),
            context.getString(R.string.stats2_csv_col_task),
            context.getString(R.string.stats2_csv_col_tags),
            context.getString(R.string.stats2_csv_col_description),
            context.getString(R.string.stats2_csv_col_billable),
        )
        val rows = buildRows(
            entries = entries,
            projects = projects,
            clients = clients,
            tasks = tasks,
            tags = tags,
            zone = zone,
            organizationName = organizationName,
            billableYes = context.getString(R.string.stats2_yes),
            billableNo = context.getString(R.string.stats2_no),
        )
        return buildCsv(header, rows)
    }

    /**
     * Writes [csv] into `cacheDir/exports/<baseName>.csv` and returns a content:// URI granted to
     * receiving apps via the app's FileProvider authority. Overwrites any previous export with the
     * same name so the cache does not accumulate stale copies.
     */
    suspend fun writeToCache(csv: String, baseName: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "$baseName.csv")
        file.writeText(csv, Charsets.UTF_8)
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    companion object {
        private val isoInstant: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
        private const val SECONDS_PER_HOUR = 3600
        private const val SECONDS_PER_MINUTE = 60

        /** RFC 4180 field escaping: quote when the value holds a comma, quote, CR or LF. */
        fun escapeField(value: String): String {
            val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
            if (!needsQuote) return value
            return "\"" + value.replace("\"", "\"\"") + "\""
        }

        /** Joins [header] and [rows] into a CRLF-delimited CSV document with a trailing newline. */
        fun buildCsv(header: List<String>, rows: List<List<String>>): String {
            val sb = StringBuilder()
            sb.append(header.joinToString(",") { escapeField(it) }).append("\r\n")
            for (row in rows) {
                sb.append(row.joinToString(",") { escapeField(it) }).append("\r\n")
            }
            return sb.toString()
        }

        /** Formats seconds as H:MM:SS (locale-neutral, no thousands separators). */
        fun formatHms(totalSeconds: Long): String {
            val s = totalSeconds.coerceAtLeast(0)
            val h = s / SECONDS_PER_HOUR
            val m = (s % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
            val sec = s % SECONDS_PER_MINUTE
            return "%d:%02d:%02d".format(h, m, sec)
        }

        /**
         * Maps each entry to one CSV row. End time and duration are derived consistently: an entry
         * with an explicit duration reports that duration and a computed end instant; otherwise the
         * end/duration come from the parsed end timestamp. Unparseable timestamps yield blank cells
         * rather than crashing the whole export.
         */
        fun buildRows(
            entries: List<TimeEntry>,
            projects: List<Project>,
            clients: List<Client>,
            tasks: List<Task>,
            tags: List<Tag>,
            zone: ZoneId,
            organizationName: String,
            billableYes: String,
            billableNo: String,
        ): List<List<String>> {
            val projectById = projects.associateBy { it.id }
            val clientById = clients.associateBy { it.id }
            val taskById = tasks.associateBy { it.id }
            val tagNameById = tags.associate { it.id to it.name }
            return entries.map { e ->
                val startInstant = runCatching { Instant.parse(e.start) }.getOrNull()
                val endInstant: Instant? = when {
                    e.duration != null && startInstant != null ->
                        startInstant.plusSeconds(e.duration.toLong().coerceAtLeast(0))
                    e.end != null -> runCatching { Instant.parse(e.end) }.getOrNull()
                    else -> null
                }
                val durationSeconds: Long? = when {
                    e.duration != null -> e.duration.toLong().coerceAtLeast(0)
                    startInstant != null && endInstant != null ->
                        (endInstant.epochSecond - startInstant.epochSecond).coerceAtLeast(0)
                    else -> null
                }
                val project = e.projectId?.let { projectById[it] }
                val clientName = project?.clientId?.let { clientById[it]?.name } ?: ""
                val taskName = e.taskId?.let { taskById[it]?.name } ?: ""
                val tagNames = e.tags.joinToString("; ") { tagNameById[it.id] ?: it.name }
                listOf(
                    e.id,
                    e.start,
                    endInstant?.let { isoInstant.format(it) } ?: "",
                    zone.id,
                    durationSeconds?.toString() ?: "",
                    durationSeconds?.let { formatHms(it) } ?: "",
                    organizationName,
                    project?.name ?: "",
                    clientName,
                    taskName,
                    tagNames,
                    e.description ?: "",
                    if (e.billable) billableYes else billableNo,
                )
            }
        }
    }
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the pure diagnostic bundle core and its redaction contract (no Android deps). */
class DiagnosticBundleTest {

    private fun buildSample(failures: List<DiagnosticFailure> = emptyList()): String = DiagnosticBundle.build(
        appVersion = "1.2.3",
        appVersionCode = 10203,
        androidRelease = "14",
        androidSdk = 34,
        deviceManufacturer = "Google",
        deviceModel = "Pixel 7",
        serverHost = "app.solidtime.io",
        isSelfHosted = false,
        capabilities = listOf("time tracking", "offline outbox"),
        pendingCount = 2,
        failedCount = failures.size,
        failures = failures,
        lastFullSyncAtMs = 1_700_000_000_000L,
        lastPushAtMs = null,
    )

    @Test
    fun `classify network and timeout errors as offline`() {
        assertEquals(DiagnosticErrorCategory.OFFLINE, DiagnosticBundle.classifyOutboxError("Unable to resolve host"))
        assertEquals(DiagnosticErrorCategory.OFFLINE, DiagnosticBundle.classifyOutboxError("connect timeout"))
        assertEquals(DiagnosticErrorCategory.OFFLINE, DiagnosticBundle.classifyOutboxError("network is unreachable"))
    }

    @Test
    fun `classify 5xx as server problem`() {
        assertEquals(DiagnosticErrorCategory.SERVER_PROBLEM, DiagnosticBundle.classifyOutboxError("500 Internal"))
        assertEquals(DiagnosticErrorCategory.SERVER_PROBLEM, DiagnosticBundle.classifyOutboxError("503 unavailable"))
        assertEquals(DiagnosticErrorCategory.SERVER_PROBLEM, DiagnosticBundle.classifyOutboxError("Bad Gateway"))
    }

    @Test
    fun `classify 4xx as rejected`() {
        assertEquals(DiagnosticErrorCategory.REJECTED, DiagnosticBundle.classifyOutboxError("422 Unprocessable"))
        assertEquals(DiagnosticErrorCategory.REJECTED, DiagnosticBundle.classifyOutboxError("403 Forbidden"))
        assertEquals(DiagnosticErrorCategory.REJECTED, DiagnosticBundle.classifyOutboxError("400 Bad Request"))
    }

    @Test
    fun `classify null and unrecognized as unknown`() {
        assertEquals(DiagnosticErrorCategory.UNKNOWN, DiagnosticBundle.classifyOutboxError(null))
        assertEquals(DiagnosticErrorCategory.UNKNOWN, DiagnosticBundle.classifyOutboxError(""))
        assertEquals(DiagnosticErrorCategory.UNKNOWN, DiagnosticBundle.classifyOutboxError("something odd"))
    }

    @Test
    fun `bundle carries app and android version and host but not a full url`() {
        val text = buildSample()
        assertTrue(text.contains("1.2.3"))
        assertTrue(text.contains("10203"))
        assertTrue(text.contains("14"))
        assertTrue(text.contains("SDK 34"))
        assertTrue(text.contains("app.solidtime.io"))
        // Host only: no scheme, path or query that could carry a token.
        assertFalse(text.contains("https://"))
        assertFalse(text.contains("http://"))
    }

    @Test
    fun `bundle summarizes failure category counts`() {
        val text = buildSample(
            failures = listOf(
                DiagnosticFailure("UPDATE", DiagnosticErrorCategory.SERVER_PROBLEM, ageDays = 1, attempts = 3),
                DiagnosticFailure("STOP", DiagnosticErrorCategory.SERVER_PROBLEM, ageDays = 0, attempts = 1),
                DiagnosticFailure("DELETE", DiagnosticErrorCategory.OFFLINE, ageDays = 2, attempts = 5),
            ),
        )
        assertTrue(text.contains("3 failures"))
        assertTrue(text.contains("2 server-problem"))
        assertTrue(text.contains("1 offline"))
    }

    @Test
    fun `redaction contract - raw error content never reaches the categorized bundle`() {
        // Simulate the exporter's own pipeline: raw errors are classified, then only the category
        // (plus op type / age / attempts) is passed to the formatter. This is the safety contract.
        val rawServerError = """500 Server Error: {"description":"secret client meeting"}"""
        val rawTokenError = "token=abcdef123 expired unauthorized 401"
        val rawProjectError = """422 {"project":"Project Moonshot rename"}"""

        val failures = listOf(rawServerError, rawTokenError, rawProjectError).mapIndexed { i, raw ->
            DiagnosticFailure(
                opType = "UPDATE",
                category = DiagnosticBundle.classifyOutboxError(raw),
                ageDays = i.toLong(),
                attempts = 1,
            )
        }
        val text = buildSample(failures = failures)

        // Categories are present.
        assertEquals(DiagnosticErrorCategory.SERVER_PROBLEM, failures[0].category)
        assertEquals(DiagnosticErrorCategory.REJECTED, failures[1].category)
        assertEquals(DiagnosticErrorCategory.REJECTED, failures[2].category)
        assertTrue(text.contains("server-problem"))
        assertTrue(text.contains("rejected"))

        // None of the raw work content or secrets leaked.
        assertFalse(text.contains("secret client meeting"))
        assertFalse(text.contains("abcdef"))
        assertFalse(text.contains("token="))
        assertFalse(text.contains("Project Moonshot"))
        // The JSON key from the raw body must not appear; the word "description" does occur in the
        // header disclaimer, so we assert on the leaked *value*'s surrounding key/value pair instead.
        assertFalse(text.contains("\"description\""))
    }
}

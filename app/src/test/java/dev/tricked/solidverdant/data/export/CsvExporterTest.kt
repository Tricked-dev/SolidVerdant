package dev.tricked.solidverdant.data.export

import dev.tricked.solidverdant.data.model.Client
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/** Exercises the pure CSV formatting helpers (no Android Context required). */
class CsvExporterTest {

    private val utc = ZoneId.of("UTC")

    private fun task(id: String, name: String, projectId: String) =
        Task(id = id, name = name, projectId = projectId, createdAt = "", updatedAt = "")

    @Test
    fun `escapeField leaves plain values untouched`() {
        assertEquals("Alpha", CsvExporter.escapeField("Alpha"))
        assertEquals("", CsvExporter.escapeField(""))
    }

    @Test
    fun `escapeField quotes and doubles embedded delimiters`() {
        assertEquals("\"a,b\"", CsvExporter.escapeField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escapeField("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvExporter.escapeField("line1\nline2"))
        assertEquals("\"has\rcr\"", CsvExporter.escapeField("has\rcr"))
    }

    @Test
    fun `buildCsv joins header and rows with CRLF and a trailing newline`() {
        val csv = CsvExporter.buildCsv(
            header = listOf("A", "B"),
            rows = listOf(listOf("1", "x,y"), listOf("2", "z")),
        )
        assertEquals("A,B\r\n1,\"x,y\"\r\n2,z\r\n", csv)
    }

    @Test
    fun `formatHms pads minutes and seconds`() {
        assertEquals("0:00:00", CsvExporter.formatHms(0))
        assertEquals("0:00:59", CsvExporter.formatHms(59))
        assertEquals("1:01:01", CsvExporter.formatHms(3661))
        assertEquals("10:00:00", CsvExporter.formatHms(36000))
        assertEquals("0:00:00", CsvExporter.formatHms(-5))
    }

    @Test
    fun `buildRows maps every documented field and derives end from duration`() {
        val entry = TimeEntry(
            id = "e1",
            userId = "u",
            start = "2026-07-01T09:00:00Z",
            duration = 3600,
            taskId = "t1",
            projectId = "p1",
            tags = listOf(Tag("g1"), Tag("g2")),
            billable = true,
            description = "Fix, \"bug\"",
            organizationId = "org",
        )
        val rows = CsvExporter.buildRows(
            entries = listOf(entry),
            projects = listOf(Project(id = "p1", name = "Alpha", color = "#fff", clientId = "c1")),
            clients = listOf(Client(id = "c1", name = "Acme")),
            tasks = listOf(task("t1", "Design", "p1")),
            tags = listOf(Tag("g1", "urgent"), Tag("g2", "backend")),
            zone = utc,
            organizationName = "My Org",
            billableYes = "Yes",
            billableNo = "No",
        )
        assertEquals(1, rows.size)
        assertEquals(
            listOf(
                "e1",
                "2026-07-01T09:00:00Z",
                "2026-07-01T10:00:00Z",
                "UTC",
                "3600",
                "1:00:00",
                "My Org",
                "Alpha",
                "Acme",
                "Design",
                "urgent; backend",
                "Fix, \"bug\"", // raw here; escaping happens in buildCsv
                "Yes",
            ),
            rows.single(),
        )
    }

    @Test
    fun `buildRows derives duration from end when duration is null`() {
        val entry = TimeEntry(
            id = "e2",
            userId = "u",
            start = "2026-07-01T09:00:00Z",
            end = "2026-07-01T09:30:00Z",
            duration = null,
            billable = false,
            organizationId = "org",
        )
        val row = CsvExporter.buildRows(
            entries = listOf(entry),
            projects = emptyList(),
            clients = emptyList(),
            tasks = emptyList(),
            tags = emptyList(),
            zone = utc,
            organizationName = "Org",
            billableYes = "Yes",
            billableNo = "No",
        ).single()
        assertEquals("1800", row[4])
        assertEquals("0:30:00", row[5])
        assertEquals("", row[7]) // no project
        assertEquals("No", row[12])
    }

    @Test
    fun `buildRows tolerates an unparseable start with blank end and duration`() {
        val entry = TimeEntry(
            id = "bad",
            userId = "u",
            start = "not-a-date",
            duration = null,
            end = null,
            organizationId = "org",
        )
        val row = CsvExporter.buildRows(
            entries = listOf(entry),
            projects = emptyList(),
            clients = emptyList(),
            tasks = emptyList(),
            tags = emptyList(),
            zone = utc,
            organizationName = "Org",
            billableYes = "Yes",
            billableNo = "No",
        ).single()
        assertEquals("bad", row[0])
        assertEquals("", row[2]) // end blank, no crash
        assertEquals("", row[4]) // duration seconds blank
        assertEquals("", row[5]) // duration blank
    }
}

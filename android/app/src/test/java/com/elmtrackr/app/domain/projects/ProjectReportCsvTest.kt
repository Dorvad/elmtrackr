package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.domain.text.BidiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * The project CSV export.
 *
 * The properties that matter are machine-readability and text safety: Hebrew and
 * other non-Latin text must survive, a name containing a comma or a quote must not
 * break the row structure, and amounts must be parseable rather than localised.
 */
class ProjectReportCsvTest {

    private val today = LocalDate.of(2026, 7, 29)
    private val from = LocalDate.of(2026, 7, 1)
    private val to = LocalDate.of(2026, 7, 31)

    private fun project(
        id: String = "p1",
        name: String = "Website rebuild",
        client: String? = "Acme Ltd",
        entered: String = "10000",
        currency: String = "USD",
        rate: String = "18",
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
        budgetMinutes: Int? = null,
        targetRate: String? = null,
    ) = Project(
        id = id,
        userId = "u1",
        name = name,
        clientName = client,
        workStatus = status,
        fee = ProjectFee.from(BigDecimal(entered), currency, TaxMode.EXCLUSIVE, BigDecimal(rate)),
        hourBudgetMinutes = budgetMinutes,
        targetHourlyRate = targetRate?.let { Money.of(it, currency) },
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun record(projectId: String = "p1", currency: String = "USD") = ProjectBillingRecord(
        id = "b-$projectId",
        userId = "u1",
        projectId = projectId,
        fee = ProjectFee.from(BigDecimal("10000"), currency, TaxMode.EXCLUSIVE, BigDecimal("18")),
        billedOn = from.plusDays(2),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun payment(projectId: String = "p1", amount: String, currency: String = "USD") =
        ProjectPayment(
            id = "pay-$projectId",
            userId = "u1",
            projectId = projectId,
            billingRecordId = "b-$projectId",
            paidOn = from.plusDays(9),
            amount = Money.of(amount, currency),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private fun report(
        projects: List<Project>,
        records: List<ProjectBillingRecord> = emptyList(),
        payments: List<ProjectPayment> = emptyList(),
        minutes: Map<String, Int> = emptyMap(),
    ): ProjectReport {
        val summaries = projects.map { p ->
            ProjectMetrics.summarize(
                project = p,
                shifts = emptyList(),
                billingRecords = records,
                payments = payments,
                today = today,
                timeSummary = ProjectTimeSummary(minutes[p.id] ?: 0, 1),
            )
        }
        return ProjectReportBuilder.build(
            summaries = summaries,
            billingRecords = records,
            payments = payments,
            filter = ProjectReportFilter(from = from, to = to),
            projectMinutes = minutes,
            activeDays = 3,
            today = today,
        )
    }

    private fun csv(report: ProjectReport) = ProjectReportCsv.build(report, includeBom = false)

    @Test
    fun `the header names every requested column`() {
        val header = csv(report(listOf(project()))).lineSequence().first()

        listOf(
            "Project", "Client", "Work status", "Billing status", "Currency",
            "Tracked hours", "Base fee", "Tax", "Client total", "Paid",
            "Outstanding", "Effective hourly rate",
        ).forEach { column ->
            assertTrue(column, header.contains("\"$column\""))
        }
    }

    @Test
    fun `a project row carries its figures`() {
        val content = csv(
            report(
                projects = listOf(project(budgetMinutes = 300, targetRate = "900")),
                records = listOf(record()),
                payments = listOf(payment(amount = "5000.00")),
                minutes = mapOf("p1" to 600),
            ),
        )
        val row = content.lineSequence().drop(1).first()

        assertTrue(row, row.contains("\"Website rebuild\""))
        assertTrue(row, row.contains("\"Acme Ltd\""))
        assertTrue(row, row.contains("\"ACTIVE\""))
        assertTrue(row, row.contains("\"USD\""))
        assertTrue(row, row.contains("\"10.00\""))
        assertTrue(row, row.contains("\"10000.00\""))
        assertTrue(row, row.contains("\"1800.00\""))
        assertTrue(row, row.contains("\"11800.00\""))
        assertTrue(row, row.contains("\"5000.00\""))
        // Effective rate: 10000 base over 10 hours.
        assertTrue(row, row.contains("\"1000.00\""))
        // Five hours over a five-hour budget.
        assertTrue(row, row.contains("\"5.00\""))
    }

    // ── Unicode and RTL ───────────────────────────────────────────────────────

    @Test
    fun `Hebrew project and client names survive verbatim`() {
        val content = csv(
            report(listOf(project(name = "בניית אתר מחדש", client = "חברת אקמה בעמ"))),
        )

        assertTrue(content.contains("\"בניית אתר מחדש\""))
        assertTrue(content.contains("\"חברת אקמה בעמ\""))
    }

    @Test
    fun `mixed Hebrew and Latin text with digits survives`() {
        val name = "פרויקט Website 2026"
        assertTrue(csv(report(listOf(project(name = name)))).contains("\"$name\""))
    }

    @Test
    fun `a UTF-8 BOM is written so Excel decodes Hebrew correctly`() {
        val content = ProjectReportCsv.build(report(listOf(project(name = "בניית אתר"))))

        assertTrue(content.startsWith(ProjectReportCsv.UTF8_BOM))
        assertTrue(content.contains("בניית אתר"))
    }

    @Test
    fun `the export round-trips through UTF-8 bytes unchanged`() {
        val content = ProjectReportCsv.build(report(listOf(project(name = "בניית אתר מחדש"))))
        val roundTripped = String(content.toByteArray(Charsets.UTF_8), Charsets.UTF_8)

        assertEquals(content, roundTripped)
        assertTrue(roundTripped.contains("בניית אתר מחדש"))
    }

    // ── Field safety ──────────────────────────────────────────────────────────

    @Test
    fun `a name containing a comma does not add a column`() {
        val content = csv(report(listOf(project(name = "Rebuild, phase 2"))))
        val header = content.lineSequence().first()
        val row = content.lineSequence().drop(1).first()

        assertTrue(row.contains("\"Rebuild, phase 2\""))
        // The comma is inside quotes, so the field count is unchanged.
        assertEquals(header.count { it == ',' }, countUnquotedCommas(row))
    }

    @Test
    fun `a name containing a quote is escaped by doubling`() {
        val content = csv(report(listOf(project(name = "The \"big\" rebuild"))))
        assertTrue(content.contains("\"The \"\"big\"\" rebuild\""))
    }

    @Test
    fun `a name containing a newline stays inside its quoted field`() {
        val content = csv(report(listOf(project(name = "Line one\nLine two"))))
        assertTrue(content.contains("\"Line one\nLine two\""))
    }

    @Test
    fun `amounts carry no currency symbol or grouping separator`() {
        val content = csv(report(listOf(project(entered = "1234567.89", currency = "ILS"))))

        assertTrue(content.contains("\"1234567.89\""))
        assertFalse(content.contains("₪"))
        assertFalse(content.contains("1,234,567"))
    }

    @Test
    fun `a missing client or rate is blank rather than zero`() {
        val content = csv(report(listOf(project(client = null))))
        val row = content.lineSequence().drop(1).first()

        assertTrue(row, row.contains("\"\""))
        // No tracked hours, so no effective rate: blank, not "0.00".
        assertEquals(1, Regex("\"0\\.00\"").findAll(row).count().coerceAtMost(1))
    }

    // ── Totals ────────────────────────────────────────────────────────────────

    @Test
    fun `there is one totals row per currency and no combined total`() {
        val content = csv(
            report(
                projects = listOf(
                    project("p1", currency = "USD", entered = "10000"),
                    project("p2", currency = "ILS", entered = "20000"),
                ),
                minutes = mapOf("p1" to 600, "p2" to 600),
            ),
        )

        assertTrue(content.contains("\"TOTAL USD\""))
        assertTrue(content.contains("\"TOTAL ILS\""))
        // No unqualified total that would imply the two were added.
        assertFalse(content.contains("\"TOTAL\""))
    }

    @Test
    fun `the per-currency total sums that currency only`() {
        val content = csv(
            report(
                projects = listOf(
                    project("p1", currency = "USD", entered = "10000"),
                    project("p2", currency = "USD", entered = "5000"),
                    project("p3", currency = "ILS", entered = "20000"),
                ),
            ),
        )
        val usdTotal = content.lineSequence().first { it.startsWith("\"TOTAL USD\"") }

        assertTrue(usdTotal, usdTotal.contains("\"15000.00\""))
        assertFalse(usdTotal, usdTotal.contains("20000.00"))
    }

    @Test
    fun `an empty report is just the header`() {
        val content = csv(report(emptyList()))
        assertEquals(1, content.lineSequence().count())
    }

    @Test
    fun `the filename carries the reported range`() {
        assertEquals(
            "elmtrackr-projects-2026-07-01-to-2026-07-31.csv",
            ProjectReportCsv.filename(report(listOf(project()))),
        )
    }

    /** Commas outside quoted fields — the ones that actually delimit columns. */
    private fun countUnquotedCommas(row: String): Int {
        var inQuotes = false
        var count = 0
        var index = 0
        while (index < row.length) {
            val c = row[index]
            when {
                c == '"' && inQuotes && index + 1 < row.length && row[index + 1] == '"' -> index++
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> count++
            }
            index++
        }
        return count
    }
    // ── Isolate characters never reach a spreadsheet ─────────────────────────

    @Test
    fun `a bidi isolate in a project name is stripped from the export`() {
        // Screen text carries invisible isolate controls so that Hebrew and
        // Latin runs do not reorder. A spreadsheet must not: two cells that look
        // identical would not compare equal, and a VLOOKUP against the name
        // typed by hand would silently miss.
        val csv = ProjectReportCsv.build(
            report(listOf(project(name = "\u2068Acme\u2069 \u05e4\u05e8\u05d5\u05d9\u05e7\u05d8"))),
            includeBom = false,
        )

        assertFalse(BidiText.containsIsolates(csv))
        assertTrue(csv.contains("Acme \u05e4\u05e8\u05d5\u05d9\u05e7\u05d8"))
    }

    @Test
    fun `stripping isolates leaves Hebrew and punctuation intact`() {
        val name = "\u05e4\u05e8\u05d5\u05d9\u05e7\u05d8 \u05d0\u05e7\u05de\u05d4, 2026-014"
        val csv = ProjectReportCsv.build(report(listOf(project(name = name))), includeBom = false)

        // The comma is still quoted, the Hebrew is still Hebrew, the reference is
        // still in order.
        assertTrue(csv.contains(name))
    }

    @Test
    fun `quoting still doubles embedded quotes after stripping`() {
        assertEquals("\"say \"\"hi\"\"\"", ProjectReportCsv.quote("say \"hi\""))
    }
}

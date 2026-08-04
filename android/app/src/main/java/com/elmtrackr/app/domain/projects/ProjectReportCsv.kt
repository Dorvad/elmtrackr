package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.text.BidiText
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The project report as CSV.
 *
 * Written to be safe for Hebrew and any other non-Latin text:
 *
 *  - a UTF-8 BOM is prepended, because Excel on Windows otherwise decodes a
 *    UTF-8 file as the system code page and turns Hebrew into mojibake;
 *  - every field is quoted and embedded quotes are doubled, so a project name
 *    containing a comma, a quote or a newline cannot break the row structure;
 *  - amounts are written as plain decimal strings with no currency symbol and no
 *    thousands separators, with the currency in its own column. A localised
 *    "1.234,56 €" is ambiguous to every parser.
 *
 * Deliberately not an invoice. This is the user's own data in a spreadsheet
 * format, and nothing about it claims to be a fiscal document.
 */
object ProjectReportCsv {

    /**
     * Excel needs this to read UTF-8; other tools ignore it.
     *
     * Written as an escape rather than the literal character, so the byte-order
     * mark does not sit inside this source file.
     */
    const val UTF8_BOM = "\uFEFF"

    /**
     * Every column that carries money or hours names its accounting basis.
     *
     * The report used to have plain "Billed" / "Paid" / "Outstanding" columns filled
     * from all-time billing, above a TOTAL row filled from period-filtered billing.
     * A project billed in June and viewed in July showed its amount on its own row
     * and zero in TOTAL, and nobody reconciling the export against a ledger could
     * make it add up. The period columns are the ones the TOTAL row sums; the
     * all-time columns are the project's standing and are labelled as such.
     */
    private val HEADERS = listOf(
        "Project",
        "Client",
        "Work status",
        "Billing status",
        "Currency",
        "Tracked hours (period)",
        "Base fee",
        "Tax",
        "Client total",
        "Billed (period)",
        "Paid (period)",
        "Outstanding (period)",
        "Billed (all-time)",
        "Paid (all-time)",
        "Outstanding (all-time)",
        "Tracked hours (all-time)",
        "Effective hourly rate (all-time)",
        "Target hourly rate",
        "Hour budget",
        "Hours over budget",
    )

    /**
     * @param includeBom false only for tests that assert on content; shipped
     * exports always include it.
     */
    fun build(report: ProjectReport, includeBom: Boolean = true): String {
        val rows = mutableListOf(HEADERS.joinToString(",") { quote(it) })

        report.projects.forEach { p ->
            rows += listOf(
                p.projectName,
                p.clientName.orEmpty(),
                p.workStatus.name,
                p.billingStatus.name,
                p.currencyCode,
                hours(p.trackedMinutes),
                amount(p.baseFee),
                amount(p.tax),
                amount(p.clientTotal),
                p.periodBilled?.let { amount(it) }.orEmpty(),
                p.periodReceived?.let { amount(it) }.orEmpty(),
                p.periodOutstanding?.let { amount(it) }.orEmpty(),
                p.billedTotal?.let { amount(it) }.orEmpty(),
                amount(p.paidTotal),
                amount(p.outstandingTotal),
                hours(p.allTimeMinutes),
                // Blank rather than 0 when there is no rate: a project with no
                // hours has no rate, and 0 would read as "earned nothing".
                p.effectiveHourlyRate?.let { amount(it) }.orEmpty(),
                p.targetHourlyRate?.let { amount(it) }.orEmpty(),
                p.hourBudgetMinutes?.let { hours(it) }.orEmpty(),
                p.minutesOverBudget?.takeIf { it > 0 }?.let { hours(it) }.orEmpty(),
            ).joinToString(",") { quote(it) }
        }

        // One totals row per currency. A single cross-currency total would be
        // fiction, so there deliberately is not one.
        if (report.projects.isNotEmpty()) {
            rows += ""
            report.currencies.forEach { currency ->
                val group = report.projects.filter { it.currencyCode == currency }
                val minutes = group.sumOf { it.trackedMinutes }
                rows += listOf(
                    "TOTAL $currency",
                    "",
                    "",
                    "",
                    currency,
                    hours(minutes),
                    amount(Money.sum(group.map { it.baseFee }, currency)),
                    amount(Money.sum(group.map { it.tax }, currency)),
                    amount(Money.sum(group.map { it.clientTotal }, currency)),
                    amount(report.totals.billed[currency]),
                    amount(report.totals.received[currency]),
                    amount(report.totals.outstanding[currency]),
                    // All-time columns are summed across the group, so the TOTAL row
                    // stays on the same basis as the column above it in both blocks.
                    amount(Money.sum(group.mapNotNull { it.billedTotal }, currency)),
                    amount(Money.sum(group.map { it.paidTotal }, currency)),
                    amount(Money.sum(group.map { it.outstandingTotal }, currency)),
                    hours(group.sumOf { it.allTimeMinutes }),
                    report.efficiency
                        .firstOrNull { it.currencyCode == currency }
                        ?.valuePerHour
                        ?.let { amount(it) }
                        .orEmpty(),
                    "",
                    "",
                    hours(group.sumOf { it.minutesOverBudget ?: 0 }),
                ).joinToString(",") { quote(it) }
            }
        }

        return (if (includeBom) UTF8_BOM else "") + rows.joinToString("\n")
    }

    /**
     * Every field is quoted, and a quote inside becomes two. Quoting
     * unconditionally rather than only when needed keeps Hebrew, commas and
     * newlines all safe without the writer having to detect them.
     *
     * Bidi isolate characters are removed on the way out. Nothing in this file
     * adds them — the export reads model values, not screen text — but this is
     * the single point every field passes through, and an invisible U+2068 in an
     * exported project name would be a real defect in someone's spreadsheet:
     * two cells that look identical would not compare equal, and a lookup
     * against the same name typed by hand would silently miss. Hebrew and every
     * other script are untouched; only the four isolate controls go.
     */
    internal fun quote(value: String): String =
        "\"" + BidiText.strip(value).replace("\"", "\"\"") + "\""

    /** Plain decimal at the currency's own scale. No symbol, no grouping. */
    internal fun amount(money: Money): String = money.amount.toPlainString()

    /** Decimal hours to two places, which is what a spreadsheet can sum. */
    internal fun hours(minutes: Int): String =
        BigDecimal(minutes)
            .divide(BigDecimal("60"), 2, RoundingMode.HALF_UP)
            .toPlainString()

    fun filename(report: ProjectReport): String =
        "elmtrackr-projects-${report.from}-to-${report.to}.csv"
}

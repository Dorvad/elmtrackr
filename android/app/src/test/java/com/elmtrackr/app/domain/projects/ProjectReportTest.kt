package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * The project report, and the rule it exists to protect: **money from different
 * accounting bases or different currencies is never added together.**
 *
 * Work performed, amount billed, cash received, tax collected and outstanding
 * balance are five different things. A payment received in July for work agreed in
 * May belongs to July's cash and May's contract, and the tests below check it
 * lands in exactly one of each rather than being double-counted or merged.
 */
class ProjectReportTest {

    private val today = LocalDate.of(2026, 7, 29)
    private val julyStart = LocalDate.of(2026, 7, 1)
    private val julyEnd = LocalDate.of(2026, 7, 31)

    private fun project(
        id: String,
        name: String = "Project $id",
        client: String? = "Acme Ltd",
        entered: String = "10000",
        currency: String = "USD",
        rate: String = "18",
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
        budgetMinutes: Int? = null,
        targetRate: String? = null,
        archivedAt: Instant? = null,
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
        archivedAt = archivedAt,
    )

    private fun record(
        id: String,
        projectId: String,
        entered: String = "10000",
        currency: String = "USD",
        rate: String = "18",
        billedOn: LocalDate = julyStart.plusDays(4),
        dueOn: LocalDate? = null,
    ) = ProjectBillingRecord(
        id = id,
        userId = "u1",
        projectId = projectId,
        fee = ProjectFee.from(BigDecimal(entered), currency, TaxMode.EXCLUSIVE, BigDecimal(rate)),
        billedOn = billedOn,
        dueOn = dueOn,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun payment(
        id: String,
        projectId: String,
        recordId: String,
        amount: String,
        currency: String = "USD",
        paidOn: LocalDate = julyStart.plusDays(10),
    ) = ProjectPayment(
        id = id,
        userId = "u1",
        projectId = projectId,
        billingRecordId = recordId,
        paidOn = paidOn,
        amount = Money.of(amount, currency),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun summaries(
        projects: List<Project>,
        records: List<ProjectBillingRecord> = emptyList(),
        payments: List<ProjectPayment> = emptyList(),
        minutes: Map<String, Int> = emptyMap(),
    ) = projects.map { p ->
        ProjectMetrics.summarize(
            project = p,
            shifts = emptyList(),
            billingRecords = records,
            payments = payments,
            today = today,
            timeSummary = ProjectTimeSummary(minutes[p.id] ?: 0, if (minutes[p.id] != null) 1 else 0),
        )
    }

    private fun report(
        projects: List<Project>,
        records: List<ProjectBillingRecord> = emptyList(),
        payments: List<ProjectPayment> = emptyList(),
        minutes: Map<String, Int> = emptyMap(),
        filter: ProjectReportFilter = ProjectReportFilter(from = julyStart, to = julyEnd),
        activeDays: Int = 0,
    ) = ProjectReportBuilder.build(
        summaries = summaries(projects, records, payments, minutes),
        billingRecords = records,
        payments = payments,
        filter = filter,
        projectMinutes = minutes,
        activeDays = activeDays,
        today = today,
    )

    // ── Accounting bases stay apart ───────────────────────────────────────────

    @Test
    fun `work agreed, billed and received are reported as three separate figures`() {
        val result = report(
            projects = listOf(project("p1")),
            records = listOf(record("b1", "p1")),
            payments = listOf(payment("pay1", "p1", "b1", "5000.00")),
        )

        // Contracted: the agreed fee, recognised when priced.
        assertEquals(Money.of("10000.00", "USD"), result.totals.contractedBase["USD"])
        // Billed: what the client was asked for in this period.
        assertEquals(Money.of("11800.00", "USD"), result.totals.billed["USD"])
        // Received: cash that arrived in this period.
        assertEquals(Money.of("5000.00", "USD"), result.totals.received["USD"])
        // Outstanding: a balance, not a flow.
        assertEquals(Money.of("6800.00", "USD"), result.totals.outstanding["USD"])
    }

    @Test
    fun `a payment for an earlier period counts as this period's cash only`() {
        // Billed in June, paid in July. July's cash includes it; July's billed does not.
        val juneRecord = record("b1", "p1", billedOn = LocalDate.of(2026, 6, 10))
        val result = report(
            projects = listOf(project("p1")),
            records = listOf(juneRecord),
            payments = listOf(payment("pay1", "p1", "b1", "11800.00", paidOn = julyStart.plusDays(5))),
        )

        assertTrue(result.totals.billed.isEmpty)
        assertEquals(Money.of("11800.00", "USD"), result.totals.received["USD"])
    }

    @Test
    fun `a payment after the period is excluded from this period's cash`() {
        val result = report(
            projects = listOf(project("p1")),
            records = listOf(record("b1", "p1")),
            payments = listOf(
                payment("pay1", "p1", "b1", "11800.00", paidOn = LocalDate.of(2026, 8, 5)),
            ),
        )

        assertTrue(result.totals.received.isEmpty)
        // But it is billed in July, and still outstanding as at the end of July.
        assertEquals(Money.of("11800.00", "USD"), result.totals.billed["USD"])
        assertEquals(Money.of("11800.00", "USD"), result.totals.outstanding["USD"])
    }

    // ── Tax is never revenue ──────────────────────────────────────────────────

    @Test
    fun `tax collected in cash is reported apart from revenue before tax`() {
        // A full payment of 11800 on a 10000 + 18% bill is 10000 revenue and 1800 tax.
        val result = report(
            projects = listOf(project("p1")),
            records = listOf(record("b1", "p1")),
            payments = listOf(payment("pay1", "p1", "b1", "11800.00")),
        )

        assertEquals(Money.of("11800.00", "USD"), result.totals.received["USD"])
        assertEquals(Money.of("10000.00", "USD"), result.totals.receivedBase["USD"])
        assertEquals(Money.of("1800.00", "USD"), result.totals.receivedTax["USD"])
        // The split reconciles to the cash exactly.
        assertEquals(
            result.totals.received["USD"],
            result.totals.receivedBase["USD"] + result.totals.receivedTax["USD"],
        )
    }

    @Test
    fun `a partial payment apportions tax pro rata rather than guessing`() {
        // Half of an 11800 bill: half the base and half the tax.
        val result = report(
            projects = listOf(project("p1")),
            records = listOf(record("b1", "p1")),
            payments = listOf(payment("pay1", "p1", "b1", "5900.00")),
        )

        assertEquals(Money.of("5000.00", "USD"), result.totals.receivedBase["USD"])
        assertEquals(Money.of("900.00", "USD"), result.totals.receivedTax["USD"])
    }

    @Test
    fun `with no tax the whole payment is revenue`() {
        val result = report(
            projects = listOf(project("p1", rate = "0")),
            records = listOf(record("b1", "p1", rate = "0")),
            payments = listOf(payment("pay1", "p1", "b1", "10000.00")),
        )

        assertEquals(Money.of("10000.00", "USD"), result.totals.receivedBase["USD"])
        assertTrue(result.totals.receivedTax.isEmpty)
    }

    @Test
    fun `contracted tax is reported apart from the contracted base`() {
        val result = report(projects = listOf(project("p1")))

        assertEquals(Money.of("10000.00", "USD"), result.totals.contractedBase["USD"])
        assertEquals(Money.of("1800.00", "USD"), result.totals.contractedTax["USD"])
        assertEquals(Money.of("11800.00", "USD"), result.totals.contractedClientTotal["USD"])
    }

    // ── Currencies stay apart ─────────────────────────────────────────────────

    @Test
    fun `two currencies are never summed into one figure`() {
        val result = report(
            projects = listOf(
                project("p1", currency = "USD", entered = "10000"),
                project("p2", currency = "ILS", entered = "20000"),
            ),
        )

        assertEquals(Money.of("10000.00", "USD"), result.totals.contractedBase["USD"])
        assertEquals(Money.of("20000.00", "ILS"), result.totals.contractedBase["ILS"])
        assertEquals(listOf("ILS", "USD"), result.currencies)
        assertTrue(result.hasMultipleCurrencies)
        // No single combined figure is obtainable.
        assertNull(result.totals.contractedBase.singleOrNull())
    }

    @Test
    fun `revenue efficiency is reported per currency, never pooled`() {
        val result = report(
            projects = listOf(
                project("p1", currency = "USD", entered = "10000"),
                project("p2", currency = "ILS", entered = "20000"),
            ),
            minutes = mapOf("p1" to 600, "p2" to 600),
        )

        assertEquals(2, result.efficiency.size)
        val usd = result.efficiency.single { it.currencyCode == "USD" }
        val ils = result.efficiency.single { it.currencyCode == "ILS" }
        assertEquals(Money.of("1000.00", "USD"), usd.valuePerHour)
        assertEquals(Money.of("2000.00", "ILS"), ils.valuePerHour)
    }

    @Test
    fun `hours are summed across currencies because an hour is an hour`() {
        val result = report(
            projects = listOf(
                project("p1", currency = "USD"),
                project("p2", currency = "ILS"),
            ),
            minutes = mapOf("p1" to 600, "p2" to 300),
        )
        assertEquals(900, result.trackedMinutes)
    }

    // ── Revenue efficiency is not profit ──────────────────────────────────────

    @Test
    fun `value per hour comes from the base fee, not the client total`() {
        val result = report(projects = listOf(project("p1")), minutes = mapOf("p1" to 600))
        val efficiency = result.efficiency.single()

        assertEquals(Money.of("10000.00", "USD"), efficiency.baseRevenue)
        assertEquals(Money.of("1000.00", "USD"), efficiency.valuePerHour)
    }

    @Test
    fun `no hours means no value per hour rather than a division by zero`() {
        val result = report(projects = listOf(project("p1")))
        assertNull(result.efficiency.single().valuePerHour)
    }

    // ── Time section ──────────────────────────────────────────────────────────

    @Test
    fun `the time section reports hours, active days, average and hours over budget`() {
        val result = report(
            projects = listOf(
                project("p1", budgetMinutes = 300),
                project("p2", budgetMinutes = 600),
            ),
            minutes = mapOf("p1" to 600, "p2" to 300),
            activeDays = 4,
        )

        assertEquals(900, result.trackedMinutes)
        assertEquals(4, result.activeDays)
        assertEquals(450, result.averageMinutesPerProject)
        // p1 is 300 over; p2 is inside its budget.
        assertEquals(300, result.minutesOverBudget)
    }

    @Test
    fun `an empty report has a shape rather than throwing`() {
        val result = report(projects = emptyList())

        assertTrue(result.isEmpty)
        assertEquals(0, result.trackedMinutes)
        assertEquals(0, result.averageMinutesPerProject)
        assertTrue(result.currencies.isEmpty())
        assertFalse(result.hasMultipleCurrencies)
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    @Test
    fun `an empty filter selects everything except archived projects`() {
        val result = report(
            projects = listOf(
                project("p1"),
                project("p2", archivedAt = Instant.EPOCH),
            ),
            filter = ProjectReportFilter(from = julyStart, to = julyEnd),
        )
        assertEquals(listOf("p1"), result.projects.map { it.projectId })
    }

    @Test
    fun `archived projects are included when asked for`() {
        val result = report(
            projects = listOf(project("p1"), project("p2", archivedAt = Instant.EPOCH)),
            filter = ProjectReportFilter(from = julyStart, to = julyEnd, includeArchived = true),
        )
        assertEquals(setOf("p1", "p2"), result.projects.map { it.projectId }.toSet())
    }

    @Test
    fun `the project filter narrows to one project`() {
        val result = report(
            projects = listOf(project("p1"), project("p2")),
            filter = ProjectReportFilter(projectId = "p2"),
        )
        assertEquals(listOf("p2"), result.projects.map { it.projectId })
    }

    @Test
    fun `the client filter matches case-insensitively on a substring`() {
        val result = report(
            projects = listOf(
                project("p1", client = "Acme Ltd"),
                project("p2", client = "Globex"),
            ),
            filter = ProjectReportFilter(clientName = "acme"),
        )
        assertEquals(listOf("p1"), result.projects.map { it.projectId })
    }

    @Test
    fun `the work status filter narrows by status`() {
        val result = report(
            projects = listOf(
                project("p1", status = ProjectWorkStatus.ACTIVE),
                project("p2", status = ProjectWorkStatus.COMPLETED),
            ),
            filter = ProjectReportFilter(workStatus = ProjectWorkStatus.COMPLETED),
        )
        assertEquals(listOf("p2"), result.projects.map { it.projectId })
    }

    @Test
    fun `the billing status filter narrows by the derived status`() {
        val records = listOf(record("b1", "p1"))
        val result = report(
            projects = listOf(project("p1"), project("p2")),
            records = records,
            filter = ProjectReportFilter(billingStatus = ProjectBillingStatus.NOT_BILLED),
        )
        assertEquals(listOf("p2"), result.projects.map { it.projectId })
    }

    @Test
    fun `the currency filter narrows to one currency`() {
        val result = report(
            projects = listOf(project("p1", currency = "USD"), project("p2", currency = "ILS")),
            filter = ProjectReportFilter(currencyCode = "ILS"),
        )
        assertEquals(listOf("p2"), result.projects.map { it.projectId })
        assertFalse(result.hasMultipleCurrencies)
    }

    @Test
    fun `filters combine`() {
        val result = report(
            projects = listOf(
                project("p1", client = "Acme Ltd", currency = "USD"),
                project("p2", client = "Acme Ltd", currency = "ILS"),
                project("p3", client = "Globex", currency = "USD"),
            ),
            filter = ProjectReportFilter(clientName = "Acme", currencyCode = "USD"),
        )
        assertEquals(listOf("p1"), result.projects.map { it.projectId })
    }

    @Test
    fun `a filter that matches nothing produces an empty report`() {
        val result = report(
            projects = listOf(project("p1")),
            filter = ProjectReportFilter(clientName = "nobody"),
        )
        assertTrue(result.isEmpty)
        assertTrue(result.totals.contractedBase.isEmpty)
    }

    @Test
    fun `the filter offers only the currencies and clients that exist`() {
        val list = summaries(
            listOf(
                project("p1", client = "Acme Ltd", currency = "USD"),
                project("p2", client = null, currency = "ILS"),
            ),
        )

        assertEquals(listOf("ILS", "USD"), ProjectReportFilterEngine.availableCurrencies(list))
        assertEquals(listOf("Acme Ltd"), ProjectReportFilterEngine.availableClients(list))
    }

    @Test
    fun `forMonth covers the whole month inclusive`() {
        val filter = ProjectReportFilter.forMonth(2026, 2)
        assertEquals(LocalDate.of(2026, 2, 1), filter.from)
        // 2026 is not a leap year.
        assertEquals(LocalDate.of(2026, 2, 28), filter.to)
    }

    // ── Overdue ───────────────────────────────────────────────────────────────

    @Test
    fun `overdue is the outstanding whose due date has passed`() {
        val result = report(
            projects = listOf(project("p1"), project("p2")),
            records = listOf(
                record("b1", "p1", dueOn = julyStart.plusDays(1)),
                record("b2", "p2", dueOn = LocalDate.of(2026, 12, 1)),
            ),
        )

        // p1 lapsed inside the period; p2 is not due until December.
        assertEquals(Money.of("11800.00", "USD"), result.totals.overdue["USD"])
        assertEquals(Money.of("23600.00", "USD"), result.totals.outstanding["USD"])
    }

    @Test
    fun `a settled bill is neither outstanding nor overdue`() {
        val result = report(
            projects = listOf(project("p1")),
            records = listOf(record("b1", "p1", dueOn = julyStart.plusDays(1))),
            payments = listOf(payment("pay1", "p1", "b1", "11800.00")),
        )

        assertTrue(result.totals.outstanding.isEmpty)
        assertTrue(result.totals.overdue.isEmpty)
    }

    @Test
    fun `a cancelled billing record counts in no basis at all`() {
        val cancelled = record("b1", "p1").copy(cancelledAt = Instant.EPOCH)
        val result = report(projects = listOf(project("p1")), records = listOf(cancelled))

        assertTrue(result.totals.billed.isEmpty)
        assertTrue(result.totals.outstanding.isEmpty)
        assertTrue(result.totals.overdue.isEmpty)
    }

    @Test
    fun `projects needing attention are surfaced`() {
        val result = report(
            projects = listOf(
                project("p1", budgetMinutes = 60),
                project("p2"),
            ),
            minutes = mapOf("p1" to 600),
        )

        assertEquals(listOf("p1"), result.needingAttention().map { it.projectId })
    }
}

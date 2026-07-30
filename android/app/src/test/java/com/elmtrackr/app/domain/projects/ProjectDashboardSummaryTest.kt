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
 * The dashboard's project block.
 *
 * Restrained by design: counts, two balances and one cash figure. The thing under
 * test above all is that it offers **no** combined income total — hourly earnings
 * are work performed this month while project payments are cash received, and
 * there is deliberately no API here that adds them.
 */
class ProjectDashboardSummaryTest {

    private val today = LocalDate.of(2026, 7, 29)
    private val monthStart = LocalDate.of(2026, 7, 1)

    private fun project(
        id: String,
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
        entered: String = "10000",
        currency: String = "USD",
        rate: String = "18",
        budgetMinutes: Int? = null,
        archivedAt: Instant? = null,
    ) = Project(
        id = id,
        userId = "u1",
        name = "Project $id",
        clientName = "Acme Ltd",
        workStatus = status,
        fee = ProjectFee.from(BigDecimal(entered), currency, TaxMode.EXCLUSIVE, BigDecimal(rate)),
        hourBudgetMinutes = budgetMinutes,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = archivedAt,
    )

    private fun record(
        projectId: String,
        currency: String = "USD",
        dueOn: LocalDate? = null,
        billedOn: LocalDate = monthStart.plusDays(2),
    ) = ProjectBillingRecord(
        id = "b-$projectId",
        userId = "u1",
        projectId = projectId,
        fee = ProjectFee.from(BigDecimal("10000"), currency, TaxMode.EXCLUSIVE, BigDecimal("18")),
        billedOn = billedOn,
        dueOn = dueOn,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun payment(
        projectId: String,
        amount: String,
        currency: String = "USD",
        paidOn: LocalDate = monthStart.plusDays(10),
    ) = ProjectPayment(
        id = "pay-$projectId-$amount",
        userId = "u1",
        projectId = projectId,
        billingRecordId = "b-$projectId",
        paidOn = paidOn,
        amount = Money.of(amount, currency),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun summary(
        projects: List<Project>,
        records: List<ProjectBillingRecord> = emptyList(),
        payments: List<ProjectPayment> = emptyList(),
        minutes: Map<String, Int> = emptyMap(),
    ): ProjectDashboardSummary {
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
        return ProjectDashboardSummaryBuilder.build(summaries, records, payments, today)
    }

    @Test
    fun `no projects produces an empty block the dashboard can omit`() {
        val result = summary(emptyList())

        assertTrue(result.isEmpty)
        assertEquals(0, result.activeProjectCount)
        assertTrue(result.outstanding.isEmpty)
        assertTrue(result.receivedThisMonth.isEmpty)
    }

    @Test
    fun `active projects are counted`() {
        val result = summary(
            listOf(
                project("p1", ProjectWorkStatus.ACTIVE),
                project("p2", ProjectWorkStatus.ACTIVE),
                project("p3", ProjectWorkStatus.PAUSED),
                project("p4", ProjectWorkStatus.DRAFT),
            ),
        )
        assertEquals(2, result.activeProjectCount)
    }

    @Test
    fun `unbilled counts work that is under way or finished with nothing billed`() {
        val result = summary(
            listOf(
                project("p1", ProjectWorkStatus.ACTIVE),
                project("p2", ProjectWorkStatus.COMPLETED),
                project("p3", ProjectWorkStatus.DRAFT),
            ),
        )
        // Drafts are not yet work, so they are not "unbilled".
        assertEquals(2, result.unbilledProjectCount)
    }

    @Test
    fun `a billed project is not counted as unbilled`() {
        val result = summary(listOf(project("p1")), records = listOf(record("p1")))
        assertEquals(0, result.unbilledProjectCount)
    }

    @Test
    fun `outstanding and overdue are reported separately`() {
        val result = summary(
            listOf(project("p1"), project("p2")),
            records = listOf(
                record("p1", dueOn = today.minusDays(5)),
                record("p2", dueOn = today.plusDays(30)),
            ),
        )

        assertEquals(Money.of("23600.00", "USD"), result.outstanding["USD"])
        // Only p1 has lapsed.
        assertEquals(Money.of("11800.00", "USD"), result.overdue["USD"])
    }

    @Test
    fun `cash received this month is reported with its tax portion separately`() {
        val result = summary(
            listOf(project("p1")),
            records = listOf(record("p1")),
            payments = listOf(payment("p1", "11800.00")),
        )

        assertEquals(Money.of("11800.00", "USD"), result.receivedThisMonth["USD"])
        assertEquals(Money.of("1800.00", "USD"), result.receivedTaxThisMonth["USD"])
        // Revenue before tax is derived, never conflated with the cash figure.
        assertEquals(Money.of("10000.00", "USD"), result.receivedBeforeTaxThisMonth["USD"])
    }

    @Test
    fun `a payment from last month is not this month's cash`() {
        val result = summary(
            listOf(project("p1")),
            records = listOf(record("p1", billedOn = LocalDate.of(2026, 6, 1))),
            payments = listOf(payment("p1", "11800.00", paidOn = LocalDate.of(2026, 6, 15))),
        )
        assertTrue(result.receivedThisMonth.isEmpty)
    }

    @Test
    fun `two currencies stay in separate buckets with no combined figure`() {
        val result = summary(
            listOf(project("p1", currency = "USD"), project("p2", currency = "ILS")),
            records = listOf(record("p1", "USD"), record("p2", "ILS")),
            payments = listOf(payment("p1", "1000.00", "USD"), payment("p2", "2000.00", "ILS")),
        )

        assertEquals(Money.of("1000.00", "USD"), result.receivedThisMonth["USD"])
        assertEquals(Money.of("2000.00", "ILS"), result.receivedThisMonth["ILS"])
        assertNull(result.receivedThisMonth.singleOrNull())
        assertEquals(listOf("ILS", "USD"), result.receivedThisMonth.currencies)
    }

    @Test
    fun `archived and cancelled projects are not counted or nagged about`() {
        val result = summary(
            listOf(
                project("p1", archivedAt = Instant.EPOCH),
                project("p2", status = ProjectWorkStatus.CANCELLED),
            ),
        )

        assertEquals(0, result.activeProjectCount)
        assertEquals(0, result.unbilledProjectCount)
        assertEquals(0, result.needsAttentionCount)
    }

    @Test
    fun `projects needing attention are counted for a single hint`() {
        val result = summary(
            listOf(
                project("p1", budgetMinutes = 60),
                project("p2"),
            ),
            minutes = mapOf("p1" to 600),
        )
        assertEquals(1, result.needsAttentionCount)
    }

    @Test
    fun `the summary exposes no combined income total`() {
        // A guard on the shape of the type: every money field is per-currency and
        // scoped to one accounting basis. If someone adds a single "income" figure
        // that merges hourly pay with project cash, this fails.
        val fields = ProjectDashboardSummary::class.java.declaredFields.map { it.name }

        assertFalse(fields.any { it.contains("gross", ignoreCase = true) })
        assertFalse(fields.any { it.contains("income", ignoreCase = true) })
        assertFalse(fields.any { it.contains("combined", ignoreCase = true) })
        // And every monetary field is a per-currency bucket, not a bare Money.
        val monetary = ProjectDashboardSummary::class.java.declaredFields
            .filter { it.type == com.elmtrackr.app.domain.money.Money::class.java }
        assertTrue(monetary.map { it.name }.toString(), monetary.isEmpty())
    }
}

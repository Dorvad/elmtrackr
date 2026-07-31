package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.model.Shift
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
import java.time.temporal.ChronoUnit

/** List filtering, search, metrics and work-status transitions. */
class ProjectListAndStatusTest {

    private val today = LocalDate.of(2026, 7, 29)
    private val now = Instant.ofEpochMilli(10_000)

    private fun project(
        id: String,
        name: String = "Project $id",
        client: String? = "Acme",
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
        entered: String = "10000",
        currency: String = "ILS",
        description: String? = null,
        updatedAt: Instant = now,
    ) = Project(
        id = id,
        userId = "u1",
        name = name,
        clientName = client,
        description = description,
        workStatus = status,
        fee = ProjectFee.from(BigDecimal(entered), currency, TaxMode.NONE, BigDecimal.ZERO),
        updatedAt = updatedAt,
    )

    private fun summary(
        project: Project,
        shifts: List<Shift> = emptyList(),
        records: List<ProjectBillingRecord> = emptyList(),
        payments: List<ProjectPayment> = emptyList(),
    ) = ProjectMetrics.summarize(project, shifts, records, payments, today)

    private fun shift(id: String, projectId: String?, hours: Long) = Shift(
        id = id,
        userId = "u1",
        startTime = Instant.parse("2026-07-20T08:00:00Z"),
        endTime = Instant.parse("2026-07-20T08:00:00Z").plus(hours, ChronoUnit.HOURS),
        projectId = projectId,
    )

    // ── Search ────────────────────────────────────────────────────────────────

    @Test
    fun `search matches project name, client and description`() {
        val summaries = listOf(
            summary(project("a", name = "Website rebuild", client = "Acme")),
            summary(project("b", name = "Brochure", client = "Globex")),
            summary(project("c", name = "App", client = "Initech", description = "Onboarding flow")),
        )
        assertEquals(listOf("a"), ProjectListFilter.apply(summaries, "website", ProjectStatusFilter.ALL).map { it.project.id })
        assertEquals(listOf("b"), ProjectListFilter.apply(summaries, "globex", ProjectStatusFilter.ALL).map { it.project.id })
        assertEquals(listOf("c"), ProjectListFilter.apply(summaries, "onboarding", ProjectStatusFilter.ALL).map { it.project.id })
    }

    @Test
    fun `search is case-insensitive and trims whitespace`() {
        val summaries = listOf(summary(project("a", name = "Website rebuild")))
        assertEquals(1, ProjectListFilter.apply(summaries, "  WEBSITE  ", ProjectStatusFilter.ALL).size)
    }

    @Test
    fun `a blank query matches everything`() {
        val summaries = listOf(summary(project("a")), summary(project("b")))
        assertEquals(2, ProjectListFilter.apply(summaries, "", ProjectStatusFilter.ALL).size)
        assertEquals(2, ProjectListFilter.apply(summaries, "   ", ProjectStatusFilter.ALL).size)
    }

    @Test
    fun `a query matching nothing returns an empty list`() {
        val summaries = listOf(summary(project("a", name = "Website")))
        assertTrue(ProjectListFilter.apply(summaries, "nothing here", ProjectStatusFilter.ALL).isEmpty())
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    @Test
    fun `each status filter selects only that status`() {
        val summaries = ProjectWorkStatus.entries.map { status ->
            summary(project(status.name, status = status))
        }
        ProjectStatusFilter.entries.filter { it != ProjectStatusFilter.ALL }.forEach { filter ->
            val result = ProjectListFilter.apply(summaries, "", filter)
            assertEquals(1, result.size)
            assertEquals(filter.workStatus, result.single().project.workStatus)
        }
    }

    @Test
    fun `the default filter hides archived projects`() {
        val summaries = listOf(
            summary(project("live", status = ProjectWorkStatus.ACTIVE)),
            summary(project("old", status = ProjectWorkStatus.ARCHIVED)),
        )
        val visible = ProjectListFilter.apply(summaries, "", ProjectStatusFilter.ALL)
        assertEquals(listOf("live"), visible.map { it.project.id })
    }

    @Test
    fun `archived projects are reachable through their own filter`() {
        val summaries = listOf(
            summary(project("live", status = ProjectWorkStatus.ACTIVE)),
            summary(project("old", status = ProjectWorkStatus.ARCHIVED)),
        )
        val archived = ProjectListFilter.apply(summaries, "", ProjectStatusFilter.ARCHIVED)
        assertEquals(listOf("old"), archived.map { it.project.id })
    }

    @Test
    fun `search and filter combine`() {
        val summaries = listOf(
            summary(project("a", name = "Website", status = ProjectWorkStatus.ACTIVE)),
            summary(project("b", name = "Website v2", status = ProjectWorkStatus.DRAFT)),
        )
        val result = ProjectListFilter.apply(summaries, "website", ProjectStatusFilter.DRAFT)
        assertEquals(listOf("b"), result.map { it.project.id })
    }

    @Test
    fun `counts reflect each filter`() {
        val summaries = listOf(
            summary(project("a", status = ProjectWorkStatus.ACTIVE)),
            summary(project("b", status = ProjectWorkStatus.ACTIVE)),
            summary(project("c", status = ProjectWorkStatus.DRAFT)),
            summary(project("d", status = ProjectWorkStatus.ARCHIVED)),
        )
        val counts = ProjectListFilter.counts(summaries)
        assertEquals(3, counts[ProjectStatusFilter.ALL])
        assertEquals(2, counts[ProjectStatusFilter.ACTIVE])
        assertEquals(1, counts[ProjectStatusFilter.DRAFT])
        assertEquals(1, counts[ProjectStatusFilter.ARCHIVED])
        assertEquals(0, counts[ProjectStatusFilter.PAUSED])
    }

    @Test
    fun `sorting puts work in progress before history`() {
        val summaries = listOf(
            summary(project("archived", status = ProjectWorkStatus.ARCHIVED)),
            summary(project("completed", status = ProjectWorkStatus.COMPLETED)),
            summary(project("active", status = ProjectWorkStatus.ACTIVE)),
            summary(project("draft", status = ProjectWorkStatus.DRAFT)),
        )
        assertEquals(
            listOf("active", "draft", "completed", "archived"),
            ProjectListFilter.sorted(summaries).map { it.project.id },
        )
    }

    // ── Metrics ───────────────────────────────────────────────────────────────

    @Test
    fun `tracked time only counts shifts linked to the project`() {
        val shifts = listOf(
            shift("s1", "p1", 4),
            shift("s2", "p1", 3),
            shift("s3", "p2", 8),
            shift("s4", null, 8),
        )
        val time = ProjectMetrics.timeSummary(shifts, "p1")
        assertEquals(7 * 60, time.trackedMinutes)
        assertEquals(2, time.shiftCount)
    }

    @Test
    fun `all project time summaries are produced in one pass`() {
        val shifts = listOf(shift("s1", "p1", 4), shift("s2", "p2", 2), shift("s3", null, 9))
        val summaries = ProjectMetrics.timeSummaries(shifts)
        assertEquals(setOf("p1", "p2"), summaries.keys)
        assertEquals(240, summaries.getValue("p1").trackedMinutes)
    }

    @Test
    fun `an active shift contributes no time until clock-out`() {
        val active = shift("s1", "p1", 4).copy(endTime = null)
        assertEquals(0, ProjectMetrics.timeSummary(listOf(active), "p1").trackedMinutes)
    }

    @Test
    fun `the effective hourly rate is based on the fee before tax`() {
        val fee = ProjectFee.from(BigDecimal("10000"), "ILS", TaxMode.EXCLUSIVE, BigDecimal("18"), "VAT")
        // 10 000 before tax over 40 hours = 250/hour. The 11 800 client total is
        // not the user's earnings.
        assertEquals(Money.of("250.00", "ILS"), ProjectMetrics.effectiveHourlyRate(fee, 40 * 60))
    }

    @Test
    fun `the effective hourly rate is null with no time tracked`() {
        val fee = ProjectFee.from(BigDecimal("10000"), "ILS", TaxMode.NONE, BigDecimal.ZERO)
        assertNull(ProjectMetrics.effectiveHourlyRate(fee, 0))
        assertNull(ProjectMetrics.effectiveHourlyRate(fee, -10))
    }

    @Test
    fun `the effective hourly rate handles a repeating quotient`() {
        val fee = ProjectFee.from(BigDecimal("1000"), "ILS", TaxMode.NONE, BigDecimal.ZERO)
        // 1000 / 3 hours = 333.333...
        assertEquals(Money.of("333.33", "ILS"), ProjectMetrics.effectiveHourlyRate(fee, 180))
    }

    @Test
    fun `budget utilization is null without a budget`() {
        assertNull(ProjectMetrics.budgetUtilization(600, null))
        assertNull(ProjectMetrics.budgetUtilization(600, 0))
        assertEquals(0.5f, ProjectMetrics.budgetUtilization(600, 1200))
    }

    // ── Delete versus archive ─────────────────────────────────────────────────

    private fun record(projectId: String = "p1", total: String = "1000") = ProjectBillingRecord(
        id = "bill-1",
        userId = "u1",
        projectId = projectId,
        fee = ProjectFee.from(BigDecimal(total), "ILS", TaxMode.NONE, BigDecimal.ZERO),
        billedOn = today,
    )

    @Test
    fun `an unused draft can be deleted permanently`() {
        val result = summary(project("p1", status = ProjectWorkStatus.DRAFT))
        assertTrue(result.canDeletePermanently)
    }

    @Test
    fun `a draft with tracked time must be archived`() {
        val result = summary(
            project("p1", status = ProjectWorkStatus.DRAFT),
            shifts = listOf(shift("s1", "p1", 2)),
        )
        assertFalse(result.canDeletePermanently)
    }

    @Test
    fun `a draft with a billing record must be archived`() {
        val result = summary(project("p1", status = ProjectWorkStatus.DRAFT), records = listOf(record()))
        assertFalse(result.canDeletePermanently)
    }

    @Test
    fun `a draft with a payment must be archived`() {
        val payment = ProjectPayment(
            id = "pay-1",
            userId = "u1",
            projectId = "p1",
            billingRecordId = "bill-1",
            paidOn = today,
            amount = Money.of("100", "ILS"),
        )
        val result = summary(
            project("p1", status = ProjectWorkStatus.DRAFT),
            records = listOf(record()),
            payments = listOf(payment),
        )
        assertFalse(result.canDeletePermanently)
    }

    @Test
    fun `a non-draft project is never deleted permanently`() {
        ProjectWorkStatus.entries.filter { it != ProjectWorkStatus.DRAFT }.forEach { status ->
            assertFalse(
                "status $status must not be permanently deletable",
                summary(project("p1", status = status)).canDeletePermanently,
            )
        }
    }

    // ── Work-status actions ───────────────────────────────────────────────────

    @Test
    fun `available actions suit the current status`() {
        assertTrue(ProjectWorkStatusActions.available(ProjectWorkStatus.DRAFT).contains(ProjectWorkAction.START))
        assertTrue(ProjectWorkStatusActions.available(ProjectWorkStatus.ACTIVE).contains(ProjectWorkAction.PAUSE))
        assertTrue(ProjectWorkStatusActions.available(ProjectWorkStatus.PAUSED).contains(ProjectWorkAction.RESUME))
        assertEquals(
            listOf(ProjectWorkAction.UNARCHIVE),
            ProjectWorkStatusActions.available(ProjectWorkStatus.ARCHIVED),
        )
    }

    @Test
    fun `every status offers at least one action`() {
        ProjectWorkStatus.entries.forEach { status ->
            assertTrue(status.name, ProjectWorkStatusActions.available(status).isNotEmpty())
        }
    }

    @Test
    fun `completing stamps a completion date`() {
        val result = ProjectWorkStatusActions.apply(
            project("p1", status = ProjectWorkStatus.ACTIVE),
            ProjectWorkAction.COMPLETE,
            now,
            today,
        )
        assertEquals(ProjectWorkStatus.COMPLETED, result.workStatus)
        assertEquals(today, result.completionDate)
    }

    @Test
    fun `reopening a completed project clears the completion date`() {
        val completed = ProjectWorkStatusActions.apply(
            project("p1"),
            ProjectWorkAction.COMPLETE,
            now,
            today,
        )
        val reopened = ProjectWorkStatusActions.apply(completed, ProjectWorkAction.RESUME, now, today)
        assertEquals(ProjectWorkStatus.ACTIVE, reopened.workStatus)
        assertNull(reopened.completionDate)
    }

    @Test
    fun `archiving is reversible`() {
        val archived = ProjectWorkStatusActions.apply(project("p1"), ProjectWorkAction.ARCHIVE, now, today)
        assertEquals(ProjectWorkStatus.ARCHIVED, archived.workStatus)
        assertEquals(now, archived.archivedAt)
        assertTrue(archived.isArchived)

        val restored = ProjectWorkStatusActions.apply(archived, ProjectWorkAction.UNARCHIVE, now, today)
        assertEquals(ProjectWorkStatus.ACTIVE, restored.workStatus)
        assertNull(restored.archivedAt)
        assertFalse(restored.isArchived)
    }

    @Test
    fun `pause and resume move only the work status`() {
        val original = project("p1", status = ProjectWorkStatus.ACTIVE)
        val paused = ProjectWorkStatusActions.apply(original, ProjectWorkAction.PAUSE, now, today)
        assertEquals(ProjectWorkStatus.PAUSED, paused.workStatus)
        assertEquals(original.fee, paused.fee)
        assertEquals(original.name, paused.name)
    }

    @Test
    fun `no work action touches the fee or the billing snapshot`() {
        val original = project("p1", entered = "10000")
        ProjectWorkAction.entries.forEach { action ->
            val result = ProjectWorkStatusActions.apply(original, action, now, today)
            assertEquals("action $action changed the fee", original.fee, result.fee)
            assertEquals(original.currencyCode, result.currencyCode)
        }
    }

    @Test
    fun `completing a project leaves its billing status alone`() {
        val billed = summary(project("p1"), records = listOf(record()))
        assertEquals(ProjectBillingStatus.BILLED, billed.billing.status)

        val completed = ProjectWorkStatusActions.apply(billed.project, ProjectWorkAction.COMPLETE, now, today)
        val after = summary(completed, records = listOf(record()))
        assertEquals(ProjectWorkStatus.COMPLETED, after.project.workStatus)
        // Still unpaid: completing work does not mean the client paid.
        assertEquals(ProjectBillingStatus.BILLED, after.billing.status)
    }

    @Test
    fun `a fully paid project is not automatically completed`() {
        val payment = ProjectPayment(
            id = "pay-1",
            userId = "u1",
            projectId = "p1",
            billingRecordId = "bill-1",
            paidOn = today,
            amount = Money.of("1000", "ILS"),
        )
        val result = summary(
            project("p1", status = ProjectWorkStatus.ACTIVE),
            records = listOf(record()),
            payments = listOf(payment),
        )
        assertEquals(ProjectBillingStatus.PAID, result.billing.status)
        assertEquals(ProjectWorkStatus.ACTIVE, result.project.workStatus)
    }
}

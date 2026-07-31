package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * When the app is allowed to nudge the user about money owed.
 *
 * The policy is deliberately quiet. Every case here is as much about what does
 * *not* notify as about what does: a settled project, a cancelled bill, a
 * disabled feature and a project with no due date all stay silent, and an overdue
 * one nudges weekly rather than daily.
 */
class ProjectBillingReminderPolicyTest {

    private val today = LocalDate.of(2026, 7, 29)

    private fun project(id: String = "p1", name: String = "Website rebuild") = Project(
        id = id,
        userId = "u1",
        name = name,
        clientName = "Acme Ltd",
        workStatus = ProjectWorkStatus.ACTIVE,
        fee = ProjectFee.from(BigDecimal("10000"), "USD", TaxMode.EXCLUSIVE, BigDecimal("18")),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun record(
        projectId: String = "p1",
        dueOn: LocalDate? = today,
        cancelledAt: Instant? = null,
    ) = ProjectBillingRecord(
        id = "b-$projectId",
        userId = "u1",
        projectId = projectId,
        fee = ProjectFee.from(BigDecimal("10000"), "USD", TaxMode.EXCLUSIVE, BigDecimal("18")),
        billedOn = today.minusDays(30),
        dueOn = dueOn,
        cancelledAt = cancelledAt,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun payment(amount: String, projectId: String = "p1") = ProjectPayment(
        id = "pay-$projectId",
        userId = "u1",
        projectId = projectId,
        billingRecordId = "b-$projectId",
        paidOn = today,
        amount = Money.of(amount, "USD"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun reminder(
        dueOn: LocalDate?,
        payments: List<ProjectPayment> = emptyList(),
        cancelledAt: Instant? = null,
        on: LocalDate = today,
    ) = ProjectBillingReminderPolicy.reminderFor(
        project = project(),
        billingRecords = listOf(record(dueOn = dueOn, cancelledAt = cancelledAt)),
        payments = payments,
        today = on,
    )

    @Test
    fun `a bill due today notifies`() {
        val result = reminder(dueOn = today)

        assertEquals(BillingReminderKind.DUE_TODAY, result?.kind)
        assertEquals(Money.of("11800.00", "USD"), result?.outstanding)
        assertEquals("Website rebuild", result?.projectName)
        assertEquals("Acme Ltd", result?.clientName)
    }

    @Test
    fun `a bill due in exactly the lead time notifies once`() {
        val lead = ProjectBillingReminderPolicy.UPCOMING_LEAD_DAYS
        assertEquals(
            BillingReminderKind.UPCOMING,
            reminder(dueOn = today.plusDays(lead))?.kind,
        )
        // Not on the days either side, so it does not nag in the run-up.
        assertNull(reminder(dueOn = today.plusDays(lead + 1)))
        assertNull(reminder(dueOn = today.plusDays(lead - 1)))
    }

    @Test
    fun `an overdue bill notifies on the weekly cadence only`() {
        val repeat = ProjectBillingReminderPolicy.OVERDUE_REPEAT_DAYS

        assertEquals(BillingReminderKind.OVERDUE, reminder(dueOn = today.minusDays(repeat))?.kind)
        assertEquals(BillingReminderKind.OVERDUE, reminder(dueOn = today.minusDays(repeat * 2))?.kind)
        // Quiet in between, so an old debt is not a daily interruption.
        assertNull(reminder(dueOn = today.minusDays(1)))
        assertNull(reminder(dueOn = today.minusDays(repeat + 1)))
    }

    @Test
    fun `a settled bill never notifies, however late it was paid`() {
        assertNull(
            reminder(
                dueOn = today.minusDays(ProjectBillingReminderPolicy.OVERDUE_REPEAT_DAYS),
                payments = listOf(payment("11800.00")),
            ),
        )
    }

    @Test
    fun `a partially paid bill still notifies, for the remaining balance`() {
        val result = reminder(dueOn = today, payments = listOf(payment("5000.00")))

        assertEquals(BillingReminderKind.DUE_TODAY, result?.kind)
        assertEquals(Money.of("6800.00", "USD"), result?.outstanding)
    }

    @Test
    fun `a cancelled bill never notifies`() {
        assertNull(reminder(dueOn = today, cancelledAt = Instant.EPOCH))
        assertNull(
            reminder(
                dueOn = today.minusDays(ProjectBillingReminderPolicy.OVERDUE_REPEAT_DAYS),
                cancelledAt = Instant.EPOCH,
            ),
        )
    }

    @Test
    fun `a bill with no due date never notifies`() {
        assertNull(reminder(dueOn = null))
    }

    @Test
    fun `a project with no billing record never notifies`() {
        assertNull(
            ProjectBillingReminderPolicy.reminderFor(
                project = project(),
                billingRecords = emptyList(),
                payments = emptyList(),
                today = today,
            ),
        )
    }

    // ── Feature and permission gates ──────────────────────────────────────────

    @Test
    fun `nothing is scheduled while Paid Projects is disabled`() {
        val reminders = ProjectBillingReminderPolicy.remindersFor(
            paidProjectsEnabled = false,
            notificationsEnabled = true,
            projects = listOf(project()),
            billingRecords = listOf(record(dueOn = today)),
            payments = emptyList(),
            today = today,
        )
        assertTrue(reminders.isEmpty())
    }

    @Test
    fun `nothing is scheduled while notifications are off`() {
        val reminders = ProjectBillingReminderPolicy.remindersFor(
            paidProjectsEnabled = true,
            notificationsEnabled = false,
            projects = listOf(project()),
            billingRecords = listOf(record(dueOn = today)),
            payments = emptyList(),
            today = today,
        )
        assertTrue(reminders.isEmpty())
    }

    @Test
    fun `re-enabling the feature brings the same reminders back`() {
        val projects = listOf(project())
        val records = listOf(record(dueOn = today))

        fun run(enabled: Boolean) = ProjectBillingReminderPolicy.remindersFor(
            paidProjectsEnabled = enabled,
            notificationsEnabled = true,
            projects = projects,
            billingRecords = records,
            payments = emptyList(),
            today = today,
        )

        // Disabling suppresses; the billing data is untouched, so re-enabling
        // produces exactly the reminder it did before.
        val before = run(true)
        assertTrue(run(false).isEmpty())
        val after = run(true)

        assertEquals(before, after)
        assertEquals(1, after.size)
        assertEquals(BillingReminderKind.DUE_TODAY, after.single().kind)
    }

    // ── Multiple projects ─────────────────────────────────────────────────────

    @Test
    fun `each project is judged on its own dates`() {
        val reminders = ProjectBillingReminderPolicy.remindersFor(
            paidProjectsEnabled = true,
            notificationsEnabled = true,
            projects = listOf(project("p1", "Due today"), project("p2", "Settled")),
            billingRecords = listOf(record("p1", dueOn = today), record("p2", dueOn = today)),
            payments = listOf(payment("11800.00", projectId = "p2")),
            today = today,
        )

        assertEquals(listOf("p1"), reminders.map { it.projectId })
    }

    @Test
    fun `at most one reminder per project per run`() {
        val reminders = ProjectBillingReminderPolicy.remindersFor(
            paidProjectsEnabled = true,
            notificationsEnabled = true,
            projects = listOf(project()),
            billingRecords = listOf(record(dueOn = today)),
            payments = emptyList(),
            today = today,
        )
        assertEquals(1, reminders.size)
    }

    @Test
    fun `the due-date comparison is date-only across a month boundary`() {
        // Due 30 June, checked on 7 July: exactly a week late, so it notifies.
        val result = ProjectBillingReminderPolicy.reminderFor(
            project = project(),
            billingRecords = listOf(record(dueOn = LocalDate.of(2026, 6, 30))),
            payments = emptyList(),
            today = LocalDate.of(2026, 7, 7),
        )
        assertEquals(BillingReminderKind.OVERDUE, result?.kind)
        assertEquals(-7L, result?.daysUntilDue)
    }
}

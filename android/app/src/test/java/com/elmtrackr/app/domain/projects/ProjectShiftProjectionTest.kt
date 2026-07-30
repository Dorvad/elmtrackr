package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.Project
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
import java.time.temporal.ChronoUnit

/**
 * Live figures for a project shift in progress.
 *
 * Two rules run through all of it: every rate is derived from the fee *before*
 * tax, never the client total, and a rate with no hours behind it is null rather
 * than zero — zero would read to the user as "you are earning nothing".
 */
class ProjectShiftProjectionTest {

    private val now = Instant.parse("2026-07-29T12:00:00Z")

    private fun project(
        entered: String = "10000",
        mode: TaxMode = TaxMode.EXCLUSIVE,
        rate: String = "18",
        currency: String = "USD",
        budgetMinutes: Int? = null,
        targetRate: String? = null,
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
        archivedAt: Instant? = null,
    ) = Project(
        id = "p1",
        userId = "u1",
        name = "Website rebuild",
        clientName = "Acme Ltd",
        workStatus = status,
        fee = ProjectFee.from(BigDecimal(entered), currency, mode, BigDecimal(rate)),
        hourBudgetMinutes = budgetMinutes,
        targetHourlyRate = targetRate?.let { Money.of(it, currency) },
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = archivedAt,
    )

    private fun completed(id: String, minutes: Long, projectId: String? = "p1") = Shift(
        id = id,
        userId = "u1",
        startTime = now.minus(minutes + 60, ChronoUnit.MINUTES),
        endTime = now.minus(60, ChronoUnit.MINUTES),
        compensationSource = if (projectId == null) CompensationSource.EMPLOYEE else CompensationSource.PROJECT,
        projectId = projectId,
    )

    private fun active(id: String, elapsedMinutes: Long, projectId: String? = "p1") = Shift(
        id = id,
        userId = "u1",
        startTime = now.minus(elapsedMinutes, ChronoUnit.MINUTES),
        endTime = null,
        compensationSource = if (projectId == null) CompensationSource.EMPLOYEE else CompensationSource.PROJECT,
        projectId = projectId,
    )

    @Test
    fun `the first shift on a project has no current rate but does have a projected one`() {
        val shift = active("a1", 120)
        val result = ProjectShiftProjectionCalculator.project(project(), shift, listOf(shift), now)

        assertEquals(0, result.trackedMinutes)
        assertEquals(120, result.activeMinutes)
        // No banked hours, so there is nothing to divide the fee by yet.
        assertNull(result.currentHourlyRate)
        // Two hours in, the pre-tax fee of 10000 projects to 5000/hour.
        assertEquals(Money.of("5000", "USD"), result.projectedHourlyRate)
    }

    @Test
    fun `the projected rate is based on the fee before tax, not the client total`() {
        // 10000 + 18% = 11800 to the client. A rate over 4 hours must be 2500,
        // not 2950 — the tax is collected for the authority, not earned.
        val shift = active("a1", 120)
        val result = ProjectShiftProjectionCalculator.project(
            project(),
            shift,
            listOf(completed("c1", 120), shift),
            now,
        )
        assertEquals(Money.of("2500", "USD"), result.projectedHourlyRate)
    }

    @Test
    fun `a tax-inclusive fee still projects from the base, not the entered amount`() {
        // 11800 including 18% has a base of 10000, so 4 hours reads 2500.
        val shift = active("a1", 120)
        val result = ProjectShiftProjectionCalculator.project(
            project(entered = "11800", mode = TaxMode.INCLUSIVE),
            shift,
            listOf(completed("c1", 120), shift),
            now,
        )
        assertEquals(Money.of("2500", "USD"), result.projectedHourlyRate)
    }

    @Test
    fun `the active shift is excluded from banked time even when present in the list`() {
        val shift = active("a1", 90)
        val result = ProjectShiftProjectionCalculator.project(
            project(),
            shift,
            listOf(completed("c1", 60), shift),
            now,
        )
        assertEquals(60, result.trackedMinutes)
        assertEquals(90, result.activeMinutes)
        assertEquals(150, result.projectedMinutes)
    }

    @Test
    fun `hours from another project and from employee work are not counted`() {
        val shift = active("a1", 60)
        val result = ProjectShiftProjectionCalculator.project(
            project(),
            shift,
            listOf(
                completed("c1", 60),
                completed("other", 600, projectId = "p2"),
                completed("wages", 600, projectId = null),
                shift,
            ),
            now,
        )
        assertEquals(60, result.trackedMinutes)
    }

    @Test
    fun `an employee-paid active shift contributes nothing`() {
        val shift = active("a1", 120, projectId = null)
        val result = ProjectShiftProjectionCalculator.project(project(), shift, listOf(shift), now)
        assertEquals(0, result.activeMinutes)
        assertEquals(0, result.trackedMinutes)
    }

    @Test
    fun `no active shift still reports the banked figures`() {
        val result = ProjectShiftProjectionCalculator.project(
            project(),
            activeShift = null,
            shifts = listOf(completed("c1", 120)),
            now = now,
        )
        assertEquals(120, result.trackedMinutes)
        assertEquals(0, result.activeMinutes)
        assertEquals(Money.of("5000", "USD"), result.currentHourlyRate)
        assertEquals(Money.of("5000", "USD"), result.projectedHourlyRate)
    }

    @Test
    fun `budget remaining goes negative rather than clamping`() {
        val shift = active("a1", 120)
        val result = ProjectShiftProjectionCalculator.project(
            project(budgetMinutes = 180),
            shift,
            listOf(completed("c1", 120), shift),
            now,
        )
        assertEquals(-60, result.budgetRemainingMinutes)
        assertTrue(result.isOverBudget)
    }

    @Test
    fun `no budget means no budget figure`() {
        val shift = active("a1", 60)
        val result = ProjectShiftProjectionCalculator.project(project(), shift, listOf(shift), now)
        assertNull(result.budgetRemainingMinutes)
        assertFalse(result.isOverBudget)
    }

    @Test
    fun `a zero budget is treated as no budget`() {
        val shift = active("a1", 60)
        val result = ProjectShiftProjectionCalculator.project(
            project(budgetMinutes = 0),
            shift,
            listOf(shift),
            now,
        )
        assertNull(result.budgetRemainingMinutes)
    }

    @Test
    fun `without a target rate there is no countdown to invent`() {
        val shift = active("a1", 60)
        val result = ProjectShiftProjectionCalculator.project(project(), shift, listOf(shift), now)
        assertNull(result.minutesUntilTargetRate)
        assertFalse(result.isBelowTargetRate)
    }

    @Test
    fun `the target-rate countdown measures hours left before the rate drops below it`() {
        // A 10000 pre-tax fee hits 100/hour at exactly 100 hours = 6000 minutes.
        // Two hours in, 5880 minutes of work remain before it crosses.
        val shift = active("a1", 120)
        val result = ProjectShiftProjectionCalculator.project(
            project(targetRate = "100"),
            shift,
            listOf(shift),
            now,
        )
        assertEquals(5880, result.minutesUntilTargetRate)
        assertFalse(result.isBelowTargetRate)
    }

    @Test
    fun `the countdown goes negative once the rate is already below target`() {
        val shift = active("a1", 60)
        val result = ProjectShiftProjectionCalculator.project(
            project(targetRate = "100"),
            shift,
            // 6000 minutes banked is exactly the crossing point; one more hour
            // is past it.
            listOf(completed("c1", 6000), shift),
            now,
        )
        assertEquals(-60, result.minutesUntilTargetRate)
        assertTrue(result.isBelowTargetRate)
    }

    @Test
    fun `a target rate of zero is ignored rather than dividing by zero`() {
        val shift = active("a1", 60)
        val result = ProjectShiftProjectionCalculator.project(
            project(targetRate = "0"),
            shift,
            listOf(shift),
            now,
        )
        assertNull(result.minutesUntilTargetRate)
    }

    @Test
    fun `a target rate in another currency yields no countdown rather than a converted guess`() {
        val base = project(targetRate = "100")
        val crossCurrency = base.copy(targetHourlyRate = Money.of("100", "EUR"))
        val shift = active("a1", 60)

        val result = ProjectShiftProjectionCalculator.project(crossCurrency, shift, listOf(shift), now)

        assertNull(result.minutesUntilTargetRate)
    }

    @Test
    fun `a zero fee produces no rate and no countdown`() {
        val shift = active("a1", 120)
        val result = ProjectShiftProjectionCalculator.project(
            project(entered = "0", rate = "0", targetRate = "100"),
            shift,
            listOf(completed("c1", 60), shift),
            now,
        )
        assertEquals(Money.of("0", "USD"), result.projectedHourlyRate)
        assertNull(result.minutesUntilTargetRate)
    }

    @Test
    fun `a repeating decimal rate does not lose or invent money`() {
        // 10000 over 3 hours is 3333.33...; the figure must round for display
        // without the arithmetic drifting.
        val shift = active("a1", 60)
        val result = ProjectShiftProjectionCalculator.project(
            project(),
            shift,
            listOf(completed("c1", 120), shift),
            now,
        )
        assertEquals(BigDecimal("3333.33"), result.projectedHourlyRate!!.amount)
    }

    @Test
    fun `a tiny target rate against a large fee does not overflow the countdown`() {
        val shift = active("a1", 1)
        val result = ProjectShiftProjectionCalculator.project(
            project(entered = "1000000000", targetRate = "0.01"),
            shift,
            listOf(shift),
            now,
        )
        // Clamped rather than wrapped negative, which would read as "already
        // below target" on a project that plainly is not.
        assertTrue(result.minutesUntilTargetRate!! > 0)
    }
}

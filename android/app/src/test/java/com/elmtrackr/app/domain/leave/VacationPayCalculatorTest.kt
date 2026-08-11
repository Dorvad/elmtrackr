package com.elmtrackr.app.domain.leave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class VacationPayCalculatorTest {

    // The absence being valued is in August, so the three whole preceding months
    // are May, June and July.
    private val august = LocalDate.of(2026, 8, 12)

    private fun month(
        yearMonth: String,
        gross: Double,
        daysWorked: Int = 20,
        minutes: Int = 20 * 480,
    ) = LeaveEarningsMonth(YearMonth.parse(yearMonth), gross, daysWorked, minutes)

    @Test
    fun `statutory average divides the three preceding months by 90`() {
        val history = LeaveEarningsHistory(
            listOf(
                month("2026-05", 9_000.0),
                month("2026-06", 9_000.0),
                month("2026-07", 9_000.0),
            ),
        )

        val average = VacationPayCalculator.israeliStatutoryAverage(history, august)

        assertNotNull(average)
        assertEquals(300.0, average!!.amountPerDay, 0.0001)
        assertEquals(27_000.0, average.grossIncluded, 0.0001)
        assertEquals(90.0, average.divisor, 0.0001)
        assertEquals(LocalDate.of(2026, 5, 1), average.periodStart)
        assertEquals(LocalDate.of(2026, 7, 31), average.periodEnd)
        assertFalse(average.usedFallbackPeriod)
    }

    @Test
    fun `the month the absence falls in is not averaged in`() {
        // August is still in progress; a part month would drag the figure down for
        // no reason the user would accept.
        val history = LeaveEarningsHistory(
            listOf(
                month("2026-05", 9_000.0),
                month("2026-06", 9_000.0),
                month("2026-07", 9_000.0),
                month("2026-08", 500.0, daysWorked = 2),
            ),
        )

        val average = VacationPayCalculator.israeliStatutoryAverage(history, august)

        assertEquals(27_000.0, average!!.grossIncluded, 0.0001)
        assertEquals(LocalDate.of(2026, 7, 31), average.periodEnd)
    }

    @Test
    fun `an incomplete recent period falls back to the strongest complete three months`() {
        // July has no earned work, so May-July cannot be used. Of the complete runs
        // in the preceding twelve months, Jan-Mar is the strongest.
        val history = LeaveEarningsHistory(
            listOf(
                month("2025-12", 4_000.0),
                month("2026-01", 12_000.0),
                month("2026-02", 12_000.0),
                month("2026-03", 12_000.0),
                month("2026-04", 5_000.0),
                month("2026-05", 5_000.0),
                month("2026-06", 5_000.0),
                month("2026-07", 0.0, daysWorked = 0, minutes = 0),
            ),
        )

        val average = VacationPayCalculator.israeliStatutoryAverage(history, august)

        assertNotNull(average)
        assertEquals(36_000.0 / 90.0, average!!.amountPerDay, 0.0001)
        assertEquals(LocalDate.of(2026, 1, 1), average.periodStart)
        assertEquals(LocalDate.of(2026, 3, 31), average.periodEnd)
        assertTrue("a substituted period must be surfaced", average.usedFallbackPeriod)
    }

    @Test
    fun `no complete three-month run reports insufficient history rather than a partial average`() {
        val history = LeaveEarningsHistory(
            listOf(
                month("2026-06", 9_000.0),
                month("2026-07", 9_000.0),
            ),
        )

        assertNull(VacationPayCalculator.israeliStatutoryAverage(history, august))
        assertNull(VacationPayCalculator.workdayAverage(history, august))
    }

    @Test
    fun `an empty history reports insufficient history`() {
        assertNull(VacationPayCalculator.israeliStatutoryAverage(LeaveEarningsHistory.EMPTY, august))
    }

    @Test
    fun `a month with earnings but no worked days does not count as complete`() {
        val history = LeaveEarningsHistory(
            listOf(
                month("2026-05", 9_000.0),
                month("2026-06", 9_000.0, daysWorked = 0),
                month("2026-07", 9_000.0),
            ),
        )

        assertNull(VacationPayCalculator.israeliStatutoryAverage(history, august))
    }

    @Test
    fun `workday average divides by the days actually worked`() {
        val history = LeaveEarningsHistory(
            listOf(
                month("2026-05", 8_000.0, daysWorked = 20),
                month("2026-06", 8_000.0, daysWorked = 20),
                month("2026-07", 8_000.0, daysWorked = 20),
            ),
        )

        val average = VacationPayCalculator.workdayAverage(history, august)

        assertEquals(400.0, average!!.amountPerDay, 0.0001)
        assertEquals(60.0, average.divisor, 0.0001)
    }

    @Test
    fun `the statutory divisor stays 90 even for a part-time worker`() {
        // Ten worked days a month over three months still divides by 90, which is
        // the point of the calendar-day basis: it is not a per-workday average.
        val history = LeaveEarningsHistory(
            listOf(
                month("2026-05", 4_500.0, daysWorked = 10),
                month("2026-06", 4_500.0, daysWorked = 10),
                month("2026-07", 4_500.0, daysWorked = 10),
            ),
        )

        val statutory = VacationPayCalculator.israeliStatutoryAverage(history, august)!!
        val perWorkday = VacationPayCalculator.workdayAverage(history, august)!!

        assertEquals(150.0, statutory.amountPerDay, 0.0001)
        assertEquals(450.0, perWorkday.amountPerDay, 0.0001)
    }
}

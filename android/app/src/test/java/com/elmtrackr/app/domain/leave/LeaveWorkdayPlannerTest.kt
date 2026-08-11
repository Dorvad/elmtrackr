package com.elmtrackr.app.domain.leave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LeaveWorkdayPlannerTest {

    // 2026-08-10 is a Monday, so the 10th to the 16th is Mon through Sun.
    private val monday = LocalDate.of(2026, 8, 10)
    private val sunday = LocalDate.of(2026, 8, 16)

    private fun dates(vararg iso: String) = iso.map { LocalDate.parse(it) }

    @Test
    fun `every calendar date in the range is offered`() {
        val proposals = LeaveWorkdayPlanner.propose(monday, sunday, dates("2026-07-06"))

        assertEquals(7, proposals.size)
        assertEquals(monday, proposals.first().date)
        assertEquals(sunday, proposals.last().date)
    }

    @Test
    fun `weekdays the user regularly works are proposed`() {
        // Three past Mondays and three past Wednesdays.
        val worked = dates(
            "2026-07-06", "2026-07-13", "2026-07-20",
            "2026-07-08", "2026-07-15", "2026-07-22",
        )

        val selected = LeaveWorkdayPlanner.propose(monday, sunday, worked)
            .filter { it.selected }
            .map { it.date }

        assertEquals(dates("2026-08-10", "2026-08-12"), selected)
    }

    @Test
    fun `a weekday worked only once is not proposed`() {
        // One stray Saturday must not turn every Saturday of a holiday into paid
        // leave the user then has to notice and untick.
        val worked = dates("2026-07-06", "2026-07-13", "2026-07-11")

        val proposals = LeaveWorkdayPlanner.propose(monday, sunday, worked)
        val saturday = proposals.first { it.date == LocalDate.of(2026, 8, 15) }

        assertFalse(saturday.selected)
        assertEquals(1, saturday.observedCount)
    }

    @Test
    fun `with no history nothing is proposed and the user is asked`() {
        val proposals = LeaveWorkdayPlanner.propose(monday, sunday, emptyList())

        assertEquals(7, proposals.size)
        assertTrue(proposals.none { it.selected })
        assertTrue(LeaveWorkdayPlanner.needsUserInput(proposals))
    }

    @Test
    fun `a recognised pattern does not need the user to intervene`() {
        val worked = dates("2026-07-06", "2026-07-13", "2026-07-20")

        val proposals = LeaveWorkdayPlanner.propose(monday, sunday, worked)

        assertFalse(LeaveWorkdayPlanner.needsUserInput(proposals))
    }

    @Test
    fun `a single-day range still resolves`() {
        val proposals = LeaveWorkdayPlanner.propose(monday, monday, dates("2026-07-06", "2026-07-13"))

        assertEquals(1, proposals.size)
        assertTrue(proposals.single().selected)
    }

    @Test
    fun `the observation threshold is adjustable`() {
        val worked = dates("2026-07-06")

        val strict = LeaveWorkdayPlanner.propose(monday, monday, worked, minimumObservations = 2)
        val lenient = LeaveWorkdayPlanner.propose(monday, monday, worked, minimumObservations = 1)

        assertFalse(strict.single().selected)
        assertTrue(lenient.single().selected)
    }
}

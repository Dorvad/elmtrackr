package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.AbsenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LeaveConflictDetectorTest {

    private fun dates(vararg iso: String) = iso.map { LocalDate.parse(it) }

    @Test
    fun `leave already reported on the same date blocks the save`() {
        val conflicts = LeaveConflictDetector.detect(
            dates = dates("2026-08-12"),
            type = AbsenceType.VACATION,
            existingLeave = listOf(ExistingLeaveDate(LocalDate.parse("2026-08-12"), AbsenceType.SICK)),
            workedDates = emptyList(),
        )

        val blocking = LeaveConflictDetector.blocking(conflicts)
        assertEquals(1, blocking.size)
        assertEquals(AbsenceType.SICK, blocking.single().existingType)
    }

    @Test
    fun `a work shift on the same date warns but does not block`() {
        // Working the morning and taking the afternoon off is ordinary. Blocking it
        // would force the user to misreport one or the other.
        val conflicts = LeaveConflictDetector.detect(
            dates = dates("2026-08-12"),
            type = AbsenceType.VACATION,
            existingLeave = emptyList(),
            workedDates = listOf(WorkedDate(LocalDate.parse("2026-08-12"), workedMinutes = 240)),
        )

        assertTrue(conflicts.any { it is LeaveConflict.WorkShiftSameDate })
        assertTrue(LeaveConflictDetector.blocking(conflicts).isEmpty())
    }

    @Test
    fun `an archived workplace warns but does not block`() {
        val conflicts = LeaveConflictDetector.detect(
            dates = dates("2026-08-12"),
            type = AbsenceType.VACATION,
            existingLeave = emptyList(),
            workedDates = emptyList(),
            workplaceIsArchived = true,
        )

        assertTrue(conflicts.any { it is LeaveConflict.ArchivedWorkplace })
        assertTrue(LeaveConflictDetector.blocking(conflicts).isEmpty())
    }

    @Test
    fun `a clean entry reports nothing`() {
        val conflicts = LeaveConflictDetector.detect(
            dates = dates("2026-08-12", "2026-08-13"),
            type = AbsenceType.VACATION,
            existingLeave = listOf(ExistingLeaveDate(LocalDate.parse("2026-07-01"), AbsenceType.VACATION)),
            workedDates = listOf(WorkedDate(LocalDate.parse("2026-08-20"), 480)),
        )

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `a sick entry starting the day after an existing period is detected as adjacent`() {
        // This matters because the ordinal drives the ladder: two separate periods
        // restart at 0 and 50 percent where one continuous illness would have
        // reached full pay.
        val conflicts = LeaveConflictDetector.adjacentSickPeriods(
            dates = dates("2026-08-11", "2026-08-12"),
            existing = listOf(SickPeriod("event-1", LocalDate.parse("2026-08-08"), LocalDate.parse("2026-08-10"))),
        )

        assertEquals(1, conflicts.size)
        assertEquals("event-1", conflicts.single().existingEventId)
    }

    @Test
    fun `a sick entry ending the day before an existing period is detected as adjacent`() {
        val conflicts = LeaveConflictDetector.adjacentSickPeriods(
            dates = dates("2026-08-05", "2026-08-07"),
            existing = listOf(SickPeriod("event-1", LocalDate.parse("2026-08-08"), LocalDate.parse("2026-08-10"))),
        )

        assertEquals(1, conflicts.size)
    }

    @Test
    fun `an overlapping sick period is detected`() {
        val conflicts = LeaveConflictDetector.adjacentSickPeriods(
            dates = dates("2026-08-09", "2026-08-12"),
            existing = listOf(SickPeriod("event-1", LocalDate.parse("2026-08-08"), LocalDate.parse("2026-08-10"))),
        )

        assertEquals(1, conflicts.size)
    }

    @Test
    fun `a sick period two days away is a separate illness`() {
        val conflicts = LeaveConflictDetector.adjacentSickPeriods(
            dates = dates("2026-08-13"),
            existing = listOf(SickPeriod("event-1", LocalDate.parse("2026-08-08"), LocalDate.parse("2026-08-10"))),
        )

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `adjacency never blocks, so a merge is always offered rather than applied`() {
        val conflicts = LeaveConflictDetector.detect(
            dates = dates("2026-08-11"),
            type = AbsenceType.SICK,
            existingLeave = emptyList(),
            workedDates = emptyList(),
            existingSickPeriods = listOf(
                SickPeriod("event-1", LocalDate.parse("2026-08-08"), LocalDate.parse("2026-08-10")),
            ),
        )

        assertTrue(conflicts.any { it is LeaveConflict.AdjacentSickPeriod })
        assertTrue(LeaveConflictDetector.blocking(conflicts).isEmpty())
    }

    @Test
    fun `vacation does not look for adjacent illnesses`() {
        val conflicts = LeaveConflictDetector.detect(
            dates = dates("2026-08-11"),
            type = AbsenceType.VACATION,
            existingLeave = emptyList(),
            workedDates = emptyList(),
            existingSickPeriods = listOf(
                SickPeriod("event-1", LocalDate.parse("2026-08-08"), LocalDate.parse("2026-08-10")),
            ),
        )

        assertTrue(conflicts.none { it is LeaveConflict.AdjacentSickPeriod })
    }
}

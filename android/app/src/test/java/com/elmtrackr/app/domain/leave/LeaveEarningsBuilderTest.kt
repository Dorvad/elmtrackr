package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.AbsenceAllocation
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LeaveEarningsBuilderTest {

    private fun allocation(
        id: String,
        date: String,
        gross: Double,
        units: Double = 1.0,
        unit: LeaveBalanceUnit = LeaveBalanceUnit.DAYS,
        workplaceId: String = "wp-a",
        eventId: String = "event-sick",
    ) = AbsenceAllocation(
        id = id,
        userId = "u1",
        absenceEventId = eventId,
        workplaceId = workplaceId,
        affectedDate = LocalDate.parse(date),
        entitlementUnits = units,
        unit = unit,
        estimatedGrossPay = gross,
    )

    private val types = mapOf(
        "event-sick" to AbsenceType.SICK,
        "event-vacation" to AbsenceType.VACATION,
    )

    private fun build(allocations: List<AbsenceAllocation>) = LeaveEarningsBuilder.buildMonthly(
        year = 2026,
        month = 8,
        currencyCode = "ILS",
        allocations = allocations,
        typeOf = { types[it.absenceEventId] },
        workplaceNameOf = { id -> if (id == "wp-a") "Main job" else "Escape Room" },
    )

    @Test
    fun `vacation and sick pay are reported separately, never merged`() {
        val result = build(
            listOf(
                allocation("a1", "2026-08-03", 410.0, eventId = "event-vacation"),
                allocation("a2", "2026-08-04", 410.0, eventId = "event-vacation"),
                allocation("a3", "2026-08-12", 180.0, units = 0.5, eventId = "event-sick"),
            ),
        )

        assertEquals(820.0, result.vacationGross, 0.0001)
        assertEquals(180.0, result.sickGross, 0.0001)
        assertEquals(2.0, result.vacationDays, 0.0001)
        assertEquals(0.5, result.sickDays, 0.0001)
        assertEquals(1_000.0, result.paidLeaveGross, 0.0001)
    }

    @Test
    fun `paid leave carries no worked minutes and no shift count`() {
        // There is deliberately nowhere on this type to put either. Worked hours
        // stay a property of shifts alone, which is what keeps the hours report's
        // regular-plus-overtime-plus-weekend invariant intact and stops leave from
        // creating overtime.
        val result = build(listOf(allocation("a1", "2026-08-03", 410.0)))

        val fields = result::class.java.declaredFields.map { it.name }
        assertTrue(
            "leave earnings must not carry worked minutes: $fields",
            fields.none { it.contains("workedMinutes", ignoreCase = true) },
        )
        assertTrue(
            "leave earnings must not carry a shift count: $fields",
            fields.none { it.contains("shiftCount", ignoreCase = true) },
        )
    }

    @Test
    fun `each workplace is totalled on its own`() {
        val result = build(
            listOf(
                allocation("a1", "2026-08-03", 410.0, workplaceId = "wp-a", eventId = "event-vacation"),
                allocation("a2", "2026-08-12", 180.0, workplaceId = "wp-b", eventId = "event-sick"),
                allocation("a3", "2026-08-13", 180.0, workplaceId = "wp-b", eventId = "event-sick"),
            ),
        )

        assertEquals(2, result.byWorkplace.size)
        val main = result.byWorkplace.first { it.workplaceId == "wp-a" }
        val escapeRoom = result.byWorkplace.first { it.workplaceId == "wp-b" }

        assertEquals("Main job", main.workplaceName)
        assertEquals(410.0, main.vacationGross, 0.0001)
        assertEquals(0.0, main.sickGross, 0.0001)

        assertEquals("Escape Room", escapeRoom.workplaceName)
        assertEquals(360.0, escapeRoom.sickGross, 0.0001)
        assertEquals(2.0, escapeRoom.sickDays, 0.0001)
    }

    @Test
    fun `days and hours are kept apart rather than converted`() {
        val result = build(
            listOf(
                allocation("a1", "2026-08-03", 410.0, eventId = "event-vacation"),
                allocation("a2", "2026-08-04", 200.0, units = 4.0, unit = LeaveBalanceUnit.HOURS, eventId = "event-vacation"),
            ),
        )

        assertEquals(1.0, result.vacationDays, 0.0001)
        assertEquals(4.0, result.vacationHours, 0.0001)
        assertEquals(610.0, result.vacationGross, 0.0001)
    }

    @Test
    fun `an allocation whose leave type cannot be resolved is left out of both columns`() {
        val result = build(
            listOf(
                allocation("a1", "2026-08-03", 410.0, eventId = "event-vacation"),
                allocation("a2", "2026-08-05", 999.0, eventId = "event-gone"),
            ),
        )

        assertEquals(410.0, result.paidLeaveGross, 0.0001)
        assertEquals(1, result.byWorkplace.size)
    }

    @Test
    fun `a month with no reported leave is empty`() {
        val result = build(emptyList())

        assertTrue(result.isEmpty)
        assertEquals(0.0, result.paidLeaveGross, 0.0001)
        assertEquals(2026, result.year)
        assertEquals(8, result.month)
    }
}

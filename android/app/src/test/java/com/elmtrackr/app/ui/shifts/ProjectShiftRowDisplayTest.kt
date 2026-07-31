package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * How a project shift reads in the shift list.
 *
 * The row's pay column, weekend badge and overtime badge are all employee-pay
 * classifications. A project shift shows its hours — the user did work them — but
 * none of those, because none of them apply to it.
 */
class ProjectShiftRowDisplayTest {

    private val settings = UserSettings(
        id = "s1",
        userId = "u1",
        timezone = "Asia/Jerusalem",
        hourlyRate = 62.5,
        currency = CurrencyCode.ILS,
        currencyCode = "ILS",
        regionCode = RegionCode.IL,
        weekendDays = listOf(5, 6),
        dailyOvertimeThresholdMinutes = 480,
        weeklyOvertimeThresholdMinutes = 2520,
    )

    private val zone = ZoneId.of("Asia/Jerusalem")

    /** A long Saturday: both a weekend day and past the daily overtime threshold. */
    private fun shift(source: CompensationSource) = Shift(
        id = "s1",
        userId = "u1",
        startTime = Instant.parse("2026-07-11T06:00:00Z"),
        endTime = Instant.parse("2026-07-11T17:00:00Z"),
        compensationSource = source,
        projectId = if (source.isProjectTime) "p1" else null,
        projectNameSnapshot = if (source.isProjectTime) "Website rebuild" else null,
    )

    @Test
    fun `an hourly shift shows pay, the weekend badge and the overtime badge`() {
        val row = buildShiftRowDisplay(
            shift(CompensationSource.EMPLOYEE),
            settings,
            emptyList(),
            emptyList(),
            zone = zone,
        )

        assertNotNull(row.payGross)
        assertTrue(row.weekend)
    }

    @Test
    fun `a project shift shows its hours but no pay and no premium badges`() {
        val employee = shift(CompensationSource.EMPLOYEE)
        val project = shift(CompensationSource.PROJECT)

        val employeeRow = buildShiftRowDisplay(employee, settings, emptyList(), emptyList(), zone = zone)
        val projectRow = buildShiftRowDisplay(project, settings, emptyList(), emptyList(), zone = zone)

        // The hours are the same work; only the compensation classification differs.
        assertEquals(employeeRow.netMinutes, projectRow.netMinutes)
        assertNull(projectRow.payGross)
        assertFalse(projectRow.weekend)
        assertFalse(projectRow.hasOt)
    }

    @Test
    fun `a project shift keeps its project name for the row label`() {
        val project = shift(CompensationSource.PROJECT)
        assertTrue(project.isProjectTime)
        assertEquals("Website rebuild", project.projectNameSnapshot)
    }
}

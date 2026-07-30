package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ReportInsightsBuilder
import com.elmtrackr.app.domain.TaskMonthlyReportBuilder
import com.elmtrackr.app.domain.WeeklyBreakdownBuilder
import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Project time must not reach any employee-pay figure.
 *
 * The pattern throughout: build a set of employee shifts, compute everything,
 * then add project shifts on top and compute again. Every employee-pay number
 * must be byte-identical. Adding project hours can only ever *remove* money from
 * a wage total if it leaks — so equality is the assertion that matters.
 */
class ProjectTimePayrollIsolationTest {

    private val settings = UserSettings(
        id = "s1",
        userId = "u1",
        timezone = "Asia/Jerusalem",
        hourlyRate = 62.5,
        currency = CurrencyCode.ILS,
        currencyCode = "ILS",
        regionCode = RegionCode.IL,
        weekendDays = listOf(5, 6),
        dailyOvertimeThresholdMinutes = 516,
        weeklyOvertimeThresholdMinutes = 2520,
    )

    private val zone = ZoneId.of("Asia/Jerusalem")

    private fun shift(
        id: String,
        startIso: String,
        hours: Long,
        source: CompensationSource = CompensationSource.EMPLOYEE,
        projectId: String? = null,
        taskId: String? = null,
    ): Shift {
        val start = Instant.parse(startIso)
        return Shift(
            id = id,
            userId = "u1",
            startTime = start,
            endTime = start.plus(hours, ChronoUnit.HOURS),
            compensationSource = source,
            projectId = projectId,
            projectNameSnapshot = projectId?.let { "Client work" },
            taskId = taskId,
            taskNameSnapshot = taskId?.let { "Delivery" },
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    /** Five ordinary weekdays, 8h each — well under every threshold. */
    private fun employeeShifts(): List<Shift> = (0 until 5).map { day ->
        shift("emp-$day", "2026-07-06T06:00:00Z", 8, taskId = "task-1")
            .let { it.copy(startTime = it.startTime.plus(day.toLong(), ChronoUnit.DAYS), endTime = it.endTime!!.plus(day.toLong(), ChronoUnit.DAYS)) }
    }

    /** Long project days in the same week, chosen to blow past both thresholds if they leaked. */
    private fun projectShifts(): List<Shift> = (0 until 5).map { day ->
        shift(
            "proj-$day",
            "2026-07-06T18:00:00Z",
            5,
            source = CompensationSource.PROJECT,
            projectId = "p1",
            taskId = "task-1",
        ).let { it.copy(startTime = it.startTime.plus(day.toLong(), ChronoUnit.DAYS), endTime = it.endTime!!.plus(day.toLong(), ChronoUnit.DAYS)) }
    }

    @Test
    fun `a project shift earns no wage`() {
        val project = projectShifts().first()
        assertNull(PayrollCalculator.calculateShiftPay(project, settings))
        assertNull(PayrollCalculator.calculateShiftPayInContext(project, projectShifts(), settings))
    }

    @Test
    fun `an employee shift still earns a wage in a mixed list`() {
        val mixed = employeeShifts() + projectShifts()
        val pay = PayrollCalculator.calculateShiftPayInContext(employeeShifts().first(), mixed, settings)
        assertNotNull(pay)
        assertTrue(pay!!.totalGross > 0.0)
    }

    @Test
    fun `monthly pay ignores project time entirely`() {
        val employeeOnly = PayrollCalculator.sumMonthlyPay(employeeShifts(), settings)
        val mixed = PayrollCalculator.sumMonthlyPay(employeeShifts() + projectShifts(), settings)

        assertEquals(employeeOnly.totalGross, mixed.totalGross, 0.0)
        assertEquals(employeeOnly.regularGross, mixed.regularGross, 0.0)
        assertEquals(employeeOnly.overtimeGross, mixed.overtimeGross, 0.0)
        assertEquals(employeeOnly.weekendGross, mixed.weekendGross, 0.0)
        assertEquals(employeeOnly.holidayGross, mixed.holidayGross, 0.0)
        assertEquals(employeeOnly.nightGross, mixed.nightGross, 0.0)
    }

    @Test
    fun `project hours never push employee hours into daily or weekly overtime`() {
        // 5 x 8h employee = 2400 min, under the 2520 weekly threshold. Adding
        // 5 x 5h of project time would take the week to 3900 min and invent
        // 1380 minutes of overtime if the two were pooled.
        val employeeOnly = MonthlyReportBuilder.buildMonthlyReport(2026, 7, employeeShifts(), settings)
        val mixed = MonthlyReportBuilder.buildMonthlyReport(2026, 7, employeeShifts() + projectShifts(), settings)

        assertEquals(0, employeeOnly.overtimeMinutes)
        assertEquals(employeeOnly.overtimeMinutes, mixed.overtimeMinutes)
        assertEquals(employeeOnly.totalMinutes, mixed.totalMinutes)
        assertEquals(employeeOnly.regularMinutes, mixed.regularMinutes)
        assertEquals(employeeOnly.weekendMinutes, mixed.weekendMinutes)
        assertEquals(employeeOnly.shiftCount, mixed.shiftCount)
    }

    @Test
    fun `a project shift on a weekend produces no weekend premium`() {
        // 2026-07-11 is a Saturday, a configured weekend day.
        val weekendProject = shift(
            "weekend-project",
            "2026-07-11T08:00:00Z",
            8,
            source = CompensationSource.PROJECT,
            projectId = "p1",
        )
        val report = MonthlyReportBuilder.buildMonthlyReport(2026, 7, listOf(weekendProject), settings)

        assertEquals(0, report.weekendMinutes)
        assertEquals(0, report.totalMinutes)
        assertNull(PayrollCalculator.calculateShiftPay(weekendProject, settings))
    }

    @Test
    fun `the weekly breakdown reports no project pay or overtime`() {
        val employeeOnly = WeeklyBreakdownBuilder.groupByWeek(
            employeeShifts(),
            settings = settings,
            zoneOverride = zone,
        )
        val mixed = WeeklyBreakdownBuilder.groupByWeek(
            employeeShifts() + projectShifts(),
            settings = settings,
            zoneOverride = zone,
        )

        assertEquals(employeeOnly.map { it.totalMinutes }, mixed.map { it.totalMinutes })
        assertEquals(employeeOnly.map { it.overtimeMinutes }, mixed.map { it.overtimeMinutes })
        assertEquals(employeeOnly.map { it.pay }, mixed.map { it.pay })
    }

    @Test
    fun `previous-month project hours do not distort the weekly delta`() {
        val weeks = WeeklyBreakdownBuilder.groupByWeek(
            employeeShifts(),
            settings = settings,
            prevMonthShifts = projectShifts(),
            zoneOverride = zone,
        )
        assertTrue(weeks.all { it.prevMonthMinutes == 0 })
    }

    @Test
    fun `task reports do not attribute project hours to a task`() {
        // Both sets carry the same task, so a leak would show up as extra hours
        // and extra pay against that task.
        val employeeOnly = TaskMonthlyReportBuilder.build(employeeShifts(), settings)
        val mixed = TaskMonthlyReportBuilder.build(employeeShifts() + projectShifts(), settings)

        assertEquals(employeeOnly.map { it.totalMinutes }, mixed.map { it.totalMinutes })
        assertEquals(employeeOnly.map { it.overtimeMinutes }, mixed.map { it.overtimeMinutes })
        assertEquals(employeeOnly.map { it.totalPay }, mixed.map { it.totalPay })
        assertEquals(employeeOnly.map { it.shiftCount }, mixed.map { it.shiftCount })
    }

    @Test
    fun `report insights count no project shift`() {
        val employeeOnly = ReportInsightsBuilder.build(employeeShifts(), settings)
        val mixed = ReportInsightsBuilder.build(employeeShifts() + projectShifts(), settings)

        assertNotNull(employeeOnly)
        assertEquals(employeeOnly!!.averageShiftMinutes, mixed!!.averageShiftMinutes)
        assertEquals(employeeOnly.longestShiftMinutes, mixed.longestShiftMinutes)
        assertEquals(employeeOnly.overtimeShiftCount, mixed.overtimeShiftCount)
        assertEquals(employeeOnly.weekendShiftCount, mixed.weekendShiftCount)
        assertEquals(employeeOnly.avgPayPerShift, mixed.avgPayPerShift)
    }

    @Test
    fun `a month of only project time produces no report and no pay`() {
        val report = MonthlyReportBuilder.buildMonthlyReport(2026, 7, projectShifts(), settings)
        assertEquals(0, report.totalMinutes)
        assertEquals(0, report.shiftCount)
        assertEquals(0.0, PayrollCalculator.sumMonthlyPay(projectShifts(), settings).totalGross, 0.0)
        assertNull(ReportInsightsBuilder.build(projectShifts(), settings))
    }

    @Test
    fun `a project shift with a task rate still earns nothing`() {
        // CompensationResolver substitutes a task rate for the base pay rate, so
        // this is the path a project fee could sneak into wages through.
        val withTaskRate = projectShifts().first().copy(taskHourlyRateSnapshot = 500.0)
        assertNull(PayrollCalculator.calculateShiftPay(withTaskRate, settings))
        assertEquals(
            0.0,
            PayrollCalculator.sumMonthlyPay(listOf(withTaskRate), settings).totalGross,
            0.0,
        )
    }

    @Test
    fun `a seventh consecutive workday is counted from employee shifts only`() {
        // Six employee days plus a project day should not read as seven worked
        // days, which in the Israeli rules attracts a rest-day premium.
        val sixEmployee = (0 until 6).map { day ->
            shift("emp-$day", "2026-07-06T06:00:00Z", 8).let {
                it.copy(
                    startTime = it.startTime.plus(day.toLong(), ChronoUnit.DAYS),
                    endTime = it.endTime!!.plus(day.toLong(), ChronoUnit.DAYS),
                )
            }
        }
        val projectSeventh = shift(
            "proj-7",
            "2026-07-12T06:00:00Z",
            8,
            source = CompensationSource.PROJECT,
            projectId = "p1",
        )

        val withoutProject = PayrollCalculator.sumMonthlyPay(sixEmployee, settings)
        val withProject = PayrollCalculator.sumMonthlyPay(sixEmployee + projectSeventh, settings)

        assertEquals(withoutProject.totalGross, withProject.totalGross, 0.0)
    }
}

package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.model.CompensationSnapshot
import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Converting a completed shift between wages and project time.
 *
 * The invariant under test: an hour is compensated by exactly one side. After any
 * conversion the shift must produce a wage or project hours, never both and never
 * neither.
 */
class ShiftCompensationReassignmentTest {

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

    private val project = Project(
        id = "p1",
        userId = "u1",
        name = "Website rebuild",
        workStatus = ProjectWorkStatus.ACTIVE,
        fee = ProjectFee.from(BigDecimal("10000"), "USD", TaxMode.EXCLUSIVE, BigDecimal("18")),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun employeeShift() = Shift(
        id = "s1",
        userId = "u1",
        startTime = Instant.parse("2026-07-06T06:00:00Z"),
        endTime = Instant.parse("2026-07-06T14:00:00Z"),
        taskId = "task-1",
        taskNameSnapshot = "Delivery",
        taskHourlyRateSnapshot = 70.0,
        compensationSnapshot = CompensationSnapshot(
            profileId = "profile-1",
            profileName = "Default",
            regionCode = RegionCode.IL,
            currencyCode = "ILS",
            timezone = "Asia/Jerusalem",
            baseHourlyRate = 70.0,
            rules = RegionPresets.forRegion(RegionCode.IL).rules,
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
            calculatedAt = Instant.EPOCH,
        ),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `converting to project time clears the wage snapshot and attaches the project`() {
        val converted = ShiftCompensationReassignment.toProjectTime(employeeShift(), project)

        assertEquals(CompensationSource.PROJECT, converted.compensationSource)
        assertEquals("p1", converted.projectId)
        assertEquals("Website rebuild", converted.projectNameSnapshot)
        // No wage snapshot left behind: nothing will read it again, and keeping it
        // would preserve numbers that no longer describe this shift.
        assertNull(converted.compensationSnapshot)
    }

    @Test
    fun `converting to project time stops the shift earning a wage`() {
        val converted = ShiftCompensationReassignment.toProjectTime(employeeShift(), project)

        assertNotNull(PayrollCalculator.calculateShiftPay(employeeShift(), settings))
        assertNull(PayrollCalculator.calculateShiftPay(converted, settings))
    }

    @Test
    fun `converting back to hourly drops the project link and earns a wage again`() {
        val projectShift = ShiftCompensationReassignment.toProjectTime(employeeShift(), project)
        val back = ShiftCompensationReassignment.toEmployeePaid(projectShift)

        assertEquals(CompensationSource.EMPLOYEE, back.compensationSource)
        assertNull(back.projectId)
        assertNull(back.projectNameSnapshot)
        assertNull(back.compensationSnapshot)
        assertNotNull(PayrollCalculator.calculateShiftPay(back, settings))
    }

    @Test
    fun `a shift never counts on both sides at once`() {
        val projectShift = ShiftCompensationReassignment.toProjectTime(employeeShift(), project)
        // Project side: contributes hours.
        assertEquals(480, ProjectMetrics.timeSummary(listOf(projectShift), "p1").trackedMinutes)
        // Employee side: contributes nothing.
        assertEquals(0.0, PayrollCalculator.sumMonthlyPay(listOf(projectShift), settings).totalGross, 0.0)

        val back = ShiftCompensationReassignment.toEmployeePaid(projectShift)
        assertEquals(0, ProjectMetrics.timeSummary(listOf(back), "p1").trackedMinutes)
        assertTrue(PayrollCalculator.sumMonthlyPay(listOf(back), settings).totalGross > 0.0)
    }

    @Test
    fun `the task link survives a conversion in both directions`() {
        // A task says what was worked on, which stays true whoever pays for it.
        val projectShift = ShiftCompensationReassignment.toProjectTime(employeeShift(), project)
        assertEquals("task-1", projectShift.taskId)
        assertEquals("Delivery", projectShift.taskNameSnapshot)

        val back = ShiftCompensationReassignment.toEmployeePaid(projectShift)
        assertEquals("task-1", back.taskId)
    }

    @Test
    fun `asking for project time without a project falls back to hourly work`() {
        val result = ShiftCompensationReassignment.apply(
            employeeShift(),
            CompensationSource.PROJECT,
            project = null,
        )
        assertEquals(CompensationSource.EMPLOYEE, result.compensationSource)
        assertNull(result.projectId)
    }

    @Test
    fun `an already employee-paid shift with no project link is returned untouched`() {
        val shift = employeeShift()
        val result = ShiftCompensationReassignment.toEmployeePaid(shift)
        // Same instance semantics: the wage snapshot is not thrown away for an
        // edit that changed nothing about compensation.
        assertEquals(shift, result)
        assertNotNull(result.compensationSnapshot)
    }

    @Test
    fun `a stale project link on an employee shift is cleaned up`() {
        val inconsistent = employeeShift().copy(projectId = "p1", projectNameSnapshot = "Website rebuild")
        val result = ShiftCompensationReassignment.toEmployeePaid(inconsistent)
        assertNull(result.projectId)
        assertNull(result.projectNameSnapshot)
    }

    @Test
    fun `changesCompensationContext detects only a change of side`() {
        val employee = employeeShift()
        assertTrue(
            ShiftCompensationReassignment.changesCompensationContext(employee, CompensationSource.PROJECT),
        )
        assertEquals(
            false,
            ShiftCompensationReassignment.changesCompensationContext(employee, CompensationSource.EMPLOYEE),
        )
    }
}

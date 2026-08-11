package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.AbsenceEvent
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.domain.model.LeavePolicy
import com.elmtrackr.app.domain.model.LeavePolicyRules
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.SickPayBasis
import com.elmtrackr.app.domain.model.VacationPayBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LeaveCalculatorTest {

    // 480 gross over 3 complete months at 20 worked days each: 24_000 / 60 = 400
    // per worked day, and 24_000 / 90 = 266.67 on the calendar-day basis.
    private val history = LeaveEarningsHistory(
        listOf(
            LeaveEarningsMonth(YearMonth.of(2026, 5), 8_000.0, 20, 20 * 480),
            LeaveEarningsMonth(YearMonth.of(2026, 6), 8_000.0, 20, 20 * 480),
            LeaveEarningsMonth(YearMonth.of(2026, 7), 8_000.0, 20, 20 * 480),
        ),
    )

    private fun policy(rules: LeavePolicyRules) = LeavePolicy(
        id = "policy-1",
        userId = "u1",
        workplaceId = "wp-a",
        regionCode = RegionCode.IL,
        rules = rules,
    )

    private fun sickEvent(start: String, end: String) = AbsenceEvent(
        id = "event-1",
        userId = "u1",
        type = AbsenceType.SICK,
        startDate = LocalDate.parse(start),
        endDate = LocalDate.parse(end),
    )

    private fun vacationEvent(start: String, end: String = start) = AbsenceEvent(
        id = "event-2",
        userId = "u1",
        type = AbsenceType.VACATION,
        startDate = LocalDate.parse(start),
        endDate = LocalDate.parse(end),
    )

    private fun request(
        event: AbsenceEvent,
        date: String,
        rules: LeavePolicyRules = LeavePresets.forRegion(RegionCode.IL),
        workplaceId: String = "wp-a",
        units: Double = 1.0,
        unit: LeaveBalanceUnit = LeaveBalanceUnit.DAYS,
        expectedWorkMinutes: Int? = null,
        hourlyRate: Double? = 50.0,
        history: LeaveEarningsHistory = this.history,
        manualDailyAmount: Double? = null,
    ) = LeaveEstimateRequest(
        event = event,
        workplaceId = workplaceId,
        policy = policy(rules),
        affectedDate = LocalDate.parse(date),
        entitlementUnits = units,
        unit = unit,
        expectedWorkMinutes = expectedWorkMinutes,
        hourlyRate = hourlyRate,
        currencyCode = "ILS",
        history = history,
        manualDailyAmount = manualDailyAmount,
    )

    private fun ready(estimate: LeaveEstimate) = (estimate as LeaveEstimate.Ready).snapshot
    private fun gap(estimate: LeaveEstimate) = (estimate as LeaveEstimate.NeedsInput).gap

    // ── Sick day ordinals across several employers ────────────────────────────

    @Test
    fun `the sick day ordinal is counted from the illness, not restarted per workplace`() {
        // One illness, 10 to 13 August. The user works at A on the 10th and 13th,
        // and at B on the 12th. B's first affected day is still illness day 3.
        val event = sickEvent("2026-08-10", "2026-08-13")

        val a10 = ready(LeaveCalculator.estimate(request(event, "2026-08-10", workplaceId = "wp-a")))
        val b12 = ready(LeaveCalculator.estimate(request(event, "2026-08-12", workplaceId = "wp-b")))
        val a13 = ready(LeaveCalculator.estimate(request(event, "2026-08-13", workplaceId = "wp-a")))

        assertEquals(1, a10.sickDayOrdinal)
        assertEquals(3, b12.sickDayOrdinal)
        assertEquals(4, a13.sickDayOrdinal)
    }

    @Test
    fun `each workplace applies the ordinal to its own ladder`() {
        val event = sickEvent("2026-08-10", "2026-08-13")
        val generousRules = LeavePresets.forRegion(RegionCode.IL).let { rules ->
            rules.copy(sick = rules.sick.copy(payTiers = LeavePresets.fullPayFromDayOneTiers))
        }

        val standard = ready(LeaveCalculator.estimate(request(event, "2026-08-10")))
        val generous = ready(
            LeaveCalculator.estimate(request(event, "2026-08-10", rules = generousRules, workplaceId = "wp-b")),
        )

        assertEquals(1, standard.sickDayOrdinal)
        assertEquals(0.0, standard.multiplier, 0.0)
        assertEquals(0.0, standard.estimatedGrossPay, 0.0)

        assertEquals(1, generous.sickDayOrdinal)
        assertEquals(1.0, generous.multiplier, 0.0)
        assertEquals(400.0, generous.estimatedGrossPay, 0.0001)
    }

    @Test
    fun `the ordinal counts calendar days, including days not worked anywhere`() {
        val event = sickEvent("2026-08-10", "2026-08-20")

        val snapshot = ready(LeaveCalculator.estimate(request(event, "2026-08-17")))

        assertEquals(8, snapshot.sickDayOrdinal)
    }

    // ── Sick pay ──────────────────────────────────────────────────────────────

    @Test
    fun `sick pay uses the average worked day and the tier multiplier`() {
        val event = sickEvent("2026-08-10", "2026-08-13")

        val day2 = ready(LeaveCalculator.estimate(request(event, "2026-08-11")))

        assertEquals(400.0, day2.baseAmount, 0.0001)
        assertEquals(0.5, day2.multiplier, 0.0)
        assertEquals(200.0, day2.estimatedGrossPay, 0.0001)
        assertEquals(SickPayBasis.HISTORICAL_AVERAGE.persistedValue, day2.payBasis)
    }

    @Test
    fun `a half sick day pays half of the tier amount`() {
        val event = sickEvent("2026-08-10", "2026-08-13")

        val snapshot = ready(LeaveCalculator.estimate(request(event, "2026-08-13", units = 0.5)))

        assertEquals(200.0, snapshot.baseAmount, 0.0001)
        assertEquals(200.0, snapshot.estimatedGrossPay, 0.0001)
        assertEquals(0.5, snapshot.balanceUnitsUsed, 0.0)
    }

    @Test
    fun `leave reported in hours is paid at the hourly rate`() {
        val event = vacationEvent("2026-08-12")

        val snapshot = ready(
            LeaveCalculator.estimate(
                request(event, "2026-08-12", units = 4.0, unit = LeaveBalanceUnit.HOURS),
            ),
        )

        assertEquals(200.0, snapshot.estimatedGrossPay, 0.0001)
        assertEquals(LeaveBalanceUnit.HOURS, snapshot.balanceUnit)
        assertEquals(4.0, snapshot.balanceUnitsUsed, 0.0)
    }

    @Test
    fun `a workplace with sick leave switched off says so instead of paying zero`() {
        val rules = LeavePresets.forRegion(RegionCode.IL).let { r ->
            r.copy(sick = r.sick.copy(enabled = false))
        }

        val estimate = LeaveCalculator.estimate(
            request(sickEvent("2026-08-10", "2026-08-10"), "2026-08-10", rules = rules),
        )

        assertEquals(LeaveEstimateGap.LEAVE_TYPE_DISABLED, gap(estimate))
    }

    @Test
    fun `a ladder that does not cover the day asks for the policy to be fixed`() {
        val rules = LeavePresets.forRegion(RegionCode.IL).let { r ->
            r.copy(sick = r.sick.copy(payTiers = emptyList()))
        }

        val estimate = LeaveCalculator.estimate(
            request(sickEvent("2026-08-10", "2026-08-10"), "2026-08-10", rules = rules),
        )

        assertEquals(LeaveEstimateGap.NO_MATCHING_TIER, gap(estimate))
    }

    // ── Vacation pay ──────────────────────────────────────────────────────────

    @Test
    fun `vacation on the statutory basis divides the three month gross by 90`() {
        val estimate = LeaveCalculator.estimate(request(vacationEvent("2026-08-12"), "2026-08-12"))

        val snapshot = ready(estimate)
        assertEquals(24_000.0 / 90.0, snapshot.estimatedGrossPay, 0.0001)
        assertEquals(1.0, snapshot.multiplier, 0.0)
        assertNull("vacation has no illness ordinal", snapshot.sickDayOrdinal)
        assertEquals(VacationPayBasis.ISRAEL_STATUTORY_AVERAGE_90.persistedValue, snapshot.payBasis)
    }

    @Test
    fun `the averaging period travels with the estimate`() {
        val snapshot = ready(LeaveCalculator.estimate(request(vacationEvent("2026-08-12"), "2026-08-12")))

        assertEquals(LocalDate.of(2026, 5, 1), snapshot.averagePeriodStart)
        assertEquals(LocalDate.of(2026, 7, 31), snapshot.averagePeriodEnd)
        assertEquals(24_000.0, snapshot.averageGrossIncluded)
        assertEquals(90.0, snapshot.averageDivisor)
        assertFalse(snapshot.usedFallbackAveragePeriod)
    }

    @Test
    fun `too little pay history asks for a value rather than estimating zero`() {
        val estimate = LeaveCalculator.estimate(
            request(vacationEvent("2026-08-12"), "2026-08-12", history = LeaveEarningsHistory.EMPTY),
        )

        assertEquals(LeaveEstimateGap.NO_PAY_HISTORY, gap(estimate))
    }

    @Test
    fun `a manual amount is used and recorded as an override`() {
        val rules = LeavePresets.forRegion(RegionCode.IL).let { r ->
            r.copy(vacation = r.vacation.copy(payBasis = VacationPayBasis.MANUAL))
        }

        val snapshot = ready(
            LeaveCalculator.estimate(
                request(
                    vacationEvent("2026-08-12"),
                    "2026-08-12",
                    rules = rules,
                    history = LeaveEarningsHistory.EMPTY,
                    manualDailyAmount = 375.0,
                ),
            ),
        )

        assertEquals(375.0, snapshot.estimatedGrossPay, 0.0001)
        assertTrue(snapshot.manualOverride?.enabled == true)
    }

    @Test
    fun `a manual basis with no amount entered asks for one`() {
        val rules = LeavePresets.forRegion(RegionCode.IL).let { r ->
            r.copy(vacation = r.vacation.copy(payBasis = VacationPayBasis.MANUAL))
        }

        val estimate = LeaveCalculator.estimate(
            request(vacationEvent("2026-08-12"), "2026-08-12", rules = rules),
        )

        assertEquals(LeaveEstimateGap.NO_MANUAL_AMOUNT, gap(estimate))
    }

    @Test
    fun `the scheduled-hours basis needs the hours the user would have worked`() {
        val rules = LeavePresets.forRegion(RegionCode.IL).let { r ->
            r.copy(vacation = r.vacation.copy(payBasis = VacationPayBasis.SCHEDULED_HOURS))
        }

        val missing = LeaveCalculator.estimate(
            request(vacationEvent("2026-08-12"), "2026-08-12", rules = rules),
        )
        val supplied = LeaveCalculator.estimate(
            request(vacationEvent("2026-08-12"), "2026-08-12", rules = rules, expectedWorkMinutes = 390),
        )

        assertEquals(LeaveEstimateGap.NO_EXPECTED_HOURS, gap(missing))
        assertEquals(325.0, ready(supplied).estimatedGrossPay, 0.0001)
    }

    @Test
    fun `the fixed-day basis needs to know how long a standard day is`() {
        val base = LeavePresets.forRegion(RegionCode.IL)
        val withoutStandard = base.copy(
            vacation = base.vacation.copy(payBasis = VacationPayBasis.FIXED_DAILY_HOURS),
            standardDayMinutes = null,
        )
        val withStandard = withoutStandard.copy(standardDayMinutes = 480)

        assertEquals(
            LeaveEstimateGap.NO_STANDARD_DAY,
            gap(LeaveCalculator.estimate(request(vacationEvent("2026-08-12"), "2026-08-12", rules = withoutStandard))),
        )
        assertEquals(
            400.0,
            ready(
                LeaveCalculator.estimate(
                    request(vacationEvent("2026-08-12"), "2026-08-12", rules = withStandard),
                ),
            ).estimatedGrossPay,
            0.0001,
        )
    }

    @Test
    fun `a workplace with no hourly rate asks for one on an hours basis`() {
        val rules = LeavePresets.forRegion(RegionCode.IL).let { r ->
            r.copy(vacation = r.vacation.copy(payBasis = VacationPayBasis.SCHEDULED_HOURS))
        }

        val estimate = LeaveCalculator.estimate(
            request(
                vacationEvent("2026-08-12"),
                "2026-08-12",
                rules = rules,
                expectedWorkMinutes = 480,
                hourlyRate = null,
            ),
        )

        assertEquals(LeaveEstimateGap.NO_WAGE, gap(estimate))
    }

    @Test
    fun `a substituted averaging period is flagged on the estimate`() {
        val patchy = LeaveEarningsHistory(
            listOf(
                LeaveEarningsMonth(YearMonth.of(2026, 1), 12_000.0, 20, 20 * 480),
                LeaveEarningsMonth(YearMonth.of(2026, 2), 12_000.0, 20, 20 * 480),
                LeaveEarningsMonth(YearMonth.of(2026, 3), 12_000.0, 20, 20 * 480),
                LeaveEarningsMonth(YearMonth.of(2026, 7), 0.0, 0, 0),
            ),
        )

        val snapshot = ready(
            LeaveCalculator.estimate(request(vacationEvent("2026-08-12"), "2026-08-12", history = patchy)),
        )

        assertTrue(snapshot.usedFallbackAveragePeriod)
        assertEquals(LocalDate.of(2026, 1, 1), snapshot.averagePeriodStart)
    }

    @Test
    fun `the snapshot records the workplace and currency it was calculated for`() {
        val snapshot = ready(
            LeaveCalculator.estimate(request(vacationEvent("2026-08-12"), "2026-08-12", workplaceId = "wp-b")),
        )

        assertEquals("wp-b", snapshot.workplaceId)
        assertEquals("ILS", snapshot.currencyCode)
        assertEquals(AbsenceType.VACATION, snapshot.absenceType)
    }
}

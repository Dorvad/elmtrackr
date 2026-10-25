package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.AbsenceEvent
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.domain.model.LeaveCalculationSnapshot
import com.elmtrackr.app.domain.model.LeaveManualMode
import com.elmtrackr.app.domain.model.LeaveManualOverride
import com.elmtrackr.app.domain.model.LeavePolicy
import com.elmtrackr.app.domain.model.LeavePolicyRules
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.VacationPayBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * What a typed leave amount means, and that it is used.
 *
 * The form accepted an amount for any day the engine could not value, but the
 * engine read it only on a MANUAL pay basis: the Israeli default (a 90-day average)
 * with too little history kept answering "needs a value" while the amount the user
 * had entered sat unused — and the saved snapshot claimed a manual override anyway.
 */
class LeaveManualAmountTest {

    private val history = LeaveEarningsHistory(
        listOf(
            LeaveEarningsMonth(YearMonth.of(2026, 5), 8_000.0, 20, 20 * 480),
            LeaveEarningsMonth(YearMonth.of(2026, 6), 8_000.0, 20, 20 * 480),
            LeaveEarningsMonth(YearMonth.of(2026, 7), 8_000.0, 20, 20 * 480),
        ),
    )

    /** The Israeli preset: 90-day vacation average; sick days at 0%, 50%, 50%, then 100%. */
    private val israeli = LeavePresets.forRegion(RegionCode.IL)

    private fun policy(rules: LeavePolicyRules) = LeavePolicy(
        id = "policy-1",
        userId = "u1",
        workplaceId = "wp-a",
        regionCode = RegionCode.IL,
        rules = rules,
    )

    private val sick = AbsenceEvent(
        id = "e-sick",
        userId = "u1",
        type = AbsenceType.SICK,
        startDate = LocalDate.parse("2026-08-10"),
        endDate = LocalDate.parse("2026-08-14"),
    )

    private val vacation = AbsenceEvent(
        id = "e-vac",
        userId = "u1",
        type = AbsenceType.VACATION,
        startDate = LocalDate.parse("2026-08-10"),
        endDate = LocalDate.parse("2026-08-12"),
    )

    private fun request(
        event: AbsenceEvent,
        date: String = "2026-08-10",
        rules: LeavePolicyRules = israeli,
        units: Double = 1.0,
        unit: LeaveBalanceUnit = LeaveBalanceUnit.DAYS,
        history: LeaveEarningsHistory = this.history,
        hourlyRate: Double? = 50.0,
        expectedWorkMinutes: Int? = null,
        amount: Double? = null,
        mode: LeaveManualMode? = null,
    ) = LeaveEstimateRequest(
        event = event,
        workplaceId = "wp-a",
        policy = policy(rules),
        affectedDate = LocalDate.parse(date),
        entitlementUnits = units,
        unit = unit,
        expectedWorkMinutes = expectedWorkMinutes,
        hourlyRate = hourlyRate,
        currencyCode = "ILS",
        history = history,
        manualAmount = amount,
        manualMode = mode,
    )

    private fun ready(estimate: LeaveEstimate) = (estimate as LeaveEstimate.Ready).snapshot
    private fun gap(estimate: LeaveEstimate) = (estimate as LeaveEstimate.NeedsInput).gap

    // ── Automatic ─────────────────────────────────────────────────────────────

    @Test
    fun `with enough history the automatic estimate stands and records no override`() {
        val snapshot = ready(LeaveCalculator.estimate(request(vacation)))

        // 24,000 over 90 days.
        assertEquals(266.67, snapshot.estimatedGrossPay, 0.01)
        assertEquals(VacationPayBasis.ISRAEL_STATUTORY_AVERAGE_90.persistedValue, snapshot.payBasis)
        assertNull(snapshot.manualOverride)
    }

    @Test
    fun `with too little history the automatic estimate asks for a value`() {
        val estimate = LeaveCalculator.estimate(request(vacation, history = LeaveEarningsHistory.EMPTY))

        assertEquals(LeaveEstimateGap.NO_PAY_HISTORY, gap(estimate))
    }

    // ── A daily amount ────────────────────────────────────────────────────────

    @Test
    fun `a daily amount fills a gap the policy's basis could not`() {
        val snapshot = ready(
            LeaveCalculator.estimate(
                request(vacation, history = LeaveEarningsHistory.EMPTY, amount = 400.0),
            ),
        )

        assertEquals(400.0, snapshot.estimatedGrossPay, 0.0)
        assertEquals(400.0, snapshot.baseAmount, 0.0)
        assertEquals("manual", snapshot.payBasis)
        assertEquals(LeaveManualOverride(true, null, LeaveManualMode.BASE_DAILY, 400.0), snapshot.manualOverride)
    }

    @Test
    fun `a daily amount replaces an automatic estimate the user chose not to use`() {
        val snapshot = ready(
            LeaveCalculator.estimate(request(vacation, amount = 500.0, mode = LeaveManualMode.BASE_DAILY)),
        )

        assertEquals(500.0, snapshot.estimatedGrossPay, 0.0)
        assertNull("no average was used", snapshot.averageDivisor)
    }

    @Test
    fun `a sick day applies its percentage to the daily amount`() {
        val day1 = ready(LeaveCalculator.estimate(request(sick, "2026-08-10", amount = 400.0)))
        val day2 = ready(LeaveCalculator.estimate(request(sick, "2026-08-11", amount = 400.0)))
        val day4 = ready(LeaveCalculator.estimate(request(sick, "2026-08-13", amount = 400.0)))

        assertEquals(0.0, day1.estimatedGrossPay, 0.0)
        assertEquals(200.0, day2.estimatedGrossPay, 0.0)
        assertEquals(0.5, day2.multiplier, 0.0)
        assertEquals(400.0, day2.baseAmount, 0.0)
        assertEquals(400.0, day4.estimatedGrossPay, 0.0)
    }

    @Test
    fun `a daily amount with no rung on the ladder still needs one`() {
        val noLadder = israeli.copy(sick = israeli.sick.copy(payTiers = emptyList()))
        val estimate = LeaveCalculator.estimate(request(sick, rules = noLadder, amount = 400.0))

        // No percentage is guessed; the user can enter a final amount instead.
        assertEquals(LeaveEstimateGap.NO_MATCHING_TIER, gap(estimate))
    }

    // ── A final amount ────────────────────────────────────────────────────────

    @Test
    fun `a final amount is used exactly as entered, with no percentage`() {
        val snapshot = ready(
            LeaveCalculator.estimate(
                request(sick, "2026-08-11", amount = 333.0, mode = LeaveManualMode.FINAL_PAYABLE),
            ),
        )

        assertEquals(333.0, snapshot.estimatedGrossPay, 0.0)
        assertEquals(1.0, snapshot.multiplier, 0.0)
        assertEquals(2, snapshot.sickDayOrdinal)
        assertEquals(LeaveManualMode.FINAL_PAYABLE, snapshot.manualOverride?.mode)
        assertEquals(snapshot.estimatedGrossPay, snapshot.baseAmount * snapshot.multiplier, 0.0)
    }

    @Test
    fun `a final amount needs no rung and no history`() {
        val noLadder = israeli.copy(sick = israeli.sick.copy(payTiers = emptyList()))
        val snapshot = ready(
            LeaveCalculator.estimate(
                request(
                    sick,
                    rules = noLadder,
                    history = LeaveEarningsHistory.EMPTY,
                    amount = 120.0,
                    mode = LeaveManualMode.FINAL_PAYABLE,
                ),
            ),
        )

        assertEquals(120.0, snapshot.estimatedGrossPay, 0.0)
    }

    // ── Part days ─────────────────────────────────────────────────────────────

    @Test
    fun `half a day of a daily amount is half of it`() {
        val snapshot = ready(LeaveCalculator.estimate(request(vacation, units = 0.5, amount = 400.0)))

        assertEquals(200.0, snapshot.estimatedGrossPay, 0.0)
        assertEquals(0.5, snapshot.balanceUnitsUsed, 0.0)
    }

    @Test
    fun `hours of a daily amount are a share of the standard day, not an hourly rate`() {
        val eightHourDay = israeli.copy(standardDayMinutes = 480)
        val snapshot = ready(
            LeaveCalculator.estimate(
                request(vacation, rules = eightHourDay, units = 4.0, unit = LeaveBalanceUnit.HOURS, amount = 400.0),
            ),
        )

        // 4 of 8 hours of a 400 day — not 4 × the 50/h wage.
        assertEquals(200.0, snapshot.estimatedGrossPay, 0.0)
    }

    @Test
    fun `hours of a daily amount use the expected hours before the standard day`() {
        val snapshot = ready(
            LeaveCalculator.estimate(
                request(
                    vacation,
                    rules = israeli.copy(standardDayMinutes = 480),
                    units = 3.0,
                    unit = LeaveBalanceUnit.HOURS,
                    expectedWorkMinutes = 360,
                    amount = 300.0,
                ),
            ),
        )

        assertEquals(150.0, snapshot.estimatedGrossPay, 0.0)
    }

    @Test
    fun `hours of a daily amount with no known day length ask for one`() {
        val estimate = LeaveCalculator.estimate(
            request(vacation, units = 4.0, unit = LeaveBalanceUnit.HOURS, amount = 400.0),
        )

        assertEquals(LeaveEstimateGap.NO_STANDARD_DAY, gap(estimate))
    }

    @Test
    fun `a final amount is not scaled by a part day`() {
        val snapshot = ready(
            LeaveCalculator.estimate(
                request(vacation, units = 0.5, amount = 180.0, mode = LeaveManualMode.FINAL_PAYABLE),
            ),
        )

        assertEquals(180.0, snapshot.estimatedGrossPay, 0.0)
        assertEquals(0.5, snapshot.balanceUnitsUsed, 0.0)
    }

    @Test
    fun `automatic hours are still priced at the wage`() {
        val snapshot = ready(
            LeaveCalculator.estimate(request(vacation, units = 4.0, unit = LeaveBalanceUnit.HOURS)),
        )

        assertEquals(200.0, snapshot.estimatedGrossPay, 0.0)
        assertNull(snapshot.manualOverride)
    }

    // ── Edges ─────────────────────────────────────────────────────────────────

    @Test
    fun `an amount for a leave type the policy switches off is still the user's to give`() {
        val noVacation = israeli.copy(vacation = israeli.vacation.copy(enabled = false))

        assertEquals(
            LeaveEstimateGap.LEAVE_TYPE_DISABLED,
            gap(LeaveCalculator.estimate(request(vacation, rules = noVacation))),
        )
        assertEquals(
            250.0,
            ready(LeaveCalculator.estimate(request(vacation, rules = noVacation, amount = 250.0))).estimatedGrossPay,
            0.0,
        )
    }

    @Test
    fun `a negative or non-finite amount is no amount`() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { bad ->
            val estimate = LeaveCalculator.estimate(
                request(vacation, history = LeaveEarningsHistory.EMPTY, amount = bad),
            )
            assertEquals("amount $bad", LeaveEstimateGap.NO_PAY_HISTORY, gap(estimate))
        }
    }

    @Test
    fun `zero is a real amount`() {
        val snapshot = ready(
            LeaveCalculator.estimate(request(vacation, history = LeaveEarningsHistory.EMPTY, amount = 0.0)),
        )

        assertEquals(0.0, snapshot.estimatedGrossPay, 0.0)
        assertEquals(0.0, snapshot.manualOverride?.amount)
    }

    @Test
    fun `the policy itself is not touched by a one-day override`() {
        val request = request(vacation, amount = 999.0, mode = LeaveManualMode.FINAL_PAYABLE)
        val before = request.policy

        LeaveCalculator.estimate(request)

        assertEquals(before, request.policy)
        assertEquals(VacationPayBasis.ISRAEL_STATUTORY_AVERAGE_90, request.policy.rules.vacation.payBasis)
    }

    // ── Stored form ───────────────────────────────────────────────────────────

    private fun snapshot(override: LeaveManualOverride?) = LeaveCalculationSnapshot(
        calculatedAt = Instant.parse("2026-08-10T10:00:00Z"),
        absenceType = AbsenceType.SICK,
        workplaceId = "wp-a",
        payBasis = "manual",
        currencyCode = "ILS",
        sickDayOrdinal = 2,
        multiplier = 0.5,
        baseAmount = 400.0,
        estimatedGrossPay = 200.0,
        manualOverride = override,
    )

    @Test
    fun `the mode and the amount survive the stored JSON`() {
        listOf(LeaveManualMode.BASE_DAILY, LeaveManualMode.FINAL_PAYABLE).forEach { mode ->
            val original = snapshot(LeaveManualOverride(true, "payslip", mode, 400.0))
            val decoded = LeavePolicyCodec.decodeCalculationSnapshot(LeavePolicyCodec.encodeCalculationSnapshot(original))
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `a snapshot written before the mode was stored still reads`() {
        val legacy = """
            {"calculated_at":"2026-08-10T10:00:00Z","absence_type":"sick","workplace_id":"wp-a",
             "pay_basis":"manual","currency_code":"ILS","multiplier":0.5,"base_amount":400,
             "estimated_gross_pay":200,"balance_units_used":1,"balance_unit":"days",
             "manual_override":{"enabled":true,"reason":null}}
        """.trimIndent()

        val decoded = LeavePolicyCodec.decodeCalculationSnapshot(legacy)!!

        assertEquals(LeaveManualOverride(enabled = true), decoded.manualOverride)
        assertEquals(200.0, decoded.estimatedGrossPay, 0.0)
    }

    @Test
    fun `an unknown mode from a newer build reads as unknown, not as a guess`() {
        val json = LeavePolicyCodec.encodeCalculationSnapshot(
            snapshot(LeaveManualOverride(true, null, LeaveManualMode.BASE_DAILY, 400.0)),
        ).replace("\"base_daily\"", "\"weekly_average\"")

        val decoded = LeavePolicyCodec.decodeCalculationSnapshot(json)!!

        assertNull(decoded.manualOverride?.mode)
        assertEquals(400.0, decoded.manualOverride?.amount)
        assertTrue(decoded.manualOverride!!.enabled)
    }
}

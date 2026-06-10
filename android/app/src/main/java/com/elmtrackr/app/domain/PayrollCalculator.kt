package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.PayBracket
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.ShiftPayBreakdown
import com.elmtrackr.app.domain.model.UserSettings
import java.time.ZoneOffset

/**
 * Israeli payroll estimation.
 *
 * Weekday tiers (from shift start):
 *   100% up to daily threshold → 125% for first 2 OT hours → 150% beyond that
 *
 * Shabbat/Holiday tiers (is_special_day OR shift start falls on a weekend day):
 *   150% for first 2h → 175% for next 2h → 200% beyond 4h
 */
object PayrollCalculator {

    private data class Tier(val label: String, val capMinutes: Int, val rate: Double)

    private fun weekdayTiers(dailyThreshold: Int) = listOf(
        Tier("100% — Regular",       dailyThreshold, 1.0),
        Tier("125% — Overtime (2h)", 120,            1.25),
        Tier("150% — Extended OT",   Int.MAX_VALUE,  1.5),
    )

    private val specialTiers = listOf(
        Tier("150% — Shabbat / Holiday (2h)",     120,          1.5),
        Tier("175% — Shabbat / Holiday OT (2h)",  120,          1.75),
        Tier("200% — Shabbat / Holiday Extended",  Int.MAX_VALUE, 2.0),
    )

    /**
     * Calculate gross pay for a single completed shift.
     * Returns null when the shift is active or no hourly rate is configured.
     */
    fun calculateShiftPay(shift: Shift, settings: UserSettings): ShiftPayBreakdown? {
        if (shift.endTime == null) return null
        val hourlyRate = settings.hourlyRate?.takeIf { it > 0 } ?: return null
        val net = ShiftDurationCalculator.netMinutes(shift)?.takeIf { it > 0 } ?: return null

        val ratePerMinute = hourlyRate / 60.0
        val startDateStr = shift.startTime.atOffset(ZoneOffset.UTC).toLocalDate().toString()
        val startOnWeekend = WeekendRules.isWeekendDate(startDateStr, settings.weekendDays)
        val isSpecial = shift.isSpecialDay || startOnWeekend

        val tiers = if (isSpecial) specialTiers
                    else weekdayTiers(settings.dailyOvertimeThresholdMinutes)

        val brackets = mutableListOf<PayBracket>()
        var remaining = net

        for (tier in tiers) {
            if (remaining <= 0) break
            val mins = if (tier.capMinutes == Int.MAX_VALUE) remaining
                       else minOf(remaining, tier.capMinutes)
            brackets += PayBracket(
                label = tier.label,
                minutes = mins,
                rate = tier.rate,
                amount = mins * ratePerMinute * tier.rate,
            )
            remaining -= mins
        }

        return ShiftPayBreakdown(
            brackets = brackets,
            totalGross = brackets.sumOf { it.amount },
            isSpecial = isSpecial,
        )
    }

    /** Aggregate gross pay across multiple shifts. Active/unconfigured shifts are skipped. */
    fun sumMonthlyPay(shifts: List<Shift>, settings: UserSettings): MonthlyPaySummary {
        var totalGross = 0.0; var regularGross = 0.0
        var overtimeGross = 0.0; var specialGross = 0.0

        for (shift in shifts) {
            val bd = calculateShiftPay(shift, settings) ?: continue
            totalGross += bd.totalGross
            if (bd.isSpecial) {
                specialGross += bd.totalGross
            } else {
                for (b in bd.brackets) {
                    if (b.rate == 1.0) regularGross += b.amount else overtimeGross += b.amount
                }
            }
        }
        return MonthlyPaySummary(totalGross, regularGross, overtimeGross, specialGross)
    }

    data class MonthlyPaySummary(
        val totalGross: Double,
        val regularGross: Double,
        val overtimeGross: Double,
        val specialGross: Double,
    )
}

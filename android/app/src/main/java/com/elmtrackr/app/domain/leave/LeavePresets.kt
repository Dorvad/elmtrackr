package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.domain.model.LeavePolicyRules
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.SickLeavePolicy
import com.elmtrackr.app.domain.model.SickPayBasis
import com.elmtrackr.app.domain.model.SickPayTier
import com.elmtrackr.app.domain.model.VacationLeavePolicy
import com.elmtrackr.app.domain.model.VacationPayBasis

/**
 * Starting points for a workplace's leave policy, in the same spirit as
 * [com.elmtrackr.app.domain.compensation.RegionPresets]: a suggested
 * configuration the user owns and can replace, not a statement of what the law
 * requires of their employer.
 *
 * The Israeli sick ladder is the one preset here that encodes a widely used
 * sequence. It is expressed as [SickPayTier] data precisely so that a workplace
 * with a better agreement replaces it with a single 100% rung and every
 * calculation follows, with no code path that asks "is this Israel".
 */
object LeavePresets {

    /**
     * The commonly described Israeli sick pay sequence: nothing for the first day
     * of illness, half pay for the second and third, full pay from the fourth.
     *
     * Reproduced as the default because it is the arrangement most users of this
     * app will recognise on their payslip — not as a legal assertion. Anything
     * the user's own agreement says wins, which is why every rung is editable.
     */
    val israeliSickTiers: List<SickPayTier> = listOf(
        SickPayTier(fromDay = 1, toDay = 1, multiplier = 0.0),
        SickPayTier(fromDay = 2, toDay = 3, multiplier = 0.5),
        SickPayTier(fromDay = 4, toDay = null, multiplier = 1.0),
    )

    /** A workplace that pays sick leave in full from day one. */
    val fullPayFromDayOneTiers: List<SickPayTier> = listOf(
        SickPayTier(fromDay = 1, toDay = null, multiplier = 1.0),
    )

    fun forRegion(regionCode: RegionCode): LeavePolicyRules = when (regionCode) {
        RegionCode.IL -> LeavePolicyRules(
            sick = SickLeavePolicy(
                enabled = true,
                payTiers = israeliSickTiers,
                payBasis = SickPayBasis.HISTORICAL_AVERAGE,
                // Reference figures only. Accrual stays off in V1: getting it right
                // for a part-time or irregular hourly worker depends on the actual
                // work pattern, partial months, seniority and sector agreements,
                // and a confidently wrong balance is worse than no balance.
                accrualEnabled = false,
                accrualDaysPerMonth = 1.5,
                maxAccruedDays = 90.0,
            ),
            vacation = VacationLeavePolicy(
                enabled = true,
                payBasis = VacationPayBasis.ISRAEL_STATUTORY_AVERAGE_90,
                accrualEnabled = false,
            ),
            standardDayMinutes = null,
            balanceUnit = LeaveBalanceUnit.DAYS,
        )

        // No statutory ladder is encoded for the other regions, because this app
        // has no verified basis for one and inventing plausible percentages would
        // be worse than asking. A neutral, obviously-editable "paid in full from
        // day one" is the starting point, and the settings screen says so.
        else -> LeavePolicyRules(
            sick = SickLeavePolicy(
                enabled = true,
                payTiers = fullPayFromDayOneTiers,
                payBasis = SickPayBasis.HISTORICAL_AVERAGE,
                accrualEnabled = false,
            ),
            vacation = VacationLeavePolicy(
                enabled = true,
                payBasis = VacationPayBasis.ACTUAL_WORKDAYS_AVERAGE,
                accrualEnabled = false,
            ),
            standardDayMinutes = null,
            balanceUnit = LeaveBalanceUnit.DAYS,
        )
    }

    /**
     * True when the sick ladder pays less at some point in the first two weeks
     * than the region's preset would. Drives a warning, never a block: a
     * workplace really can have a worse arrangement than the preset, and the app
     * is not in a position to tell the user their employer is wrong.
     */
    fun paysLessThanPreset(regionCode: RegionCode, tiers: List<SickPayTier>): Boolean {
        val preset = forRegion(regionCode).sick.payTiers
        if (preset.isEmpty() || tiers.isEmpty()) return false
        return (1..14).any { day ->
            val configured = SickPayCalculator.resolveTier(tiers, day)?.multiplier
            val expected = SickPayCalculator.resolveTier(preset, day)?.multiplier
            configured != null && expected != null && configured < expected
        }
    }
}

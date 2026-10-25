package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.AbsenceEvent
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.domain.model.LeaveCalculationSnapshot
import com.elmtrackr.app.domain.model.LeaveManualMode
import com.elmtrackr.app.domain.model.LeaveManualOverride
import com.elmtrackr.app.domain.model.LeavePolicy
import com.elmtrackr.app.domain.model.LeavePolicySnapshot
import com.elmtrackr.app.domain.model.SickPayBasis
import com.elmtrackr.app.domain.model.VacationPayBasis
import java.time.Instant
import java.time.LocalDate

/**
 * What one absent date at one workplace is estimated to pay.
 *
 * Everything the calculation needs is passed in, including the pay history, so
 * the engine is a pure function of its input and is tested without a database, a
 * clock or a device.
 */
data class LeaveEstimateRequest(
    val event: AbsenceEvent,
    val workplaceId: String,
    val policy: LeavePolicy,
    val affectedDate: LocalDate,
    /** A whole day is 1.0, a half day 0.5. Interpreted in [unit]. */
    val entitlementUnits: Double,
    val unit: LeaveBalanceUnit,
    /** What the user says they were scheduled to work that day, when known. */
    val expectedWorkMinutes: Int? = null,
    val hourlyRate: Double?,
    val currencyCode: String,
    val history: LeaveEarningsHistory = LeaveEarningsHistory.EMPTY,
    /**
     * A value the user typed for this day, which replaces the automatic one. What
     * it means is [manualMode]; a negative or non-finite value is treated as absent.
     */
    val manualAmount: Double? = null,
    /**
     * What [manualAmount] is. Null with an amount means [LeaveManualMode.BASE_DAILY],
     * the meaning the field has always been labelled with: what a day is worth.
     */
    val manualMode: LeaveManualMode? = null,
    val manualReason: String? = null,
    val calculatedAt: Instant = Instant.EPOCH,
)

sealed interface LeaveEstimate {
    /** The estimate, with its working shown. */
    data class Ready(val snapshot: LeaveCalculationSnapshot) : LeaveEstimate

    /**
     * The app cannot value this day and says so.
     *
     * This case exists because the alternative is worse. An unexplained 0 reads
     * as "this day is worth nothing", and a user reconciling against a payslip
     * cannot tell that apart from a day that genuinely pays nothing — under the
     * Israeli default the first day of an illness really is 0. Every gap below
     * names something the user can supply.
     */
    data class NeedsInput(val gap: LeaveEstimateGap) : LeaveEstimate
}

enum class LeaveEstimateGap {
    /** The workplace has no leave policy at all yet. */
    NO_POLICY,

    /** The workplace's policy has this kind of leave switched off. */
    LEAVE_TYPE_DISABLED,

    /** No hourly rate on the workplace's pay profile. */
    NO_WAGE,

    /** Not enough complete months of pay history to average. */
    NO_PAY_HISTORY,

    /** The basis needs the hours the user was scheduled to work, and none are known. */
    NO_EXPECTED_HOURS,

    /** The policy is set to a fixed standard day but does not say how long one is. */
    NO_STANDARD_DAY,

    /** The sick ladder says nothing about this day of the illness. */
    NO_MATCHING_TIER,

    /** The basis is manual and no amount has been entered. */
    NO_MANUAL_AMOUNT,
}

/**
 * Manual amounts, as one rule for both kinds of leave.
 *
 * The form accepted an amount for any day the engine could not value, but the
 * engine read it only when the workplace's pay basis was itself MANUAL. A user who
 * typed an amount to fill a gap — no wage, too little history — saw the estimate
 * ignore it, while the saved snapshot still claimed a manual override. Now:
 *
 *  - an amount is used whenever one is given, whatever the policy's basis, because
 *    the user has said what this day is worth; the workplace's policy is untouched;
 *  - [LeaveManualMode.BASE_DAILY] replaces only the value of a full day. Part days
 *    scale from it and a sick day's percentage applies to it, as for any day;
 *  - [LeaveManualMode.FINAL_PAYABLE] is the result, used as entered;
 *  - the snapshot records the override only when it priced the day, with the mode
 *    and the amount, so the record says what was entered and what it meant.
 */
private data class ManualInput(val amount: Double, val mode: LeaveManualMode)

private fun LeaveEstimateRequest.manualInput(): ManualInput? {
    val amount = manualAmount?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    return ManualInput(amount, manualMode ?: LeaveManualMode.BASE_DAILY)
}

object LeaveCalculator {

    private const val MINUTES_PER_HOUR = 60.0

    /** The basis recorded for a day priced from the user's own amount. Same string for both leave types. */
    private val MANUAL_BASIS = VacationPayBasis.MANUAL.persistedValue

    fun buildPolicySnapshot(policy: LeavePolicy, capturedAt: Instant): LeavePolicySnapshot =
        LeavePolicySnapshot(
            policyId = policy.id.ifBlank { null },
            regionCode = policy.regionCode,
            rules = policy.rules,
            capturedAt = capturedAt,
        )

    fun estimate(request: LeaveEstimateRequest): LeaveEstimate = when (request.event.type) {
        AbsenceType.VACATION -> estimateVacation(request)
        AbsenceType.SICK -> estimateSick(request)
    }

    // ── Vacation ──────────────────────────────────────────────────────────────

    private fun estimateVacation(request: LeaveEstimateRequest): LeaveEstimate {
        val policy = request.policy.rules.vacation
        val manual = request.manualInput()
        // "Disabled" is the policy's answer for an automatic estimate. An amount the
        // user typed for this one day is their own statement of what it pays, and it
        // does not change the workplace's policy.
        if (!policy.enabled && manual == null) return LeaveEstimate.NeedsInput(LeaveEstimateGap.LEAVE_TYPE_DISABLED)
        if (manual?.mode == LeaveManualMode.FINAL_PAYABLE) return finalPayable(request, manual, ordinal = null)

        val basis = policy.payBasis
        val valued = if (manual != null) {
            DayValueResult.Value(manual.amount)
        } else {
            when (basis) {
                VacationPayBasis.ISRAEL_STATUTORY_AVERAGE_90 ->
                    averaged(VacationPayCalculator.israeliStatutoryAverage(request.history, request.affectedDate))

                VacationPayBasis.ACTUAL_WORKDAYS_AVERAGE ->
                    averaged(VacationPayCalculator.workdayAverage(request.history, request.affectedDate))

                VacationPayBasis.SCHEDULED_HOURS -> scheduledHoursValue(request)
                VacationPayBasis.FIXED_DAILY_HOURS -> fixedDailyValue(request, policy.fixedDailyMinutes)
                VacationPayBasis.MANUAL -> DayValueResult.Gap(LeaveEstimateGap.NO_MANUAL_AMOUNT)
            }
        }

        return when (valued) {
            is DayValueResult.Gap -> LeaveEstimate.NeedsInput(valued.gap)
            is DayValueResult.Value -> finish(
                request = request,
                payBasis = if (manual != null) MANUAL_BASIS else basis.persistedValue,
                dayValue = valued,
                multiplier = 1.0,
                ordinal = null,
                manual = manual,
                fixedDailyMinutes = policy.fixedDailyMinutes,
            )
        }
    }

    // ── Sick ──────────────────────────────────────────────────────────────────

    private fun estimateSick(request: LeaveEstimateRequest): LeaveEstimate {
        val policy = request.policy.rules.sick
        val manual = request.manualInput()
        if (!policy.enabled && manual == null) return LeaveEstimate.NeedsInput(LeaveEstimateGap.LEAVE_TYPE_DISABLED)

        // Counted from the illness, not from this workplace's first affected day.
        // A worker with two jobs whose second job's first missed day falls on day
        // three of one illness is on day three there too.
        val ordinal = request.event.ordinalFor(request.affectedDate)
        // A final amount is already what the day pays, percentage included, so it
        // needs no rung of the ladder.
        if (manual?.mode == LeaveManualMode.FINAL_PAYABLE) return finalPayable(request, manual, ordinal)

        // A daily amount is a full day's value and still takes this day's percentage —
        // which the ladder has to supply. Without a rung there is no percentage to
        // apply, and guessing 100% would overstate a day the policy may pay less for.
        val tier = SickPayCalculator.resolveTier(policy.payTiers, ordinal)
            ?: return LeaveEstimate.NeedsInput(LeaveEstimateGap.NO_MATCHING_TIER)

        val basis = policy.payBasis
        val valued = if (manual != null) {
            DayValueResult.Value(manual.amount)
        } else {
            when (basis) {
                SickPayBasis.HISTORICAL_AVERAGE ->
                    averaged(VacationPayCalculator.workdayAverage(request.history, request.affectedDate))

                SickPayBasis.SCHEDULED_HOURS -> scheduledHoursValue(request)
                SickPayBasis.FIXED_DAILY_HOURS -> fixedDailyValue(request, policy.fixedDailyMinutes)
                SickPayBasis.MANUAL -> DayValueResult.Gap(LeaveEstimateGap.NO_MANUAL_AMOUNT)
            }
        }

        return when (valued) {
            is DayValueResult.Gap -> LeaveEstimate.NeedsInput(valued.gap)
            is DayValueResult.Value -> finish(
                request = request,
                payBasis = if (manual != null) MANUAL_BASIS else basis.persistedValue,
                dayValue = valued,
                multiplier = tier.multiplier,
                ordinal = ordinal,
                manual = manual,
                fixedDailyMinutes = policy.fixedDailyMinutes,
            )
        }
    }

    // ── Valuing one full day ──────────────────────────────────────────────────

    private sealed interface DayValueResult {
        /** The value of one full absent day, and the average behind it if there was one. */
        data class Value(val perDay: Double, val average: LeaveAverage? = null) : DayValueResult

        data class Gap(val gap: LeaveEstimateGap) : DayValueResult
    }

    private fun averaged(average: LeaveAverage?): DayValueResult =
        if (average == null) {
            DayValueResult.Gap(LeaveEstimateGap.NO_PAY_HISTORY)
        } else {
            DayValueResult.Value(average.amountPerDay, average)
        }

    private fun scheduledHoursValue(request: LeaveEstimateRequest): DayValueResult {
        val minutes = request.expectedWorkMinutes
            ?: return DayValueResult.Gap(LeaveEstimateGap.NO_EXPECTED_HOURS)
        val rate = request.hourlyRate ?: return DayValueResult.Gap(LeaveEstimateGap.NO_WAGE)
        return DayValueResult.Value(rate * minutes / MINUTES_PER_HOUR)
    }

    private fun fixedDailyValue(
        request: LeaveEstimateRequest,
        fixedDailyMinutes: Int?,
    ): DayValueResult {
        val minutes = fixedDailyMinutes
            ?: request.policy.rules.standardDayMinutes
            ?: return DayValueResult.Gap(LeaveEstimateGap.NO_STANDARD_DAY)
        val rate = request.hourlyRate ?: return DayValueResult.Gap(LeaveEstimateGap.NO_WAGE)
        return DayValueResult.Value(rate * minutes / MINUTES_PER_HOUR)
    }

    /** A day priced at exactly what the user entered: no percentage, no part-day scaling. */
    private fun finalPayable(request: LeaveEstimateRequest, manual: ManualInput, ordinal: Int?): LeaveEstimate =
        LeaveEstimate.Ready(
            LeaveCalculationSnapshot(
                calculatedAt = request.calculatedAt,
                absenceType = request.event.type,
                workplaceId = request.workplaceId,
                payBasis = MANUAL_BASIS,
                currencyCode = request.currencyCode,
                sickDayOrdinal = ordinal,
                // 1.0, not the ladder's rung: no percentage was applied, and the stored
                // working has to multiply out to the stored result.
                multiplier = 1.0,
                expectedWorkMinutes = request.expectedWorkMinutes,
                baseAmount = manual.amount,
                estimatedGrossPay = manual.amount,
                balanceUnitsUsed = request.entitlementUnits,
                balanceUnit = request.unit,
                manualOverride = LeaveManualOverride(
                    enabled = true,
                    reason = request.manualReason,
                    mode = LeaveManualMode.FINAL_PAYABLE,
                    amount = manual.amount,
                ),
            ),
        )

    private fun finish(
        request: LeaveEstimateRequest,
        payBasis: String,
        dayValue: DayValueResult.Value,
        multiplier: Double,
        ordinal: Int?,
        manual: ManualInput? = null,
        fixedDailyMinutes: Int? = null,
    ): LeaveEstimate {
        // Hours are paid at the hourly rate; days scale the value of a full day.
        // Reading a day figure as if it were hours (or the reverse) is the one
        // arithmetic mistake here that yields a plausible wrong number rather than
        // an obviously wrong one, so the two cases stay explicit.
        val base = when (request.unit) {
            LeaveBalanceUnit.DAYS -> dayValue.perDay * request.entitlementUnits
            LeaveBalanceUnit.HOURS -> if (manual != null) {
                // A manual daily amount is what a day is worth, not an hourly rate. The
                // hours become a share of that day by the day's length — the hours the
                // user expected to work, else the policy's standard day. With neither
                // there is no honest share, and the user can enter a final amount.
                val dayMinutes = (
                    request.expectedWorkMinutes
                        ?: fixedDailyMinutes
                        ?: request.policy.rules.standardDayMinutes
                    )?.takeIf { it > 0 }
                    ?: return LeaveEstimate.NeedsInput(LeaveEstimateGap.NO_STANDARD_DAY)
                dayValue.perDay * request.entitlementUnits * MINUTES_PER_HOUR / dayMinutes
            } else {
                val rate = request.hourlyRate
                    ?: return LeaveEstimate.NeedsInput(LeaveEstimateGap.NO_WAGE)
                rate * request.entitlementUnits
            }
        }
        val average = dayValue.average
        return LeaveEstimate.Ready(
            LeaveCalculationSnapshot(
                calculatedAt = request.calculatedAt,
                absenceType = request.event.type,
                workplaceId = request.workplaceId,
                payBasis = payBasis,
                currencyCode = request.currencyCode,
                sickDayOrdinal = ordinal,
                multiplier = multiplier,
                expectedWorkMinutes = request.expectedWorkMinutes,
                baseAmount = base,
                estimatedGrossPay = base * multiplier,
                balanceUnitsUsed = request.entitlementUnits,
                balanceUnit = request.unit,
                averagePeriodStart = average?.periodStart,
                averagePeriodEnd = average?.periodEnd,
                averageGrossIncluded = average?.grossIncluded,
                averageDivisor = average?.divisor,
                usedFallbackAveragePeriod = average?.usedFallbackPeriod ?: false,
                // Only when the user's amount actually priced the day. This used to be set
                // whenever one had been typed, so a record could claim an override the
                // engine had ignored.
                manualOverride = manual?.let {
                    LeaveManualOverride(
                        enabled = true,
                        reason = request.manualReason,
                        mode = it.mode,
                        amount = it.amount,
                    )
                },
            ),
        )
    }
}

package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.COMPENSATION_ESTIMATE_NOTE
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.compensation.IsraeliCompensationEngine
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.OvertimeTier
import com.elmtrackr.app.domain.model.PayBracket
import com.elmtrackr.app.domain.model.PayBucket
import com.elmtrackr.app.domain.model.PayCategory
import com.elmtrackr.app.domain.model.paysDailyOvertime
import com.elmtrackr.app.domain.model.ResolvedCompensation
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.ShiftPayBreakdown
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.domain.premium.PremiumStacking
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.time.WallClockTime
import com.elmtrackr.app.domain.time.WorkTimezone
import com.elmtrackr.app.domain.time.ZoneMinutes
import kotlin.math.min

/**
 * Compensation estimation based on user-configured profiles.
 * ElmTrackr does not provide legally compliant payroll calculations.
 */
object PayrollCalculator {

    internal data class Tier(
        val label: String,
        val capMinutes: Int,
        val rate: Double,
        val category: PayCategory,
    )

    fun calculateShiftPay(
        shift: Shift,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
        priorWeekMinutes: Int = 0,
        premiumProfiles: List<PremiumProfile> = emptyList(),
        allShiftsInWeek: List<Shift> = emptyList(),
        isSeventhConsecutiveWorkday: Boolean = false,
    ): ShiftPayBreakdown? {
        if (shift.endTime == null) return null
        // Project time earns the project's fixed fee, not wages. Refusing it here
        // rather than only at the call sites means a caller that passes a mixed
        // list still gets employee-only pay.
        if (!shift.isEmployeePaid) return null
        val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
        if (resolved.regionCode == RegionCode.IL) {
            return if (allShiftsInWeek.isNotEmpty()) {
                IsraeliCompensationEngine.calculateIsraeliShiftPay(
                    shift, allShiftsInWeek, settings, profiles, premiumProfiles,
                )
            } else {
                calculateIsraeliShiftPayWithPriorRegularMinutes(
                    shift, resolved, settings, profiles, priorWeekMinutes, premiumProfiles,
                )
            }
        }
        return calculateGenericShiftPay(
            shift, settings, profiles, priorWeekMinutes, premiumProfiles, resolved,
            isSeventhConsecutiveWorkday,
        )
    }

    private fun calculateIsraeliShiftPayWithPriorRegularMinutes(
        shift: Shift,
        resolved: ResolvedCompensation,
        settings: UserSettings,
        profiles: List<CompensationProfile>,
        priorWeekRegularMinutes: Int,
        premiumProfiles: List<PremiumProfile>,
    ): ShiftPayBreakdown? {
        val hourlyRate = resolved.baseHourlyRate?.takeIf { it > 0 } ?: return null
        val zone = WorkTimezone.zoneFor(resolved, settings)
        val segments = IsraeliCompensationEngine.classifyShiftSegments(
            shift = shift,
            weeklyRegularMinutesBefore = priorWeekRegularMinutes,
            weeklyOvertimeMinutesBefore = 0,
            resolved = resolved,
            zone = zone,
            premiumProfiles = premiumProfiles,
        )
        if (segments.isEmpty()) return null

        return israeliBreakdown(
            segments = segments,
            shift = shift,
            resolved = resolved,
            zone = zone,
            hourlyRate = hourlyRate,
            premiumProfiles = premiumProfiles,
        )
    }

    /**
     * Turns Israeli-engine segments into a pay breakdown.
     *
     * Shared because this loop existed twice — here and in
     * [IsraeliCompensationEngine.calculateIsraeliShiftPay] — and the two copies had
     * already drifted apart in two ways. Both hardcoded `nightGross = 0.0`, so a
     * user on the Israeli region could configure a night premium and see no effect
     * anywhere and no warning; the night rules were honoured only by the generic
     * engine. And only one copy excluded `forceRegularRate` from the manual-holiday
     * check, so "pay this shift at the regular rate" was respected on one path and
     * ignored on the other.
     *
     * The night uplift is applied exactly as [calculateGenericShiftPay] applies it:
     * each segment's own minutes carry the uplift in proportion to how much of the
     * shift falls inside the night window, blended into the rate so the bracket
     * total stays exact and every category bucket stays non-negative.
     */
    private fun israeliBreakdown(
        segments: List<IsraeliCompensationEngine.ClassifiedPaySegment>,
        shift: Shift,
        resolved: ResolvedCompensation,
        zone: java.time.ZoneId,
        hourlyRate: Double,
        premiumProfiles: List<PremiumProfile>,
    ): ShiftPayBreakdown {
        val ratePerMinute = hourlyRate / 60.0
        var regularGross = 0.0
        var overtimeGross = 0.0
        var weekendGross = 0.0
        var holidayGross = 0.0
        var nightGross = 0.0

        val shiftPremiumType = if (shift.forceRegularRate) {
            null
        } else {
            shift.premiumProfileId?.let { id ->
                premiumProfiles.firstOrNull { it.id == id || it.remoteId == id }?.premiumType
            }
        }

        val nightFraction = nightFractionFor(resolved, shift, zone)

        val brackets = segments.map { segment ->
            val nightRate = if (nightFraction > 0.0) {
                nightStackedRate(segment.multiplier, resolved, shiftPremiumType)
            } else {
                segment.multiplier
            }
            val blendedRate = segment.multiplier + nightFraction * (nightRate - segment.multiplier)
            val amount = segment.minutes * ratePerMinute * blendedRate
            val nightPremium =
                segment.minutes * ratePerMinute * nightFraction * (nightRate - segment.multiplier)
            val net = amount - nightPremium
            when (segment.category.bucket) {
                PayBucket.REGULAR -> regularGross += net
                PayBucket.OVERTIME -> overtimeGross += net
                PayBucket.WEEKEND -> weekendGross += net
                PayBucket.HOLIDAY -> holidayGross += net
            }
            nightGross += nightPremium
            PayBracket(segment.label, segment.minutes, blendedRate, amount, segment.category)
        }

        val isSpecial = segments.any { it.isWeeklyRest } ||
            (shift.isSpecialDay && !shift.forceRegularRate && resolved.rules.holidayManualSpecialDayEnabled)

        val totalGross = brackets.sumOf { it.amount }
        val deductions = deductionsFor(totalGross, resolved.rules)
        return ShiftPayBreakdown(
            brackets = brackets,
            totalGross = totalGross,
            regularGross = regularGross,
            overtimeGross = overtimeGross,
            weekendGross = weekendGross,
            holidayGross = holidayGross,
            nightGross = nightGross,
            deductionsGross = deductions,
            netGross = maxOf(0.0, totalGross - deductions),
            isSpecial = isSpecial,
            profileId = resolved.profileId,
            profileName = resolved.profileName,
            currencyCode = resolved.currencyCode,
            disclaimer = COMPENSATION_ESTIMATE_NOTE,
        )
    }

    /** Shared with [IsraeliCompensationEngine], which classifies its own segments. */
    internal fun israeliBreakdownOf(
        segments: List<IsraeliCompensationEngine.ClassifiedPaySegment>,
        shift: Shift,
        resolved: ResolvedCompensation,
        zone: java.time.ZoneId,
        hourlyRate: Double,
        premiumProfiles: List<PremiumProfile>,
    ): ShiftPayBreakdown =
        israeliBreakdown(segments, shift, resolved, zone, hourlyRate, premiumProfiles)

    private fun calculateGenericShiftPay(
        shift: Shift,
        settings: UserSettings,
        profiles: List<CompensationProfile>,
        priorWeekMinutes: Int,
        premiumProfiles: List<PremiumProfile>,
        resolved: ResolvedCompensation,
        isSeventhConsecutiveWorkday: Boolean = false,
    ): ShiftPayBreakdown? {
        val hourlyRate = resolved.baseHourlyRate?.takeIf { it > 0 } ?: return null
        val plan = genericPlan(
            shift, resolved, settings, priorWeekMinutes, premiumProfiles,
            isSeventhConsecutiveWorkday,
        ) ?: return null
        val net = plan.net
        val tiers = plan.tiers
        val isSpecial = plan.isSpecial
        val shiftPremium = plan.shiftPremium

        val ratePerMinute = hourlyRate / 60.0
        val zone = WorkTimezone.zoneFor(resolved, settings)
        val brackets = mutableListOf<PayBracket>()
        var remaining = net
        var regularGross = 0.0
        var overtimeGross = 0.0
        var weekendGross = 0.0
        var holidayGross = 0.0
        var nightGross = 0.0

        val nightFraction = nightFractionFor(resolved, shift, zone)

        for (tier in tiers) {
            if (remaining <= 0) break
            val mins = if (tier.capMinutes == Int.MAX_VALUE) remaining else minOf(remaining, tier.capMinutes)
            val nightRate = if (nightFraction > 0.0) {
                nightStackedRate(tier.rate, resolved, shiftPremium?.premiumType)
            } else {
                tier.rate
            }
            // Night uplift applies to this tier's own minutes, proportional to
            // the share of the shift inside the night window. The blended rate
            // keeps totals exact and every category bucket non-negative.
            val blendedRate = tier.rate + nightFraction * (nightRate - tier.rate)
            val amount = mins * ratePerMinute * blendedRate
            val nightPremium = mins * ratePerMinute * nightFraction * (nightRate - tier.rate)
            brackets += PayBracket(tier.label, mins, blendedRate, amount, tier.category)

            // Was a six-way substring match on the English label, whose fifth and
            // sixth branches differed only in case and so could never both be
            // reached — `contains("overtime", ignoreCase = true)` had already
            // matched anything `contains("Overtime", ignoreCase = true)` would.
            // That dead branch is the kind of thing string matching hides.
            val net = amount - nightPremium
            when (tier.category.bucket) {
                PayBucket.REGULAR -> regularGross += net
                PayBucket.OVERTIME -> overtimeGross += net
                PayBucket.WEEKEND -> weekendGross += net
                PayBucket.HOLIDAY -> holidayGross += net
            }
            nightGross += nightPremium
            remaining -= mins
        }

        val totalGross = brackets.sumOf { it.amount }
        val deductionsGross = deductionsFor(totalGross, resolved.rules)

        return ShiftPayBreakdown(
            brackets = brackets,
            totalGross = totalGross,
            regularGross = regularGross,
            overtimeGross = overtimeGross,
            weekendGross = weekendGross,
            holidayGross = holidayGross,
            nightGross = nightGross,
            deductionsGross = deductionsGross,
            netGross = maxOf(0.0, totalGross - deductionsGross),
            isSpecial = isSpecial,
            profileId = resolved.profileId,
            profileName = resolved.profileName,
            currencyCode = resolved.currencyCode,
            disclaimer = COMPENSATION_ESTIMATE_NOTE,
        )
    }

    /**
     * What the generic engine decides about a shift *before* any money is applied:
     * how many minutes are payable, the tier ladder they run through, and whether
     * the shift counts as special.
     *
     * Split out of [calculateGenericShiftPay] so the same decision can answer two
     * questions. Pay needs an hourly rate; **hours do not**, and the monthly report
     * has to classify minutes for a profile that has no rate set at all. Before
     * this, the report classified them itself — with a whole-day weekend test and
     * the raw daily standard — and disagreed with the money for exactly the shifts
     * where it matters.
     */
    internal data class GenericPlan(
        val net: Int,
        val tiers: List<Tier>,
        val isSpecial: Boolean,
        val shiftPremium: PremiumProfile?,
    )

    internal fun genericPlan(
        shift: Shift,
        resolved: ResolvedCompensation,
        settings: UserSettings,
        priorWeekMinutes: Int,
        premiumProfiles: List<PremiumProfile>,
        isSeventhConsecutiveWorkday: Boolean = false,
    ): GenericPlan? {
        val net = payableNetMinutes(shift, resolved.rules)?.takeIf { it > 0 } ?: return null
        val zone = WorkTimezone.zoneFor(resolved, settings)
        val startDateStr = shift.startTime.atZone(zone).toLocalDate().toString()
        val startOnWeekend = resolved.rules.weekendEnabled &&
            WeekendRules.isWeekendDate(startDateStr, resolved.rules.weekendDays)
        val shiftPremium = if (shift.forceRegularRate) {
            null
        } else {
            shift.premiumProfileId?.let { id ->
                premiumProfiles.firstOrNull { it.id == id || it.remoteId == id }
            }
        }
        val isHoliday = shiftPremium != null ||
            (resolved.rules.holidayEnabled &&
                resolved.rules.holidayManualSpecialDayEnabled &&
                shift.isSpecialDay && !shift.forceRegularRate)
        // Weekend minutes are counted per local day. Deciding the whole shift from
        // its start date paid a Friday 23:00 → Saturday 07:00 shift entirely at the
        // weekday rate and a Saturday 23:00 → Sunday 07:00 shift entirely at the
        // weekend rate, while the report split both proportionally — so the report's
        // weekend hours and the payroll's weekend money described different halves of
        // the same shift.
        val weekendMinutes = genericWeekendMinutes(shift, resolved, zone, net, isHoliday)
        val isSpecial = isHoliday || startOnWeekend

        // A shift that sits entirely on one side of the boundary keeps its existing
        // path exactly; only a genuinely mixed shift is split. Holidays stay
        // all-or-nothing: a holiday is a property of the shift, declared by the user,
        // not of each local day it touches.
        val tiers = if (weekendMinutes in 1 until net && !isHoliday) {
            val weekdayMinutes = net - weekendMinutes
            // The weekday portion runs the ordinary ladder over its own minutes only —
            // "overtime is computed from the weekday-only portion" is the report's
            // stated invariant, and this is what makes the two agree.
            buildTiers(
                resolved = resolved,
                isSpecial = false,
                isHoliday = false,
                startOnWeekend = false,
                net = weekdayMinutes,
                priorWeekMinutes = priorWeekMinutes,
                shiftPremium = null,
                shift = shift,
                zone = zone,
                isSeventhConsecutiveWorkday = isSeventhConsecutiveWorkday,
            ).capTo(weekdayMinutes) + weekendTier(resolved, weekendMinutes)
        } else {
            buildTiers(
                resolved = resolved,
                isSpecial = isSpecial,
                isHoliday = isHoliday,
                startOnWeekend = startOnWeekend,
                net = net,
                priorWeekMinutes = priorWeekMinutes,
                shiftPremium = shiftPremium,
                shift = shift,
                zone = zone,
                isSeventhConsecutiveWorkday = isSeventhConsecutiveWorkday,
            )
        }
        return GenericPlan(net, tiers, isSpecial, shiftPremium)
    }

    fun calculateShiftPayInContext(
        shift: Shift,
        allCompletedShifts: List<Shift>,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
        premiumProfiles: List<PremiumProfile> = emptyList(),
    ): ShiftPayBreakdown? {
        if (!shift.isEmployeePaid) return null
        // The week context must be employee-only too: project hours must not push
        // genuine employee hours over a daily or weekly overtime threshold, nor
        // count as one of the six prior workdays for a seventh-day premium.
        val employeeShifts = allCompletedShifts.employeePaidOnly()
        val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
        if (resolved.regionCode == RegionCode.IL) {
            return IsraeliCompensationEngine.calculateIsraeliShiftPay(
                shift, employeeShifts, settings, profiles, premiumProfiles,
            )
        }
        val zone = WorkTimezone.zoneFor(resolved, settings)
        val prior = priorStraightTimeMinutesBefore(
            shift, employeeShifts, settings, profiles, zone, resolved.rules,
        )
        val seventhDay = isSeventhConsecutiveWorkday(shift, employeeShifts, resolved.rules, zone)
        return calculateShiftPay(
            shift, settings, profiles, prior, premiumProfiles,
            isSeventhConsecutiveWorkday = seventhDay,
        )
    }

    /**
     * Straight-time minutes worked earlier in the same pay week (anchored to
     * [anchorRules].weekStartDay). Minutes already paid at a daily overtime
     * premium do not also count toward the weekly overtime threshold (the
     * anti-pyramiding rule used by US federal and California overtime law:
     * each hour is credited against one threshold, not both).
     */
    internal fun priorStraightTimeMinutesBefore(
        shift: Shift,
        completedShifts: List<Shift>,
        settings: UserSettings,
        profiles: List<CompensationProfile>,
        zone: java.time.ZoneId,
        anchorRules: CompensationRules,
    ): Int {
        val weekStart = PayWeekMinutes.weekStart(shift, zone, anchorRules.weekStartDay)
        return completedShifts
            .asSequence()
            .filter { it.isEmployeePaid }
            .filter { it.id != shift.id && it.endTime != null }
            .filter { PayWeekMinutes.weekStart(it, zone, anchorRules.weekStartDay) == weekStart }
            .filter { it.startTime.isBefore(shift.startTime) }
            .sumOf { prior ->
                val rules = CompensationResolver.resolveShiftCompensation(prior, settings, profiles).rules
                val net = payableNetMinutes(prior, rules) ?: 0
                if (rules.paysDailyOvertime) {
                    min(net, effectiveDailyStandardMinutes(rules, prior, zone))
                } else {
                    net
                }
            }
    }

    /**
     * True when [shift] falls on the 7th distinct workday of its pay week —
     * i.e. the person already worked the other six days of the week. Used for
     * California-style 7th-consecutive-day premiums; requires [CompensationRules.seventhDayEnabled].
     */
    internal fun isSeventhConsecutiveWorkday(
        shift: Shift,
        completedShifts: List<Shift>,
        rules: CompensationRules,
        zone: java.time.ZoneId,
    ): Boolean {
        if (!rules.seventhDayEnabled || rules.seventhDayTiers.isEmpty()) return false
        val weekStart = PayWeekMinutes.weekStart(shift, zone, rules.weekStartDay)
        val shiftDate = shift.startTime.atZone(zone).toLocalDate()
        val priorDates = completedShifts
            .asSequence()
            .filter { it.isEmployeePaid }
            .filter { it.id != shift.id && it.endTime != null }
            .filter { PayWeekMinutes.weekStart(it, zone, rules.weekStartDay) == weekStart }
            .filter { it.startTime.isBefore(shift.startTime) }
            .map { it.startTime.atZone(zone).toLocalDate() }
            .toSet()
        return shiftDate !in priorDates && priorDates.size >= 6
    }

    /**
     * Minutes a shift is paid for, applying the profile's time rules to the
     * recorded clock time:
     * - paid breaks: recorded break minutes are not deducted;
     * - auto break deduction: when no break was recorded, the configured
     *   unpaid break is deducted automatically (only if the shift is longer
     *   than the break itself);
     * - rounding: payable minutes rounded to the configured increment;
     * - minimum shift: short shifts are topped up to the guaranteed minimum
     *   (reporting-time / minimum-call pay).
     * Returns null for active shifts.
     */
    /**
     * Deduction attributable to a single shift's gross.
     *
     * Only the percentage mode scales with one shift. A "fixed" deduction is a charge on
     * the pay period (a monthly fee), so charging it per shift multiplied it by the shift
     * count — 22 shifts against a 300 fixed amount deducted 6 600 from the month. The fixed
     * amount is applied once by [fixedPeriodDeduction] at the summary level instead.
     */
    internal fun deductionsFor(totalGross: Double, rules: CompensationRules): Double {
        if (!rules.deductionsEnabled) return 0.0
        return when (rules.deductionsMode) {
            "percentage" -> totalGross * (rules.deductionsPercentage / 100.0)
            else -> 0.0
        }
    }

    /** The once-per-period portion of the deduction rules, or 0.0 when not in fixed mode. */
    internal fun fixedPeriodDeduction(rules: CompensationRules): Double {
        if (!rules.deductionsEnabled) return 0.0
        return if (rules.deductionsMode == "fixed") rules.deductionsFixedAmount else 0.0
    }

    internal fun payableNetMinutes(shift: Shift, rules: CompensationRules): Int? {
        val gross = ShiftDurationCalculator.grossMinutes(shift) ?: return null
        var net = if (rules.paidBreaks) gross else maxOf(0, gross - shift.breakMinutes)
        val autoBreak = rules.autoDeductBreakMinutes ?: 0
        if (!rules.paidBreaks && autoBreak > 0 && shift.breakMinutes == 0 && net > autoBreak) {
            net -= autoBreak
        }
        // Held before rounding, for the minimum-shift guard below.
        val workedNet = net
        val rounding = rules.rounding
        if (rounding.enabled && rounding.incrementMinutes > 0 && net > 0) {
            val inc = rounding.incrementMinutes
            net = when (rounding.direction) {
                "up" -> ((net + inc - 1) / inc) * inc
                "down" -> (net / inc) * inc
                else -> ((net + inc / 2) / inc) * inc
            }
        }
        val minimum = rules.minimumShiftMinutes ?: 0
        // Judged against the *worked* net, not the rounded one. Rounding can take a
        // short shift to zero — 7 minutes at a 15-minute "nearest" increment, or any
        // sub-increment shift rounded "down" — and the previous guard was
        // `net in 1 until minimum`, which excluded zero and so skipped the floor
        // entirely. The shift then paid nothing, which is the opposite of what a
        // minimum-shift rule exists to do.
        //
        // `workedNet > 0` is what keeps a genuinely empty shift at zero: a shift whose
        // break consumes all of its gross time has no worked minutes to guarantee.
        if (minimum > 0 && workedNet > 0 && net < minimum) net = minimum
        return net
    }

    /**
     * @param contextShifts a window wide enough to cover the pay weeks the reported
     *   [shifts] belong to — normally the month plus the tail of the week containing
     *   the 1st. Only [shifts] are summed; [contextShifts] supply the prior-minutes
     *   accumulation that decides weekly overtime. Defaulting to [shifts] is what the
     *   code did implicitly, and is why a pay week straddling the 1st began with no
     *   prior minutes and under-counted overtime in the first partial week.
     */
    fun sumMonthlyPay(
        shifts: List<Shift>,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
        premiumProfiles: List<PremiumProfile> = emptyList(),
        contextShifts: List<Shift> = shifts,
    ): MonthlyPaySummary {
        // Employee-only from here down: project shifts contribute no gross, and
        // must not appear in the week accumulation that drives overtime either.
        @Suppress("NAME_SHADOWING") val shifts = shifts.employeePaidOnly()
        val context = contextShifts.employeePaidOnly().ifEmpty { shifts }
        var totalGross = 0.0
        var regularGross = 0.0
        var overtimeGross = 0.0
        var specialGross = 0.0
        var weekendGross = 0.0
        var holidayGross = 0.0
        var nightGross = 0.0
        var deductionsGross = 0.0
        // Gross per currency, rather than one running total and one label.
        //
        // A profile carries its own currencyCode, so a month worked across two
        // jobs in two currencies was summed into a single number and then stamped
        // with whichever shift the fold happened to visit last — an amount that is
        // not a quantity of anything. The rest of the money layer refuses this
        // structurally (`Money.requireSameCurrency`, `MoneyByCurrency`); the wage
        // path did not.
        val grossByCurrency = mutableMapOf<String, Double>()
        // Fixed deductions are charged once per period, not per shift. Keyed by profile so
        // a month spanning two profiles charges each of their fees once.
        val fixedDeductionsByProfile = mutableMapOf<String, Double>()

        // Chronological, keyed on nothing but the start time. The previous pass grouped
        // by ISO week to hand each shift a running week total that this fold then threw
        // away (`{ shift, _ -> }`), and resolved a compensation profile for every shift
        // purely to pick the zone that the discarded grouping needed — two resolves per
        // shift where one does. Grouping never reordered anything either: the groups were
        // built from an already-chronological list, so they came out in week order with
        // each week in start order, which is the same sequence as sorting once here.
        for (shift in shifts.filter { it.endTime != null }.sortedBy { it.startTime }) {
            val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
            val bd = if (resolved.regionCode == RegionCode.IL) {
                IsraeliCompensationEngine.calculateIsraeliShiftPay(
                    shift, context, settings, profiles, premiumProfiles,
                )
            } else {
                calculateShiftPayInContext(shift, context, settings, profiles, premiumProfiles)
            } ?: continue
            totalGross += bd.totalGross
            regularGross += bd.regularGross
            overtimeGross += bd.overtimeGross
            weekendGross += bd.weekendGross
            holidayGross += bd.holidayGross
            nightGross += bd.nightGross
            deductionsGross += bd.deductionsGross
            specialGross += bd.weekendGross + bd.holidayGross
            grossByCurrency[bd.currencyCode] =
                (grossByCurrency[bd.currencyCode] ?: 0.0) + bd.totalGross
            fixedPeriodDeduction(resolved.rules).takeIf { it != 0.0 }?.let { fixed ->
                fixedDeductionsByProfile[resolved.profileId ?: PERIOD_DEDUCTION_FALLBACK_KEY] = fixed
            }
        }

        deductionsGross += fixedDeductionsByProfile.values.sum()

        return MonthlyPaySummary(
            totalGross, regularGross, overtimeGross, specialGross,
            weekendGross, holidayGross, nightGross, deductionsGross,
            // Derived from the clamped total rather than summing per-shift nets: those are
            // each floored at 0 while deductions kept accumulating, so the summary could
            // report totalGross - deductionsGross != netGross.
            netGross = maxOf(0.0, totalGross - deductionsGross),
            // The single currency when exactly one was priced; null when none was
            // (an empty month) or when several were. Null is what every display
            // site already handles — each one reads
            // `paySummary?.currencyCode ?: settings.displayCurrencyCode()` — so a
            // month with a rate but no shifts yet stops showing "$" to a user
            // whose settings say ILS, which is what the old "USD" default did.
            currencyCode = grossByCurrency.keys.singleOrNull(),
            grossByCurrency = grossByCurrency.toMap(),
        )
    }

    private const val PERIOD_DEDUCTION_FALLBACK_KEY = "__legacy_settings__"

    /**
     * How many of a shift's payable minutes fall on a weekend day, counted per local
     * day rather than decided by the start date.
     *
     * Proportioned onto the payable net rather than taken from the segments directly:
     * [OvernightShiftDetector.splitShiftByDay] divides net wall-clock minutes across
     * days, while `net` here has already had breaks, rounding and the minimum-shift
     * floor applied. Scaling keeps `weekday + weekend == net` exactly, so no minute is
     * lost or paid twice.
     *
     * Returns 0 for a holiday or a force-regular shift: both are shift-level
     * declarations that override the calendar.
     */
    private fun genericWeekendMinutes(
        shift: Shift,
        resolved: ResolvedCompensation,
        zone: java.time.ZoneId,
        net: Int,
        isHoliday: Boolean,
    ): Int {
        if (!resolved.rules.weekendEnabled || isHoliday || shift.forceRegularRate) return 0
        val segments = WeekendRules.annotateWeekendSegments(
            OvernightShiftDetector.splitShiftByDay(shift, zone),
            resolved.rules.weekendDays,
        )
        val total = segments.sumOf { it.minutes }
        if (total <= 0) return 0
        val weekend = WeekendRules.totalWeekendMinutes(segments)
        return when {
            weekend <= 0 -> 0
            weekend >= total -> net
            else -> (net.toLong() * weekend / total).toInt().coerceIn(0, net)
        }
    }

    /**
     * Closes an open tier ladder at [limit] minutes.
     *
     * The last tier of a ladder carries [Int.MAX_VALUE] so it absorbs everything left.
     * Appending a second ladder behind one of those would leave it nothing, so the
     * weekday ladder is bounded to its own minutes before the weekend tier follows it.
     */
    private fun List<Tier>.capTo(limit: Int): List<Tier> {
        val out = mutableListOf<Tier>()
        var remaining = limit
        for (tier in this) {
            if (remaining <= 0) break
            val cap = if (tier.capMinutes == Int.MAX_VALUE) remaining else minOf(tier.capMinutes, remaining)
            out += tier.copy(capMinutes = cap)
            remaining -= cap
        }
        return out
    }

    /** The weekend portion of a mixed shift: one flat tier, labelled so it lands in `weekendGross`. */
    private fun weekendTier(resolved: ResolvedCompensation, minutes: Int): List<Tier> {
        val multiplier = resolved.rules.weekendMultiplier
        val premium = multiplier > 1.0
        val label = if (premium) "${(multiplier * 100).toInt()}% — Weekend" else "100% — Regular"
        return listOf(
            Tier(label, minutes, multiplier, if (premium) PayCategory.WEEKEND else PayCategory.REGULAR),
        )
    }

    private fun buildTiers(
        resolved: ResolvedCompensation,
        isSpecial: Boolean,
        isHoliday: Boolean,
        startOnWeekend: Boolean,
        net: Int,
        priorWeekMinutes: Int,
        shiftPremium: PremiumProfile? = null,
        shift: Shift? = null,
        zone: java.time.ZoneId? = null,
        isSeventhConsecutiveWorkday: Boolean = false,
    ): List<Tier> {
        val rules = resolved.rules
        shiftPremium?.let {
            val pct = (it.multiplier * 100).toInt()
            return listOf(
                Tier("$pct% — Premium (${it.name})", Int.MAX_VALUE, it.multiplier, PayCategory.PREMIUM),
            )
        }

        if (!isSpecial && isSeventhConsecutiveWorkday && rules.overtimeEnabled &&
            rules.seventhDayEnabled && rules.seventhDayTiers.isNotEmpty()
        ) {
            // Every minute of the 7th consecutive workday is premium time; the ladder
            // in seventhDayTiers replaces the regular daily standard and tiers.
            val segments = buildCombinedRateSegments(
                resolved = resolved,
                net = net,
                priorWeekMinutes = priorWeekMinutes,
                dailyStandard = 0,
                dailyTiersOverride = rules.seventhDayTiers,
            )
            return segments.map { (minutes, rate) ->
                val regular = rate <= 1.0 + 1e-9
                val label = if (regular) "100% — Regular" else "${(rate * 100).toInt()}% — Overtime"
                Tier(label, minutes, rate, if (regular) PayCategory.REGULAR else PayCategory.SEVENTH_DAY)
            }
        }

        if (isSpecial && rules.overtimeEnabled) {
            // Weekly-rest / holiday premium does not automatically imply overtime.
            // Apply the rest base rate to all hours, then layer daily/weekly OT on top.
            val restBase = when {
                isHoliday && rules.holidayEnabled -> rules.holidayMultiplier
                startOnWeekend && rules.weekendEnabled -> rules.weekendMultiplier
                rules.holidayEnabled -> rules.holidayMultiplier
                else -> rules.weekendMultiplier
            }
            val restKind = if (isHoliday && !startOnWeekend) HOLIDAY_REST_KIND else WEEKLY_REST_KIND
            val dailyStandard = if (shift != null && zone != null) {
                effectiveDailyStandardMinutes(rules, shift, zone)
            } else {
                rules.dailyStandardMinutes
            }
            return buildWeeklyRestWithOvertimeTiers(
                resolved = resolved,
                net = net,
                priorWeekMinutes = priorWeekMinutes,
                restBase = restBase,
                restKind = restKind,
                dailyStandard = dailyStandard,
            )
        }

        if (isSpecial) {
            if (rules.holidayEnabled) {
                return listOf(
                    Tier(
                        "${(rules.holidayMultiplier * 100).toInt()}% — Holiday",
                        Int.MAX_VALUE,
                        rules.holidayMultiplier,
                        PayCategory.HOLIDAY,
                    ),
                )
            }
            if (rules.weekendEnabled) {
                return listOf(
                    Tier(
                        "${(rules.weekendMultiplier * 100).toInt()}% — Weekend",
                        Int.MAX_VALUE,
                        rules.weekendMultiplier,
                        PayCategory.WEEKEND,
                    ),
                )
            }
        }

        if (!rules.overtimeEnabled || shiftPremium?.premiumType == PremiumType.EXCLUDED_FROM_REGULAR_RATE) {
            return listOf(Tier("100% — Regular", Int.MAX_VALUE, 1.0, PayCategory.REGULAR))
        }

        val otBaseMultiplier = shiftPremium?.let {
            PremiumStacking.regularRateForOvertime(1.0, it.multiplier, it.premiumType)
        } ?: 1.0

        val dailyStandard = if (shift != null && zone != null) {
            effectiveDailyStandardMinutes(rules, shift, zone)
        } else {
            rules.dailyStandardMinutes
        }

        val segments = if (otBaseMultiplier > 1.0 + 1e-9) {
            buildCombinedRateSegments(resolved, net, priorWeekMinutes, dailyStandard).map { (minutes, rate) ->
                minutes to rate * otBaseMultiplier
            }
        } else {
            buildCombinedRateSegments(resolved, net, priorWeekMinutes, dailyStandard)
        }
        if (segments.isEmpty()) {
            return listOf(Tier("100% — Regular", Int.MAX_VALUE, 1.0, PayCategory.REGULAR))
        }

        return segments.map { (minutes, rate) ->
            val regular = rate <= 1.0 + 1e-9
            val label = if (regular) "100% — Regular" else "${(rate * 100).toInt()}% — Overtime"
            Tier(label, minutes, rate, if (regular) PayCategory.REGULAR else PayCategory.OVERTIME)
        }
    }

    /**
     * The category for a rest-or-holiday tier.
     *
     * [restKind] is the word that goes in the label, and it is the one place the
     * generic engine still decides a category from a string. It is safe because
     * the string is produced two lines above by [buildTiers] rather than parsed
     * from anything, and it is confined here so localising the label cannot reach
     * it.
     */
    private fun restCategory(restKind: String, overtime: Boolean): PayCategory =
        when {
            restKind == HOLIDAY_REST_KIND && overtime -> PayCategory.HOLIDAY_OVERTIME
            restKind == HOLIDAY_REST_KIND -> PayCategory.HOLIDAY
            overtime -> PayCategory.WEEKLY_REST_OVERTIME
            else -> PayCategory.WEEKLY_REST
        }

    private const val HOLIDAY_REST_KIND = "Holiday"
    private const val WEEKLY_REST_KIND = "Weekly rest"

    /**
     * Maps daily/weekly overtime segments onto a weekly-rest or holiday base rate.
     * Regular rest hours stay at [restBase]; OT hours add the OT premium above 1×
     * (e.g. 150% rest + 25% OT = 175%, 150% rest + 50% OT = 200%).
     */
    internal fun buildWeeklyRestWithOvertimeTiers(
        resolved: ResolvedCompensation,
        net: Int,
        priorWeekMinutes: Int,
        restBase: Double,
        restKind: String,
        dailyStandard: Int,
    ): List<Tier> {
        val segments = buildCombinedRateSegments(resolved, net, priorWeekMinutes, dailyStandard)
        if (segments.isEmpty()) {
            return listOf(
                Tier(
                    "${(restBase * 100).toInt()}% — $restKind regular",
                    Int.MAX_VALUE,
                    restBase,
                    restCategory(restKind, overtime = false),
                ),
            )
        }
        return segments.map { (minutes, otRate) ->
            val isOvertime = otRate > 1.0 + 1e-9
            val rate = if (isOvertime) restBase + (otRate - 1.0) else restBase
            val label = if (isOvertime) {
                "${(rate * 100).toInt()}% — $restKind overtime"
            } else {
                "${(rate * 100).toInt()}% — $restKind regular"
            }
            Tier(label, minutes, rate, restCategory(restKind, isOvertime))
        }
    }

    /** Minimum minutes inside the night window for a shift to count as night work. */
    internal const val NIGHT_SHIFT_MIN_NIGHT_MINUTES = 120

    /** 22:00 and 06:00, the shipped night window and the fallback for a bad one. */
    private const val DEFAULT_NIGHT_START_MINUTES = 22 * 60
    private const val DEFAULT_NIGHT_END_MINUTES = 6 * 60

    internal fun effectiveDailyStandardMinutes(
        rules: CompensationRules,
        shift: Shift,
        zone: java.time.ZoneId,
    ): Int {
        val nightStandard = rules.nightDailyStandardMinutes
        if (nightStandard != null && isNightWorkShift(shift, rules, zone)) {
            return nightStandard
        }
        return rules.dailyStandardMinutes
    }

    /**
     * A shift counts as night work when at least two hours of it fall inside the
     * night window — the definition used by the Israeli Hours of Work and Rest Law
     * (≥2 h between 22:00 and 06:00 makes the whole workday a 7-hour night workday).
     */
    internal fun isNightWorkShift(
        shift: Shift,
        rules: CompensationRules,
        zone: java.time.ZoneId,
    ): Boolean {
        if (!rules.nightEnabled || rules.nightDailyStandardMinutes == null || shift.endTime == null) {
            return false
        }
        val net = ShiftDurationCalculator.netMinutes(shift) ?: return false
        if (net <= 0) return false
        // Wall-clock, not break-scaled: this asks whether the shift *fell* across
        // the night window, which is a question about the clock.
        val nightMinutes = countNightMinutes(shift, rules.nightStartTime, rules.nightEndTime, zone)
        return nightMinutes >= NIGHT_SHIFT_MIN_NIGHT_MINUTES
    }

    internal fun effectiveDailyOvertimeTiers(
        rules: CompensationRules,
        dailyStandard: Int,
    ): List<OvertimeTier> {
        if (rules.dailyOvertimeTiers.isEmpty()) return emptyList()
        val delta = dailyStandard - rules.dailyStandardMinutes
        return rules.dailyOvertimeTiers.map { OvertimeTier(it.afterMinutes + delta, it.multiplier) }
    }

    /**
     * Builds consecutive (length, effectiveMultiplier) segments for a shift, combining daily and
     * weekly overtime ladders per [StackingPolicy].
     */
    internal fun buildCombinedRateSegments(
        resolved: ResolvedCompensation,
        net: Int,
        priorWeekMinutes: Int,
        dailyStandard: Int = resolved.rules.dailyStandardMinutes,
        dailyTiersOverride: List<OvertimeTier>? = null,
    ): List<Pair<Int, Double>> {
        if (net <= 0) return emptyList()

        val rules = resolved.rules
        val policy = resolved.stackingPolicy
        val dailyTiers = dailyTiersOverride ?: effectiveDailyOvertimeTiers(rules, dailyStandard)

        val bounds = sortedSetOf(1, net + 1)
        bounds += min(net + 1, dailyStandard + 1)
        dailyTiers.forEach { tier ->
            val boundary = tier.afterMinutes + 1
            if (boundary in 2..net) bounds += boundary
        }
        val weeklyStandardBoundary = rules.weeklyStandardMinutes - priorWeekMinutes + 1
        if (weeklyStandardBoundary in 2..net) bounds += weeklyStandardBoundary
        rules.weeklyOvertimeTiers.forEach { tier ->
            val boundary = tier.afterMinutes - priorWeekMinutes + 1
            if (boundary in 2..net) bounds += boundary
        }

        val sortedBounds = bounds.sorted()
        val segments = mutableListOf<Pair<Int, Double>>()

        for (i in 0 until sortedBounds.size - 1) {
            val segmentEnd = sortedBounds[i + 1] - 1
            val length = sortedBounds[i + 1] - sortedBounds[i]
            if (length <= 0) continue

            val dailyMult = dailyMultiplier(segmentEnd, rules, dailyStandard, dailyTiers)
            val weeklyMult = weeklyMultiplier(priorWeekMinutes + segmentEnd, rules)
            val effective = combineRates(dailyMult, weeklyMult, policy)

            if (segments.isNotEmpty() && segments.last().second == effective) {
                val last = segments.removeAt(segments.lastIndex)
                segments += (last.first + length) to effective
            } else {
                segments += length to effective
            }
        }

        return segments
    }

    /**
     * Rate for the [minuteInShift]-th minute of a shift under the daily ladder.
     *
     * **The standard is the trigger; the first tier's rate applies from it.** When a
     * custom ladder's first tier starts later than [dailyStandard] — say standard 480
     * with a first tier at `afterMinutes = 540` — minutes 481..540 fall in a gap that
     * no tier claims, and the last line pays them at the lowest tier's rate rather than
     * 1.0. That is deliberate, and the alternative was considered and rejected:
     *
     * - Every shipped preset sets the first tier's `afterMinutes` **equal to** the
     *   standard (`RegionPresets.kt`), and [effectiveDailyOvertimeTiers] shifts the whole
     *   ladder by the same delta when a night or day-before-rest standard moves it, so
     *   the gap is empty in every configuration the app ships. Only a hand-built ladder
     *   opens it.
     * - Paying 1.0 through the gap would break the invariant the reports depend on: the
     *   daily standard is what [com.elmtrackr.app.domain.compensation.ShiftClassifier]
     *   and [MonthlyReportBuilder] use to call a minute overtime, so an hour reported as
     *   overtime would be paid at straight time. Hours and money would disagree again —
     *   exactly the defect class Wave B closed.
     *
     * Someone who wants those minutes at straight time is describing a longer standard,
     * and should raise `dailyStandardMinutes` to where the tier starts.
     */
    internal fun dailyMultiplier(
        minuteInShift: Int,
        rules: CompensationRules,
        dailyStandard: Int = rules.dailyStandardMinutes,
        dailyTiers: List<OvertimeTier> = effectiveDailyOvertimeTiers(rules, dailyStandard),
    ): Double {
        if (dailyTiers.isEmpty()) return 1.0
        if (minuteInShift <= dailyStandard) return 1.0
        val matched = overtimeTierMultiplier(minuteInShift, dailyTiers)
        if (matched > 1.0) return matched
        return dailyTiers.minBy { it.afterMinutes }.multiplier
    }

    /**
     * Rate for the [minuteInWeek]-th minute of a pay week under the weekly ladder.
     * Same gap rule as [dailyMultiplier], for the same reason: the weekly standard is
     * the trigger, and the lowest tier's rate applies from it.
     */
    internal fun weeklyMultiplier(minuteInWeek: Int, rules: CompensationRules): Double {
        if (rules.weeklyOvertimeTiers.isEmpty()) return 1.0
        if (minuteInWeek <= rules.weeklyStandardMinutes) return 1.0
        val matched = overtimeTierMultiplier(minuteInWeek, rules.weeklyOvertimeTiers)
        if (matched > 1.0) return matched
        return rules.weeklyOvertimeTiers.minBy { it.afterMinutes }.multiplier
    }

    internal fun overtimeTierMultiplier(cumulativeMinutes: Int, tiers: List<OvertimeTier>): Double {
        val applicable = tiers.filter { cumulativeMinutes > it.afterMinutes }.maxByOrNull { it.afterMinutes }
        return applicable?.multiplier ?: 1.0
    }

    internal fun combineRates(daily: Double, weekly: Double, policy: StackingPolicy): Double =
        PremiumStacking.combinePolicy(daily, weekly, policy)

    /**
     * Fraction of the shift's net minutes the night uplift applies to — 1.0 for
     * "entire_shift", window/net otherwise, and 0.0 when night pay is inactive for
     * this shift.
     *
     * The fraction lets the tier loop apply the uplift proportionally: paying a whole
     * tier at the stacked rate would overcharge a shift that only partly overlaps the
     * night window, and attributing the whole shift's premium to every tier corrupted
     * the category split (a bucket could go negative while "night pay" showed several
     * times the real premium).
     *
     * Resolved once per shift rather than once per tier. It is a property of the shift
     * — nothing in it varies by bracket — but it used to be recomputed inside the tier
     * loop, and [countNightMinutes] walks the shift a minute at a time: a four-bracket
     * shift ran that walk four times, and the Israeli path ran it once per classified
     * segment.
     */
    internal fun nightFractionFor(
        resolved: ResolvedCompensation,
        shift: Shift,
        zone: java.time.ZoneId,
    ): Double {
        val rules = resolved.rules
        if (!rules.nightEnabled || shift.endTime == null) return 0.0
        if (rules.nightMultiplier - 1.0 <= 0) return 0.0

        val net = ShiftDurationCalculator.netMinutes(shift)?.takeIf { it > 0 } ?: return 0.0
        val nightMinutes = if (rules.nightApplyTo == "entire_shift") {
            net
        } else {
            // Scaled, because this is a share of *paid* minutes and `net` below is
            // already net of the break.
            paidNightMinutes(shift, rules.nightStartTime, rules.nightEndTime, zone)
        }
        if (nightMinutes <= 0) return 0.0
        return (nightMinutes.toDouble() / net).coerceIn(0.0, 1.0)
    }

    /**
     * [baseRate] with the night multiplier stacked onto it under the applicable
     * policy. Only meaningful where [nightFractionFor] is above zero; callers keep the
     * base rate unchanged otherwise, which is what the old single function returned
     * from each of its early exits.
     */
    private fun nightStackedRate(
        baseRate: Double,
        resolved: ResolvedCompensation,
        premiumType: PremiumType?,
    ): Double = if (premiumType != null) {
        PremiumStacking.combine(baseRate, resolved.rules.nightMultiplier, premiumType)
    } else {
        PremiumStacking.combinePolicy(resolved.rules.nightMultiplier, baseRate, resolved.stackingPolicy)
    }

    /**
     * Wall-clock minutes of the shift inside the night window, unscaled.
     *
     * This is the figure the "is this a night shift" test needs, and it used to be
     * the break-scaled one. That test asks a question about the clock — did at
     * least two hours of this shift fall between 22:00 and 06:00 — and the break's
     * position is not recorded, so scaling the count by the break ratio before
     * answering it was arbitrary. It also had a sharp edge: a shift with 130
     * wall-clock night minutes and a 60-minute break counted 115, dropped below
     * the 120-minute threshold, and lost the shortened night standard along with
     * every overtime minute that standard produced — far more than the break
     * itself was worth.
     *
     * Walked a minute at a time because the window is a local wall-clock range, so a
     * shift crossing midnight or a DST change cannot be measured by subtracting two
     * instants. What the walk needs from each minute is only its minute-of-day, and
     * [ZoneMinutes] derives that arithmetically wherever the zone holds one offset
     * across the shift — which removes an `Instant.atZone` allocation and zone-rules
     * lookup per minute. A shift containing a transition still converts each minute
     * the original way.
     */
    private fun countNightMinutes(
        shift: Shift,
        nightStart: String,
        nightEnd: String,
        zone: java.time.ZoneId,
    ): Int {
        val end = shift.endTime ?: return 0
        val ns = parseTimeToMinutes(nightStart, DEFAULT_NIGHT_START_MINUTES)
        val ne = parseTimeToMinutes(nightEnd, DEFAULT_NIGHT_END_MINUTES)
        val startMs = shift.startTime.toEpochMilli()
        val endMs = end.toEpochMilli()
        if (endMs <= startMs) return 0

        var nightMs = 0L
        val step = 60_000L
        val fixedOffset = ZoneMinutes.hasFixedOffset(zone, startMs, endMs - 1)
        val offsetSeconds = if (fixedOffset) ZoneMinutes.offsetSecondsAt(zone, startMs) else 0L
        var t = startMs
        while (t < endMs) {
            val mins = if (fixedOffset) {
                ZoneMinutes.minuteOfDay(t, offsetSeconds)
            } else {
                val localTime = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalTime()
                localTime.hour * 60 + localTime.minute
            }
            val inNight = if (ns > ne) mins >= ns || mins < ne else mins in ns until ne
            if (inNight) nightMs += step
            t += step
        }
        return (nightMs / 60_000L).toInt()
    }

    /**
     * The night minutes a *premium* is paid on: the wall-clock count above,
     * reduced in the same proportion as the rest of the shift's paid time.
     *
     * Kept scaled, unlike the classification test. Here the question is how much
     * of the paid time was at night, and the paid time is already net of the
     * break — so counting unscaled night minutes against a net denominator would
     * pay a night premium on minutes the shift is not paid for at all.
     */
    private fun paidNightMinutes(
        shift: Shift,
        nightStart: String,
        nightEnd: String,
        zone: java.time.ZoneId,
    ): Int {
        val end = shift.endTime ?: return 0
        val grossMinutes = (end.toEpochMilli() - shift.startTime.toEpochMilli()) / 60_000.0
        if (grossMinutes <= 0) return 0
        val ratio = maxOf(0.0, grossMinutes - shift.breakMinutes) / grossMinutes
        return (countNightMinutes(shift, nightStart, nightEnd, zone) * ratio).toInt()
    }

    /**
     * The night window's ends, falling back to the preset defaults.
     *
     * A malformed value used to throw from inside the pay calculation. 22:00 and
     * 06:00 are the shipped defaults, so a profile whose window cannot be read is
     * measured against the ordinary night rather than crashing the report it
     * appears in.
     */
    private fun parseTimeToMinutes(time: String, fallbackMinutes: Int): Int =
        WallClockTime.parseMinutesOfDayOrNull(time) ?: fallbackMinutes

    data class MonthlyPaySummary(
        val totalGross: Double,
        val regularGross: Double,
        val overtimeGross: Double,
        val specialGross: Double,
        val weekendGross: Double = 0.0,
        val holidayGross: Double = 0.0,
        val nightGross: Double = 0.0,
        val deductionsGross: Double = 0.0,
        val netGross: Double = totalGross,
        /**
         * The currency every figure above is in, or null when that question has no
         * single answer — an empty month, or one worked in more than one currency.
         *
         * Nullable rather than defaulted so a caller cannot render a made-up
         * currency. [grossByCurrency] is the honest breakdown when this is null.
         */
        val currencyCode: String? = null,
        /** Gross per currency. Sums to [totalGross] only when there is one. */
        val grossByCurrency: Map<String, Double> = emptyMap(),
    )
}

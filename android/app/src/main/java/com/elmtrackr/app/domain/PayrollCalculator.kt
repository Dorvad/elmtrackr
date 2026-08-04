package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.COMPENSATION_ESTIMATE_NOTE
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.compensation.IsraeliCompensationEngine
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.OvertimeTier
import com.elmtrackr.app.domain.model.PayBracket
import com.elmtrackr.app.domain.model.ResolvedCompensation
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.ShiftPayBreakdown
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.domain.premium.PremiumStacking
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.time.WorkTimezone
import kotlin.math.min

/**
 * Compensation estimation based on user-configured profiles.
 * ElmTrackr does not provide legally compliant payroll calculations.
 */
object PayrollCalculator {

    internal data class Tier(val label: String, val capMinutes: Int, val rate: Double)

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

        val brackets = segments.map { segment ->
            val (nightStackedRate, nightFraction) = applyNightPremium(
                segment.multiplier,
                resolved,
                shift,
                zone,
                shiftPremiumType,
            )
            val blendedRate = segment.multiplier + nightFraction * (nightStackedRate - segment.multiplier)
            val amount = segment.minutes * ratePerMinute * blendedRate
            val nightPremium =
                segment.minutes * ratePerMinute * nightFraction * (nightStackedRate - segment.multiplier)
            when {
                segment.label.contains("Premium", ignoreCase = true) -> holidayGross += amount - nightPremium
                segment.isWeeklyRest && segment.bucket == IsraeliCompensationEngine.OvertimeBucket.REGULAR ->
                    weekendGross += amount - nightPremium
                segment.isWeeklyRest && segment.bucket != IsraeliCompensationEngine.OvertimeBucket.REGULAR ->
                    overtimeGross += amount - nightPremium
                segment.isWeeklyOvertime -> overtimeGross += amount - nightPremium
                segment.isDailyOvertime -> overtimeGross += amount - nightPremium
                segment.bucket == IsraeliCompensationEngine.OvertimeBucket.REGULAR ->
                    regularGross += amount - nightPremium
                else -> overtimeGross += amount - nightPremium
            }
            nightGross += nightPremium
            PayBracket(segment.label, segment.minutes, blendedRate, amount)
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
        val net = payableNetMinutes(shift, resolved.rules)?.takeIf { it > 0 } ?: return null

        val ratePerMinute = hourlyRate / 60.0
        val zone = WorkTimezone.zoneFor(resolved, settings)
        val startDateStr = shift.startTime.atZone(zone).toLocalDate().toString()
        val weekendDays = resolved.rules.weekendDays
        val startOnWeekend = resolved.rules.weekendEnabled &&
            WeekendRules.isWeekendDate(startDateStr, weekendDays)
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
        val isSpecial = isHoliday || startOnWeekend

        val tiers = buildTiers(
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
        val brackets = mutableListOf<PayBracket>()
        var remaining = net
        var regularGross = 0.0
        var overtimeGross = 0.0
        var weekendGross = 0.0
        var holidayGross = 0.0
        var nightGross = 0.0

        for (tier in tiers) {
            if (remaining <= 0) break
            val mins = if (tier.capMinutes == Int.MAX_VALUE) remaining else minOf(remaining, tier.capMinutes)
            val (nightStackedRate, nightFraction) = applyNightPremium(
                tier.rate,
                resolved,
                shift,
                zone,
                shiftPremium?.premiumType,
            )
            // Night uplift applies to this tier's own minutes, proportional to
            // the share of the shift inside the night window. The blended rate
            // keeps totals exact and every category bucket non-negative.
            val blendedRate = tier.rate + nightFraction * (nightStackedRate - tier.rate)
            val amount = mins * ratePerMinute * blendedRate
            val nightPremium = mins * ratePerMinute * nightFraction * (nightStackedRate - tier.rate)
            brackets += PayBracket(tier.label, mins, blendedRate, amount)

            when {
                tier.label.contains("Premium", ignoreCase = true) -> holidayGross += amount - nightPremium
                tier.label.contains("overtime", ignoreCase = true) -> overtimeGross += amount - nightPremium
                tier.label.contains("Holiday", ignoreCase = true) -> holidayGross += amount - nightPremium
                tier.label.contains("Weekly rest", ignoreCase = true) -> weekendGross += amount - nightPremium
                tier.label.contains("Weekend", ignoreCase = true) -> weekendGross += amount - nightPremium
                tier.label.contains("Overtime", ignoreCase = true) -> overtimeGross += amount - nightPremium
                else -> regularGross += amount - nightPremium
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
                if (rules.overtimeEnabled && rules.dailyOvertimeTiers.isNotEmpty()) {
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
        if (minimum > 0 && net in 1 until minimum) net = minimum
        return net
    }

    fun sumMonthlyPay(
        shifts: List<Shift>,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
        premiumProfiles: List<PremiumProfile> = emptyList(),
    ): MonthlyPaySummary {
        // Employee-only from here down: project shifts contribute no gross, and
        // must not appear in the week accumulation that drives overtime either.
        @Suppress("NAME_SHADOWING") val shifts = shifts.employeePaidOnly()
        var totalGross = 0.0
        var regularGross = 0.0
        var overtimeGross = 0.0
        var specialGross = 0.0
        var weekendGross = 0.0
        var holidayGross = 0.0
        var nightGross = 0.0
        var deductionsGross = 0.0
        var currencyCode = "USD"
        // Fixed deductions are charged once per period, not per shift. Keyed by profile so
        // a month spanning two profiles charges each of their fees once.
        val fixedDeductionsByProfile = mutableMapOf<String, Double>()

        PayWeekMinutes.forEachWithPriorWeekMinutes(shifts, { shift ->
            val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
            WorkTimezone.zoneFor(resolved, settings)
        }) { shift, _ ->
            val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
            val bd = if (resolved.regionCode == RegionCode.IL) {
                IsraeliCompensationEngine.calculateIsraeliShiftPay(
                    shift, shifts, settings, profiles, premiumProfiles,
                )
            } else {
                calculateShiftPayInContext(shift, shifts, settings, profiles, premiumProfiles)
            } ?: return@forEachWithPriorWeekMinutes
            totalGross += bd.totalGross
            regularGross += bd.regularGross
            overtimeGross += bd.overtimeGross
            weekendGross += bd.weekendGross
            holidayGross += bd.holidayGross
            nightGross += bd.nightGross
            deductionsGross += bd.deductionsGross
            specialGross += bd.weekendGross + bd.holidayGross
            currencyCode = bd.currencyCode
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
            currencyCode = currencyCode,
        )
    }

    private const val PERIOD_DEDUCTION_FALLBACK_KEY = "__legacy_settings__"

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
            return listOf(Tier("$pct% — Premium (${it.name})", Int.MAX_VALUE, it.multiplier))
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
                val label = if (rate <= 1.0 + 1e-9) "100% — Regular" else "${(rate * 100).toInt()}% — Overtime"
                Tier(label, minutes, rate)
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
            val restKind = if (isHoliday && !startOnWeekend) "Holiday" else "Weekly rest"
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
                return listOf(Tier("${(rules.holidayMultiplier * 100).toInt()}% — Holiday", Int.MAX_VALUE, rules.holidayMultiplier))
            }
            if (rules.weekendEnabled) {
                return listOf(Tier("${(rules.weekendMultiplier * 100).toInt()}% — Weekend", Int.MAX_VALUE, rules.weekendMultiplier))
            }
        }

        if (!rules.overtimeEnabled || shiftPremium?.premiumType == PremiumType.EXCLUDED_FROM_REGULAR_RATE) {
            return listOf(Tier("100% — Regular", Int.MAX_VALUE, 1.0))
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
            return listOf(Tier("100% — Regular", Int.MAX_VALUE, 1.0))
        }

        return segments.map { (minutes, rate) ->
            val label = if (rate <= 1.0 + 1e-9) "100% — Regular" else "${(rate * 100).toInt()}% — Overtime"
            Tier(label, minutes, rate)
        }
    }

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
                Tier("${(restBase * 100).toInt()}% — $restKind regular", Int.MAX_VALUE, restBase),
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
            Tier(label, minutes, rate)
        }
    }

    /** Minimum minutes inside the night window for a shift to count as night work. */
    internal const val NIGHT_SHIFT_MIN_NIGHT_MINUTES = 120

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
     * Returns the night-stacked rate for a tier plus the fraction of the
     * shift's net minutes that the night uplift applies to (1.0 for
     * "entire_shift", window/net otherwise, 0.0 when night pay is inactive).
     *
     * The fraction lets the tier loop apply the uplift proportionally: paying
     * the whole tier at the stacked rate would overcharge shifts that only
     * partially overlap the night window, and attributing the whole shift's
     * premium to every tier corrupted the category split (a bucket could go
     * negative while "night pay" showed several times the real premium).
     */
    private fun applyNightPremium(
        baseRate: Double,
        resolved: ResolvedCompensation,
        shift: Shift,
        zone: java.time.ZoneId,
        premiumType: PremiumType? = null,
    ): Pair<Double, Double> {
        val rules = resolved.rules
        if (!rules.nightEnabled || shift.endTime == null) return baseRate to 0.0
        val nightDelta = rules.nightMultiplier - 1.0
        if (nightDelta <= 0) return baseRate to 0.0

        val net = ShiftDurationCalculator.netMinutes(shift)?.takeIf { it > 0 } ?: return baseRate to 0.0
        val nightMinutes = if (rules.nightApplyTo == "entire_shift") {
            net
        } else {
            countNightMinutes(shift, rules.nightStartTime, rules.nightEndTime, zone)
        }
        if (nightMinutes <= 0) return baseRate to 0.0

        val effectiveRate = if (premiumType != null) {
            PremiumStacking.combine(baseRate, rules.nightMultiplier, premiumType)
        } else {
            PremiumStacking.combinePolicy(rules.nightMultiplier, baseRate, resolved.stackingPolicy)
        }
        val nightFraction = (nightMinutes.toDouble() / net).coerceIn(0.0, 1.0)
        return effectiveRate to nightFraction
    }

    private fun countNightMinutes(
        shift: Shift,
        nightStart: String,
        nightEnd: String,
        zone: java.time.ZoneId,
    ): Int {
        val end = shift.endTime ?: return 0
        val ns = parseTimeToMinutes(nightStart)
        val ne = parseTimeToMinutes(nightEnd)
        val startMs = shift.startTime.toEpochMilli()
        val endMs = end.toEpochMilli()
        if (endMs <= startMs) return 0

        var nightMs = 0L
        val step = 60_000L
        var t = startMs
        while (t < endMs) {
            val localTime = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalTime()
            val mins = localTime.hour * 60 + localTime.minute
            val inNight = if (ns > ne) mins >= ns || mins < ne else mins in ns until ne
            if (inNight) nightMs += step
            t += step
        }
        val grossMinutes = (endMs - startMs) / 60_000.0
        val netRatio = if (grossMinutes > 0) {
            maxOf(0.0, grossMinutes - shift.breakMinutes) / grossMinutes
        } else 1.0
        return (nightMs / 60_000.0 * netRatio).toInt()
    }

    private fun parseTimeToMinutes(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + (parts.getOrNull(1)?.toInt() ?: 0)
    }

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
        val currencyCode: String = "USD",
    )
}

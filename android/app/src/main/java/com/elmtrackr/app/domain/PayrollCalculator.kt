package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.COMPENSATION_ESTIMATE_NOTE
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.OvertimeTier
import com.elmtrackr.app.domain.model.PayBracket
import com.elmtrackr.app.domain.model.ResolvedCompensation
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.ShiftPayBreakdown
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.time.WorkTimezone

/**
 * Compensation estimation based on user-configured profiles.
 * ElmTrackr does not provide legally compliant payroll calculations.
 */
object PayrollCalculator {

    private data class Tier(val label: String, val capMinutes: Int, val rate: Double)

    fun calculateShiftPay(
        shift: Shift,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
        priorWeekMinutes: Int = 0,
    ): ShiftPayBreakdown? {
        if (shift.endTime == null) return null
        val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
        val hourlyRate = resolved.baseHourlyRate?.takeIf { it > 0 } ?: return null
        val net = ShiftDurationCalculator.netMinutes(shift)?.takeIf { it > 0 } ?: return null

        val ratePerMinute = hourlyRate / 60.0
        val zone = WorkTimezone.zoneFor(resolved, settings)
        val startDateStr = shift.startTime.atZone(zone).toLocalDate().toString()
        val weekendDays = resolved.rules.weekendDays
        val startOnWeekend = resolved.rules.weekendEnabled &&
            WeekendRules.isWeekendDate(startDateStr, weekendDays)
        val isHoliday = resolved.rules.holidayEnabled &&
            resolved.rules.holidayManualSpecialDayEnabled &&
            shift.isSpecialDay
        val isSpecial = isHoliday || startOnWeekend

        val tiers = buildTiers(resolved, isSpecial, net, priorWeekMinutes)
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
            val (effectiveRate, nightPremium) = applyNightPremium(tier.rate, resolved, shift, zone)
            val amount = mins * ratePerMinute * effectiveRate
            brackets += PayBracket(tier.label, mins, effectiveRate, amount)

            when {
                tier.label.contains("Holiday", ignoreCase = true) -> holidayGross += amount - nightPremium
                tier.label.contains("Weekend", ignoreCase = true) -> weekendGross += amount - nightPremium
                tier.label.contains("Overtime", ignoreCase = true) -> overtimeGross += amount - nightPremium
                else -> regularGross += amount - nightPremium
            }
            nightGross += nightPremium
            remaining -= mins
        }

        val totalGross = brackets.sumOf { it.amount }
        var deductionsGross = 0.0
        val rules = resolved.rules
        if (rules.deductionsEnabled) {
            deductionsGross = when (rules.deductionsMode) {
                "percentage" -> totalGross * (rules.deductionsPercentage / 100.0)
                "fixed" -> rules.deductionsFixedAmount
                else -> 0.0
            }
        }

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
    ): ShiftPayBreakdown? {
        val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
        val zone = WorkTimezone.zoneFor(resolved, settings)
        val prior = PayWeekMinutes.priorMinutesBefore(shift, allCompletedShifts, zone)
        return calculateShiftPay(shift, settings, profiles, prior)
    }

    fun sumMonthlyPay(
        shifts: List<Shift>,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
    ): MonthlyPaySummary {
        var totalGross = 0.0
        var regularGross = 0.0
        var overtimeGross = 0.0
        var specialGross = 0.0
        var weekendGross = 0.0
        var holidayGross = 0.0
        var nightGross = 0.0
        var deductionsGross = 0.0
        var netGross = 0.0
        var currencyCode = "USD"

        PayWeekMinutes.forEachWithPriorWeekMinutes(shifts, { shift ->
            val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
            WorkTimezone.zoneFor(resolved, settings)
        }) { shift, prior ->
            val bd = calculateShiftPay(shift, settings, profiles, prior) ?: return@forEachWithPriorWeekMinutes
            totalGross += bd.totalGross
            regularGross += bd.regularGross
            overtimeGross += bd.overtimeGross
            weekendGross += bd.weekendGross
            holidayGross += bd.holidayGross
            nightGross += bd.nightGross
            deductionsGross += bd.deductionsGross
            netGross += bd.netGross
            specialGross += bd.weekendGross + bd.holidayGross
            currencyCode = bd.currencyCode
        }

        return MonthlyPaySummary(
            totalGross, regularGross, overtimeGross, specialGross,
            weekendGross, holidayGross, nightGross, deductionsGross, netGross, currencyCode,
        )
    }

    private fun buildTiers(
        resolved: ResolvedCompensation,
        isSpecial: Boolean,
        net: Int,
        priorWeekMinutes: Int,
    ): List<Tier> {
        val rules = resolved.rules
        if (isSpecial) {
            rules.holidayTiers?.takeIf { it.isNotEmpty() }?.let { return tiersFromAfterMinutes(it, "Holiday") }
            if (rules.holidayEnabled) {
                return listOf(Tier("${(rules.holidayMultiplier * 100).toInt()}% — Holiday", Int.MAX_VALUE, rules.holidayMultiplier))
            }
            if (rules.weekendEnabled) {
                return listOf(Tier("${(rules.weekendMultiplier * 100).toInt()}% — Weekend", Int.MAX_VALUE, rules.weekendMultiplier))
            }
        }

        if (!rules.overtimeEnabled) {
            return listOf(Tier("100% — Regular", Int.MAX_VALUE, 1.0))
        }

        val segments = buildCombinedRateSegments(resolved, net, priorWeekMinutes)
        if (segments.isEmpty()) {
            return listOf(Tier("100% — Regular", Int.MAX_VALUE, 1.0))
        }

        return segments.map { (minutes, rate) ->
            val label = if (rate <= 1.0 + 1e-9) "100% — Regular" else "${(rate * 100).toInt()}% — Overtime"
            Tier(label, minutes, rate)
        }
    }

    /**
     * Builds consecutive (length, effectiveMultiplier) segments for a shift, combining daily and
     * weekly overtime ladders per [StackingPolicy].
     */
    internal fun buildCombinedRateSegments(
        resolved: ResolvedCompensation,
        net: Int,
        priorWeekMinutes: Int,
    ): List<Pair<Int, Double>> {
        if (net <= 0) return emptyList()

        val rules = resolved.rules
        val policy = resolved.stackingPolicy
        val dailyStandard = rules.dailyStandardMinutes

        val bounds = sortedSetOf(1, net + 1)
        bounds += min(net + 1, dailyStandard + 1)
        rules.dailyOvertimeTiers.forEach { tier ->
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

            val dailyMult = dailyMultiplier(segmentEnd, rules, dailyStandard)
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
    ): Double {
        if (rules.dailyOvertimeTiers.isEmpty()) return 1.0
        if (minuteInShift <= dailyStandard) return 1.0
        val matched = overtimeTierMultiplier(minuteInShift, rules.dailyOvertimeTiers)
        if (matched > 1.0) return matched
        return rules.dailyOvertimeTiers.minBy { it.afterMinutes }.multiplier
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
        when (policy) {
            StackingPolicy.HIGHEST_ONLY -> maxOf(daily, weekly)
            StackingPolicy.ADDITIVE -> 1.0 + maxOf(0.0, daily - 1.0) + maxOf(0.0, weekly - 1.0)
        }

    private fun tiersFromAfterMinutes(tierDefs: List<OvertimeTier>, label: String): List<Tier> {
        val sorted = tierDefs.sortedBy { it.afterMinutes }
        return buildList {
            for (i in sorted.indices) {
                val tier = sorted[i]
                val nextAfter = sorted.getOrNull(i + 1)?.afterMinutes ?: Int.MAX_VALUE
                val cap = if (nextAfter == Int.MAX_VALUE) Int.MAX_VALUE else nextAfter - tier.afterMinutes
                add(Tier("${(tier.multiplier * 100).toInt()}% — $label", cap, tier.multiplier))
            }
        }
    }

    private fun applyNightPremium(
        baseRate: Double,
        resolved: ResolvedCompensation,
        shift: Shift,
        zone: java.time.ZoneId,
    ): Pair<Double, Double> {
        val rules = resolved.rules
        if (!rules.nightEnabled || shift.endTime == null) return baseRate to 0.0
        val nightDelta = rules.nightMultiplier - 1.0
        if (nightDelta <= 0) return baseRate to 0.0

        val net = ShiftDurationCalculator.netMinutes(shift) ?: return baseRate to 0.0
        val nightMinutes = if (rules.nightApplyTo == "entire_shift") {
            net
        } else {
            countNightMinutes(shift, rules.nightStartTime, rules.nightEndTime, zone)
        }
        if (nightMinutes <= 0) return baseRate to 0.0

        val effectiveRate = when (resolved.stackingPolicy) {
            StackingPolicy.ADDITIVE -> baseRate + nightDelta
            StackingPolicy.HIGHEST_ONLY -> maxOf(baseRate, rules.nightMultiplier)
        }
        val hourlyRate = resolved.baseHourlyRate ?: return baseRate to 0.0
        val premium = nightMinutes * (hourlyRate / 60.0) * (effectiveRate - baseRate)
        return effectiveRate to premium
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

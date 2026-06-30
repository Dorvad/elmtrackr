package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.COMPENSATION_ESTIMATE_NOTE
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.CompensationProfile
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

        val tiers = buildTiers(resolved, isSpecial, net)
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

        for (shift in shifts) {
            val bd = calculateShiftPay(shift, settings, profiles) ?: continue
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

    private fun buildTiers(resolved: ResolvedCompensation, isSpecial: Boolean, net: Int): List<Tier> {
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

        val tiers = mutableListOf(Tier("100% — Regular", rules.dailyStandardMinutes, 1.0))
        if (rules.overtimeEnabled) {
            val sorted = rules.dailyOvertimeTiers.sortedBy { it.afterMinutes }
            for (i in sorted.indices) {
                val tier = sorted[i]
                val nextAfter = sorted.getOrNull(i + 1)?.afterMinutes ?: Int.MAX_VALUE
                val cap = if (nextAfter == Int.MAX_VALUE) Int.MAX_VALUE else nextAfter - tier.afterMinutes
                tiers += Tier("${(tier.multiplier * 100).toInt()}% — Overtime", cap, tier.multiplier)
            }
        }
        if (
            tiers.size == 1 &&
            net > rules.dailyStandardMinutes &&
            rules.dailyOvertimeTiers.isNotEmpty()
        ) {
            tiers += Tier("150% — Overtime", Int.MAX_VALUE, 1.5)
        }
        return tiers
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

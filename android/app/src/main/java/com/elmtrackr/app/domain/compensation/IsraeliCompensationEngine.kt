package com.elmtrackr.app.domain.compensation

import com.elmtrackr.app.domain.PayWeekMinutes
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.WeekendRules
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.PayBracket
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.ResolvedCompensation
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.ShiftPayBreakdown
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.time.WorkTimezone
import com.elmtrackr.app.domain.toJsDay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.min

/**
 * Israeli-style compensation: weekly-rest premium is separate from overtime.
 * Weekly overtime applies only after [CompensationRules.weeklyStandardMinutes] of
 * accumulated regular paid hours in the ISO pay week — not because a shift falls
 * on Friday night or Shabbat.
 */
object IsraeliCompensationEngine {

    private const val WEEKLY_OT_FIRST_TIER_MINUTES = 120

    enum class OvertimeBucket { REGULAR, OT_FIRST_TWO, OT_ADDITIONAL }

    data class WeekPayState(
        val weeklyRegularMinutes: Int = 0,
        val weeklyOvertimeMinutes: Int = 0,
    )

    data class ClassifiedPaySegment(
        val minutes: Int,
        val multiplier: Double,
        val label: String,
        val isWeeklyRest: Boolean,
        val isDailyOvertime: Boolean,
        val isWeeklyOvertime: Boolean,
        val bucket: OvertimeBucket,
    )

    fun getWeeklyRegularMinutesBeforeShift(
        currentShift: Shift,
        allShiftsInWeek: List<Shift>,
        resolved: ResolvedCompensation,
        zone: ZoneId,
        settings: UserSettings,
        profiles: List<CompensationProfile>,
        premiumProfiles: List<PremiumProfile>,
    ): Int = weekStateBeforeShift(
        currentShift,
        allShiftsInWeek,
        resolved,
        zone,
        settings,
        profiles,
        premiumProfiles,
    ).weeklyRegularMinutes

    fun classifyShiftSegments(
        shift: Shift,
        weeklyRegularMinutesBefore: Int,
        weeklyOvertimeMinutesBefore: Int,
        resolved: ResolvedCompensation,
        zone: ZoneId,
        premiumProfiles: List<PremiumProfile> = emptyList(),
    ): List<ClassifiedPaySegment> {
        if (shift.endTime == null) return emptyList()
        val shiftPremium = shift.premiumProfileId?.let { id ->
            premiumProfiles.firstOrNull { it.id == id || it.remoteId == id }
        }
        shiftPremium?.let { premium ->
            val net = PayrollCalculator.payableNetMinutes(shift, resolved.rules) ?: return emptyList()
            val pct = (premium.multiplier * 100).toInt()
            return listOf(
                ClassifiedPaySegment(
                    minutes = net,
                    multiplier = premium.multiplier,
                    label = "$pct% — Premium (${premium.name})",
                    isWeeklyRest = false,
                    isDailyOvertime = false,
                    isWeeklyOvertime = false,
                    bucket = OvertimeBucket.REGULAR,
                ),
            )
        }

        val rules = resolved.rules
        if (!rules.overtimeEnabled) {
            return flatRestOrRegularSegments(shift, resolved, zone)
        }

        return classifyOvertimeSegments(
            shift = shift,
            rules = rules,
            zone = zone,
            weeklyRegularMinutesBefore = weeklyRegularMinutesBefore,
            weeklyOvertimeMinutesBefore = weeklyOvertimeMinutesBefore,
            stackingPolicy = resolved.stackingPolicy,
            manualHoliday = shift.isSpecialDay && rules.holidayManualSpecialDayEnabled,
        )
    }

    fun calculateIsraeliShiftPay(
        shift: Shift,
        allShiftsInWeek: List<Shift>,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
        premiumProfiles: List<PremiumProfile> = emptyList(),
    ): ShiftPayBreakdown? {
        if (shift.endTime == null) return null
        val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
        val hourlyRate = resolved.baseHourlyRate?.takeIf { it > 0 } ?: return null
        val zone = WorkTimezone.zoneFor(resolved, settings)
        val weekState = weekStateBeforeShift(shift, allShiftsInWeek, resolved, zone, settings, profiles, premiumProfiles)
        val segments = classifyShiftSegments(
            shift = shift,
            weeklyRegularMinutesBefore = weekState.weeklyRegularMinutes,
            weeklyOvertimeMinutesBefore = weekState.weeklyOvertimeMinutes,
            resolved = resolved,
            zone = zone,
            premiumProfiles = premiumProfiles,
        )
        if (segments.isEmpty()) return null

        val ratePerMinute = hourlyRate / 60.0
        var regularGross = 0.0
        var overtimeGross = 0.0
        var weekendGross = 0.0
        var holidayGross = 0.0

        val brackets = segments.map { segment ->
            val amount = segment.minutes * ratePerMinute * segment.multiplier
            when {
                segment.label.contains("Premium", ignoreCase = true) -> holidayGross += amount
                segment.isWeeklyRest && segment.bucket == OvertimeBucket.REGULAR -> weekendGross += amount
                segment.isWeeklyRest && segment.bucket != OvertimeBucket.REGULAR -> overtimeGross += amount
                segment.isWeeklyOvertime -> overtimeGross += amount
                segment.isDailyOvertime -> overtimeGross += amount
                segment.bucket == OvertimeBucket.REGULAR -> regularGross += amount
                else -> overtimeGross += amount
            }
            PayBracket(segment.label, segment.minutes, segment.multiplier, amount)
        }

        val isSpecial = segments.any { it.isWeeklyRest } ||
            (shift.isSpecialDay && resolved.rules.holidayManualSpecialDayEnabled)

        return ShiftPayBreakdown(
            brackets = brackets,
            totalGross = brackets.sumOf { it.amount },
            regularGross = regularGross,
            overtimeGross = overtimeGross,
            weekendGross = weekendGross,
            holidayGross = holidayGross,
            nightGross = 0.0,
            deductionsGross = 0.0,
            netGross = brackets.sumOf { it.amount },
            isSpecial = isSpecial,
            profileId = resolved.profileId,
            profileName = resolved.profileName,
            currencyCode = resolved.currencyCode,
            disclaimer = COMPENSATION_ESTIMATE_NOTE,
        )
    }

    internal fun weekStateBeforeShift(
        currentShift: Shift,
        allShiftsInWeek: List<Shift>,
        resolved: ResolvedCompensation,
        zone: ZoneId,
        settings: UserSettings,
        profiles: List<CompensationProfile>,
        premiumProfiles: List<PremiumProfile>,
    ): WeekPayState {
        val weekStartDay = resolved.rules.weekStartDay
        val weekStart = PayWeekMinutes.weekStart(currentShift, zone, weekStartDay)
        val priorShifts = allShiftsInWeek
            .asSequence()
            .filter { it.id != currentShift.id && it.endTime != null }
            .filter { PayWeekMinutes.weekStart(it, zone, weekStartDay) == weekStart }
            .filter { it.startTime.isBefore(currentShift.startTime) }
            .sortedBy { it.startTime }
            .toList()

        var state = WeekPayState()
        for (prior in priorShifts) {
            val priorResolved = CompensationResolver.resolveShiftCompensation(prior, settings, profiles)
            val segments = classifyShiftSegments(
                shift = prior,
                weeklyRegularMinutesBefore = state.weeklyRegularMinutes,
                weeklyOvertimeMinutesBefore = state.weeklyOvertimeMinutes,
                resolved = priorResolved,
                zone = zone,
                premiumProfiles = premiumProfiles,
            )
            state = advanceWeekState(state, segments)
        }
        return state
    }

    internal fun advanceWeekState(
        state: WeekPayState,
        segments: List<ClassifiedPaySegment>,
    ): WeekPayState {
        var weeklyRegular = state.weeklyRegularMinutes
        var weeklyOt = state.weeklyOvertimeMinutes
        for (segment in segments) {
            when {
                segment.bucket == OvertimeBucket.REGULAR -> weeklyRegular += segment.minutes
                segment.isWeeklyOvertime -> weeklyOt += segment.minutes
            }
        }
        return WeekPayState(weeklyRegular, weeklyOt)
    }

    private fun flatRestOrRegularSegments(
        shift: Shift,
        resolved: ResolvedCompensation,
        zone: ZoneId,
    ): List<ClassifiedPaySegment> {
        val net = PayrollCalculator.payableNetMinutes(shift, resolved.rules) ?: return emptyList()
        val rules = resolved.rules
        val startDate = shift.startTime.atZone(zone).toLocalDate().toString()
        val onWeekend = rules.weekendEnabled && WeekendRules.isWeekendDate(startDate, rules.weekendDays)
        val manualHoliday = shift.isSpecialDay && rules.holidayManualSpecialDayEnabled
        return if (onWeekend || manualHoliday) {
            val mult = when {
                manualHoliday && rules.holidayEnabled -> rules.holidayMultiplier
                onWeekend && rules.weekendEnabled -> rules.weekendMultiplier
                else -> 1.0
            }
            listOf(
                ClassifiedPaySegment(
                    minutes = net,
                    multiplier = mult,
                    label = "${(mult * 100).toInt()}% — Weekly rest regular",
                    isWeeklyRest = true,
                    isDailyOvertime = false,
                    isWeeklyOvertime = false,
                    bucket = OvertimeBucket.REGULAR,
                ),
            )
        } else {
            listOf(
                ClassifiedPaySegment(
                    minutes = net,
                    multiplier = 1.0,
                    label = "100% — Regular",
                    isWeeklyRest = false,
                    isDailyOvertime = false,
                    isWeeklyOvertime = false,
                    bucket = OvertimeBucket.REGULAR,
                ),
            )
        }
    }

    private fun classifyOvertimeSegments(
        shift: Shift,
        rules: CompensationRules,
        zone: ZoneId,
        weeklyRegularMinutesBefore: Int,
        weeklyOvertimeMinutesBefore: Int,
        stackingPolicy: StackingPolicy,
        manualHoliday: Boolean,
    ): List<ClassifiedPaySegment> {
        val net = PayrollCalculator.payableNetMinutes(shift, rules) ?: return emptyList()
        if (net <= 0) return emptyList()

        val startMs = shift.startTime.toEpochMilli()
        val endMs = shift.endTime!!.toEpochMilli()
        val grossMinutes = ((endMs - startMs) / 60_000.0).coerceAtLeast(1.0)
        val breakRatio = net / grossMinutes

        var weeklyRegularAccum = weeklyRegularMinutesBefore
        var weeklyOtAccum = weeklyOvertimeMinutesBefore
        // A shift is a single workday even when it crosses local midnight — the
        // daily overtime clock keeps running for the whole shift.
        var dailyMinutesInDay = 0
        var dailyOtMinutesInDay = 0

        // Night work is a property of the whole shift (≥2 h inside the night window
        // makes the entire workday a shortened night workday), not of each minute.
        val isNightShift = PayrollCalculator.isNightWorkShift(shift, rules, zone)

        val rawSegments = mutableListOf<ClassifiedPaySegment>()

        for (minuteIndex in 0 until net) {
            val grossOffset = minuteIndex / breakRatio
            val instantMs = startMs + (grossOffset * 60_000).toLong()
            val zdt = Instant.ofEpochMilli(instantMs).atZone(zone)

            val isWeeklyRest = isWeeklyRestAt(zdt, rules, manualHoliday)
            val dailyStandard = dailyStandardAt(zdt, rules, isWeeklyRest, isNightShift)
            val isDailyOt = dailyMinutesInDay >= dailyStandard
            val isWeeklyOt = weeklyRegularAccum >= rules.weeklyStandardMinutes

            val dailyMult = if (isDailyOt) {
                dailyOtMultiplier(dailyOtMinutesInDay, rules, dailyStandard)
            } else {
                1.0
            }
            val weeklyMult = if (isWeeklyOt) {
                weeklyOtMultiplier(weeklyOtAccum, rules)
            } else {
                1.0
            }
            val otMult = PayrollCalculator.combineRates(dailyMult, weeklyMult, stackingPolicy)
            val bucket = when {
                otMult <= 1.0 + 1e-9 -> OvertimeBucket.REGULAR
                isWeeklyOt && (!isDailyOt || weeklyMult >= dailyMult) ->
                    weeklyOtBucket(weeklyOtAccum)
                isDailyOt ->
                    dailyOtBucket(dailyOtMinutesInDay)
                else -> OvertimeBucket.OT_ADDITIONAL
            }
            val multiplier = payMultiplier(isWeeklyRest, bucket)
            val label = payLabel(
                isWeeklyRest = isWeeklyRest,
                bucket = bucket,
                isDailyOvertime = isDailyOt && otMult > 1.0 + 1e-9,
                isWeeklyOvertime = isWeeklyOt && otMult > 1.0 + 1e-9,
                multiplier = multiplier,
            )

            rawSegments += ClassifiedPaySegment(
                minutes = 1,
                multiplier = multiplier,
                label = label,
                isWeeklyRest = isWeeklyRest,
                isDailyOvertime = isDailyOt && otMult > 1.0 + 1e-9,
                isWeeklyOvertime = isWeeklyOt && otMult > 1.0 + 1e-9,
                bucket = bucket,
            )

            dailyMinutesInDay++
            when (bucket) {
                OvertimeBucket.REGULAR -> weeklyRegularAccum++
                OvertimeBucket.OT_FIRST_TWO, OvertimeBucket.OT_ADDITIONAL -> {
                    dailyOtMinutesInDay++
                    if (isWeeklyOt) weeklyOtAccum++
                }
            }
        }

        return mergeAdjacentSegments(rawSegments)
    }

    private fun mergeAdjacentSegments(segments: List<ClassifiedPaySegment>): List<ClassifiedPaySegment> {
        if (segments.isEmpty()) return emptyList()
        val merged = mutableListOf<ClassifiedPaySegment>()
        var current = segments.first()
        for (next in segments.drop(1)) {
            if (next.label == current.label && next.multiplier == current.multiplier) {
                current = current.copy(minutes = current.minutes + next.minutes)
            } else {
                merged += current
                current = next
            }
        }
        merged += current
        return merged
    }

    internal fun isWeeklyRestAt(
        zdt: ZonedDateTime,
        rules: CompensationRules,
        manualHoliday: Boolean,
    ): Boolean {
        if (manualHoliday && rules.holidayEnabled) return true
        if (!rules.weekendEnabled) return false
        val jsDay = zdt.dayOfWeek.toJsDay()
        if (jsDay !in rules.weekendDays) return false
        val restStart = rules.weeklyRestStartTime
        if (jsDay == 5 && restStart != null) {
            return timeToMinutes(zdt) >= parseTimeToMinutes(restStart)
        }
        return true
    }

    internal fun dailyStandardAt(
        zdt: ZonedDateTime,
        rules: CompensationRules,
        isWeeklyRest: Boolean,
        isNightShift: Boolean,
    ): Int {
        if (isNightShift) {
            return rules.nightDailyStandardMinutes ?: rules.dailyStandardMinutes
        }
        if (!isWeeklyRest && isDayBeforeRestAt(zdt, rules)) {
            return rules.dayBeforeRestDailyStandardMinutes ?: rules.dailyStandardMinutes
        }
        return rules.dailyStandardMinutes
    }

    internal fun isDayBeforeRestAt(zdt: ZonedDateTime, rules: CompensationRules): Boolean {
        if (rules.dayBeforeRestDailyStandardMinutes == null) return false
        if (5 !in rules.weekendDays) return false
        if (zdt.dayOfWeek.toJsDay() != 5) return false
        val restStart = rules.weeklyRestStartTime ?: return true
        return timeToMinutes(zdt) < parseTimeToMinutes(restStart)
    }

    internal fun isNightAt(zdt: ZonedDateTime, rules: CompensationRules): Boolean {
        if (!rules.nightEnabled) return false
        val current = timeToMinutes(zdt)
        val start = parseTimeToMinutes(rules.nightStartTime)
        val end = parseTimeToMinutes(rules.nightEndTime)
        return if (start > end) current >= start || current < end else current in start until end
    }

    private fun dailyOtBucket(dailyOtMinutesInDay: Int): OvertimeBucket =
        if (dailyOtMinutesInDay < WEEKLY_OT_FIRST_TIER_MINUTES) {
            OvertimeBucket.OT_FIRST_TWO
        } else {
            OvertimeBucket.OT_ADDITIONAL
        }

    private fun weeklyOtBucket(weeklyOtMinutesInWeek: Int): OvertimeBucket =
        if (weeklyOtMinutesInWeek < WEEKLY_OT_FIRST_TIER_MINUTES) {
            OvertimeBucket.OT_FIRST_TWO
        } else {
            OvertimeBucket.OT_ADDITIONAL
        }

    private fun dailyOtMultiplier(
        dailyOtMinutesInDay: Int,
        rules: CompensationRules,
        dailyStandard: Int,
    ): Double {
        val tiers = PayrollCalculator.effectiveDailyOvertimeTiers(rules, dailyStandard)
        if (tiers.isEmpty()) return 1.25
        val minuteInDay = dailyStandard + dailyOtMinutesInDay + 1
        return PayrollCalculator.overtimeTierMultiplier(minuteInDay, tiers)
            .takeIf { it > 1.0 } ?: tiers.minBy { it.afterMinutes }.multiplier
    }

    private fun weeklyOtMultiplier(weeklyOtMinutesInWeek: Int, rules: CompensationRules): Double {
        if (rules.weeklyOvertimeTiers.isEmpty()) {
            return if (weeklyOtMinutesInWeek < WEEKLY_OT_FIRST_TIER_MINUTES) 1.25 else 1.5
        }
        val minuteInWeek = rules.weeklyStandardMinutes + weeklyOtMinutesInWeek + 1
        return PayrollCalculator.overtimeTierMultiplier(minuteInWeek, rules.weeklyOvertimeTiers)
            .takeIf { it > 1.0 } ?: rules.weeklyOvertimeTiers.minBy { it.afterMinutes }.multiplier
    }

    internal fun payMultiplier(isWeeklyRest: Boolean, bucket: OvertimeBucket): Double = when {
        isWeeklyRest -> when (bucket) {
            OvertimeBucket.REGULAR -> 1.5
            OvertimeBucket.OT_FIRST_TWO -> 1.75
            OvertimeBucket.OT_ADDITIONAL -> 2.0
        }
        else -> when (bucket) {
            OvertimeBucket.REGULAR -> 1.0
            OvertimeBucket.OT_FIRST_TWO -> 1.25
            OvertimeBucket.OT_ADDITIONAL -> 1.5
        }
    }

    internal fun payLabel(
        isWeeklyRest: Boolean,
        bucket: OvertimeBucket,
        isDailyOvertime: Boolean,
        isWeeklyOvertime: Boolean,
        multiplier: Double,
    ): String {
        val pct = (multiplier * 100).toInt()
        return when {
            isWeeklyRest && bucket == OvertimeBucket.REGULAR ->
                "$pct% — Weekly rest regular"
            isWeeklyRest && isWeeklyOvertime ->
                "$pct% — Weekly rest overtime"
            isWeeklyRest && isDailyOvertime ->
                "$pct% — Weekly rest daily overtime"
            bucket == OvertimeBucket.REGULAR ->
                "$pct% — Regular"
            isWeeklyOvertime ->
                "$pct% — Weekly overtime"
            isDailyOvertime ->
                "$pct% — Daily overtime"
            else ->
                "$pct% — Overtime"
        }
    }

    private fun timeToMinutes(zdt: ZonedDateTime): Int = zdt.hour * 60 + zdt.minute

    private fun parseTimeToMinutes(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + (parts.getOrNull(1)?.toInt() ?: 0)
    }
}

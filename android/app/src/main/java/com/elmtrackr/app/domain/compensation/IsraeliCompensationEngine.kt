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
import com.elmtrackr.app.domain.time.ZoneMinutes
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
        val shiftPremium = if (shift.forceRegularRate) {
            null
        } else {
            shift.premiumProfileId?.let { id ->
                premiumProfiles.firstOrNull { it.id == id || it.remoteId == id }
            }
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
            manualHoliday = shift.isSpecialDay && !shift.forceRegularRate && rules.holidayManualSpecialDayEnabled,
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
        // Project time is paid by the project's fee, never as wages.
        if (!shift.isEmployeePaid) return null
        val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
        val hourlyRate = resolved.baseHourlyRate?.takeIf { it > 0 } ?: return null
        val zone = WorkTimezone.zoneFor(resolved, settings)
        // Employee-only week context, so project hours cannot consume the weekly
        // regular-hours allowance and push real work into overtime.
        val employeeShiftsInWeek = allShiftsInWeek.filter { it.isEmployeePaid }
        val weekState = weekStateBeforeShift(
            shift, employeeShiftsInWeek, resolved, zone, settings, profiles, premiumProfiles,
        )
        val segments = classifyShiftSegments(
            shift = shift,
            weeklyRegularMinutesBefore = weekState.weeklyRegularMinutes,
            weeklyOvertimeMinutesBefore = weekState.weeklyOvertimeMinutes,
            resolved = resolved,
            zone = zone,
            premiumProfiles = premiumProfiles,
        )
        if (segments.isEmpty()) return null

        // One shared builder with PayrollCalculator rather than a second copy of the
        // same loop. The two copies had drifted: both hardcoded nightGross to zero,
        // and only one honoured forceRegularRate in the manual-holiday check.
        return PayrollCalculator.israeliBreakdownOf(
            segments = segments,
            shift = shift,
            resolved = resolved,
            zone = zone,
            hourlyRate = hourlyRate,
            premiumProfiles = premiumProfiles,
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

    /**
     * The no-overtime path: rest and non-rest runs at their configured rates, with
     * no overtime ladder on top.
     *
     * Rest is decided by [isWeeklyRestAt] — minute by minute, exactly as
     * [classifyOvertimeSegments] decides it. It used to be decided once from the
     * shift's start date via [WeekendRules.isWeekendDate], which is a whole-day test:
     * on the Israeli preset (`weekendDays = [5, 6]`, `weeklyRestStartTime = "17:00"`)
     * that made the *whole* of Friday weekly rest. Turning overtime off then *raised*
     * pay for a Friday morning shift — 08:00–16:00 went from 480 minutes at 1.0× to
     * 480 at 1.5× — and a shift starting before rest began was paid entirely at the
     * rest rate, or entirely at the weekday rate if it started the day before.
     *
     * The rest rate is `maxOf(weekend, holiday)` for the same reason
     * [classifyOvertimeSegments] uses it: a day that is both a calendar rest day and
     * a user-marked special day pays the better of the two, and preferring holiday
     * unconditionally paid the lower rate whenever a user configured a weekend
     * multiplier above their holiday one.
     */
    private fun flatRestOrRegularSegments(
        shift: Shift,
        resolved: ResolvedCompensation,
        zone: ZoneId,
    ): List<ClassifiedPaySegment> {
        val net = PayrollCalculator.payableNetMinutes(shift, resolved.rules) ?: return emptyList()
        if (net <= 0) return emptyList()
        val rules = resolved.rules
        val manualHoliday = shift.isSpecialDay && !shift.forceRegularRate && rules.holidayManualSpecialDayEnabled
        val endMs = shift.endTime?.toEpochMilli() ?: return emptyList()
        val startMs = shift.startTime.toEpochMilli()
        val breakRatio = net / ((endMs - startMs) / 60_000.0).coerceAtLeast(1.0)

        val rawSegments = mutableListOf<ClassifiedPaySegment>()
        forEachPayableMinute(startMs, net, breakRatio, zone) { _, jsDay, minuteOfDay ->
            val isWeeklyRest = isWeeklyRestAt(jsDay, minuteOfDay, rules, manualHoliday)
            val multiplier = if (isWeeklyRest) {
                restBaseFor(jsDay, minuteOfDay, rules, manualHoliday)
            } else {
                1.0
            }
            rawSegments += ClassifiedPaySegment(
                minutes = 1,
                multiplier = multiplier,
                label = payLabel(
                    isWeeklyRest = isWeeklyRest,
                    bucket = OvertimeBucket.REGULAR,
                    isDailyOvertime = false,
                    isWeeklyOvertime = false,
                    multiplier = multiplier,
                ),
                isWeeklyRest = isWeeklyRest,
                isDailyOvertime = false,
                isWeeklyOvertime = false,
                bucket = OvertimeBucket.REGULAR,
            )
        }
        return mergeAdjacentSegments(rawSegments)
    }

    /**
     * The rate a weekly-rest minute is paid at before any overtime premium.
     *
     * Shared by both classification paths so they cannot disagree about what a rest
     * hour is worth.
     */
    private fun restBaseFor(
        jsDay: Int,
        minuteOfDay: Int,
        rules: CompensationRules,
        manualHoliday: Boolean,
    ): Double {
        val calendarRest = isWeeklyRestAt(jsDay, minuteOfDay, rules, manualHoliday = false)
        return maxOf(
            if (calendarRest && rules.weekendEnabled) rules.weekendMultiplier else 1.0,
            if (manualHoliday && rules.holidayEnabled) rules.holidayMultiplier else 1.0,
        )
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
        val endMs = shift.endTime?.toEpochMilli() ?: return emptyList()
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

        forEachPayableMinute(startMs, net, breakRatio, zone) { _, jsDay, minuteOfDay ->
            val isWeeklyRest = isWeeklyRestAt(jsDay, minuteOfDay, rules, manualHoliday)
            val dailyStandard = dailyStandardAt(jsDay, minuteOfDay, rules, isWeeklyRest, isNightShift)
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
            // Derive the segment rate from the configured rules rather than the
            // fixed statutory ladder, so edited weekend/holiday multipliers and
            // custom overtime tiers actually change pay. The IL preset's
            // defaults reproduce the statutory ladder exactly (1.5/1.75/2.0 on
            // rest days, 1.0/1.25/1.5 on weekdays).
            val otExtra = if (bucket == OvertimeBucket.REGULAR) 0.0 else (otMult - 1.0).coerceAtLeast(0.0)
            val multiplier = if (isWeeklyRest) {
                restBaseFor(jsDay, minuteOfDay, rules, manualHoliday) + otExtra
            } else {
                1.0 + otExtra
            }
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

    /**
     * Walks the payable minutes of a shift, handing each one its local day index
     * (0=Sun … 6=Sat) and minute of the local day.
     *
     * Those two numbers are everything the per-minute classifiers read from the clock
     * ([isWeeklyRestAt], [isDayBeforeRestAt]). Building a [ZonedDateTime] to get them
     * was the hot loop in this engine: one allocation and one zone-rules lookup per
     * payable minute, and [weekStateBeforeShift] re-classifies every prior shift of
     * the week for every shift, so a month cost roughly O(shifts² × minutes).
     *
     * A zone's offset is fixed between DST transitions, and inside such a stretch the
     * local calendar can be derived from the instant with integer arithmetic — the
     * same arithmetic `java.time` itself uses (`localSecond = epochSecond + offset`,
     * then floor-divide by the length of a day). When a transition falls inside the
     * interval the walk covers, every minute is converted the original way instead:
     * correctness first, and a shift containing a transition is rare enough that its
     * cost is irrelevant.
     *
     * @param breakRatio payable minutes per wall-clock minute, so minute *i* of pay
     *   maps onto the same instant it did before: `start + (i / breakRatio)` minutes.
     */
    private inline fun forEachPayableMinute(
        startMs: Long,
        net: Int,
        breakRatio: Double,
        zone: ZoneId,
        action: (index: Int, jsDay: Int, minuteOfDay: Int) -> Unit,
    ) {
        if (net <= 0) return
        val lastInstantMs = startMs + (((net - 1) / breakRatio) * 60_000).toLong()

        if (ZoneMinutes.hasFixedOffset(zone, startMs, lastInstantMs)) {
            val offsetSeconds = ZoneMinutes.offsetSecondsAt(zone, startMs)
            for (index in 0 until net) {
                val instantMs = startMs + ((index / breakRatio) * 60_000).toLong()
                action(
                    index,
                    ZoneMinutes.jsDayOfWeek(instantMs, offsetSeconds),
                    ZoneMinutes.minuteOfDay(instantMs, offsetSeconds),
                )
            }
        } else {
            for (index in 0 until net) {
                val instantMs = startMs + ((index / breakRatio) * 60_000).toLong()
                val zdt = Instant.ofEpochMilli(instantMs).atZone(zone)
                action(index, zdt.dayOfWeek.toJsDay(), timeToMinutes(zdt))
            }
        }
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
    ): Boolean = isWeeklyRestAt(zdt.dayOfWeek.toJsDay(), timeToMinutes(zdt), rules, manualHoliday)

    /**
     * The same test against a local day index and minute-of-day rather than a
     * [ZonedDateTime]. Those two numbers are all this rule reads from the clock, and
     * the per-minute walk in [forEachPayableMinute] produces them without allocating.
     */
    internal fun isWeeklyRestAt(
        jsDay: Int,
        minuteOfDay: Int,
        rules: CompensationRules,
        manualHoliday: Boolean,
    ): Boolean {
        if (manualHoliday && rules.holidayEnabled) return true
        if (!rules.weekendEnabled) return false
        if (jsDay !in rules.weekendDays) return false
        val restStart = rules.weeklyRestStartTime
        if (jsDay == 5 && restStart != null) {
            return minuteOfDay >= parseTimeToMinutes(restStart)
        }
        return true
    }

    internal fun dailyStandardAt(
        zdt: ZonedDateTime,
        rules: CompensationRules,
        isWeeklyRest: Boolean,
        isNightShift: Boolean,
    ): Int = dailyStandardAt(
        zdt.dayOfWeek.toJsDay(), timeToMinutes(zdt), rules, isWeeklyRest, isNightShift,
    )

    internal fun dailyStandardAt(
        jsDay: Int,
        minuteOfDay: Int,
        rules: CompensationRules,
        isWeeklyRest: Boolean,
        isNightShift: Boolean,
    ): Int {
        if (isNightShift) {
            return rules.nightDailyStandardMinutes ?: rules.dailyStandardMinutes
        }
        if (!isWeeklyRest && isDayBeforeRestAt(jsDay, minuteOfDay, rules)) {
            return rules.dayBeforeRestDailyStandardMinutes ?: rules.dailyStandardMinutes
        }
        return rules.dailyStandardMinutes
    }

    internal fun isDayBeforeRestAt(zdt: ZonedDateTime, rules: CompensationRules): Boolean =
        isDayBeforeRestAt(zdt.dayOfWeek.toJsDay(), timeToMinutes(zdt), rules)

    internal fun isDayBeforeRestAt(jsDay: Int, minuteOfDay: Int, rules: CompensationRules): Boolean {
        if (rules.dayBeforeRestDailyStandardMinutes == null) return false
        if (5 !in rules.weekendDays) return false
        if (jsDay != 5) return false
        val restStart = rules.weeklyRestStartTime ?: return true
        return minuteOfDay < parseTimeToMinutes(restStart)
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

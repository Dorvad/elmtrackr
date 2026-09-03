package com.elmtrackr.app.domain.compensation

import com.elmtrackr.app.domain.PayWeekMinutes
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.employeePaidOnly
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.PayBucket
import com.elmtrackr.app.domain.model.PayCategory
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.time.WorkTimezone

/**
 * What a shift's paid minutes *are*, independent of what they are worth.
 *
 * The app answered this question twice, in two places, with two different rules.
 * `MonthlyReportBuilder` decided weekend minutes with a whole-day calendar test
 * and overtime against the raw daily standard; the pay engines decided weekly rest
 * minute by minute from `weeklyRestStartTime` and measured overtime against the
 * *effective* standard, which is shorter on a night shift and on the day before
 * rest. Both then described the same shift to the same user — hours on one line,
 * money on the next.
 *
 * On the Israeli preset, which is what a user with no region configured also gets
 * ([CompensationResolver.legacySettingsToResolved] copies it), a Friday 08:00–17:06
 * shift was *paid* as seven hours of ordinary time plus two hours of overtime and
 * six minutes of rest-time overtime — and *reported* as nine hours of weekend work
 * with no overtime at all.
 *
 * This classifier is the single answer. Both engines feed it, it needs no hourly
 * rate — a profile may legitimately have none, and the report still has to count
 * hours — and every consumer reads minutes by [PayBucket] rather than deriving
 * categories of its own.
 */
object ShiftClassifier {

    /**
     * One shift's payable minutes, split by what they are.
     *
     * [payableMinutes] is what the pay rules actually pay for: breaks deducted,
     * rounding applied, the minimum-shift floor honoured. It is not always the same
     * as [workedMinutes], which is the clock time less breaks — rounding and the
     * minimum can make the payable figure larger. Reports that add hours want the
     * payable figure, because that is the one the money is derived from.
     */
    data class ShiftClassification(
        val payableMinutes: Int,
        val workedMinutes: Int,
        val byCategory: Map<PayCategory, Int>,
    ) {
        fun minutesIn(bucket: PayBucket): Int =
            byCategory.entries.sumOf { if (it.key.bucket == bucket) it.value else 0 }

        val regularMinutes: Int get() = minutesIn(PayBucket.REGULAR)
        val overtimeMinutes: Int get() = minutesIn(PayBucket.OVERTIME)

        /**
         * Weekend and holiday minutes together.
         *
         * The report carries one "special" hours figure where the money carries two
         * totals, and `specialGross` is likewise `weekendGross + holidayGross`. Keeping
         * the same pairing is what lets hours and money be compared directly.
         */
        val specialMinutes: Int get() =
            minutesIn(PayBucket.WEEKEND) + minutesIn(PayBucket.HOLIDAY)
    }

    /**
     * Classifies [shift] against the pay week it belongs to.
     *
     * [contextShifts] should be wide enough to cover that week — normally the month
     * plus the tail of the week containing the 1st. Passing only the month is what
     * made a week straddling the 1st start with no accumulated minutes and
     * under-count overtime; passing only the shift itself is valid and simply means
     * "no prior context known".
     *
     * Returns null for an active shift, or for project time, which is paid by the
     * project's fee rather than as wages.
     */
    fun classify(
        shift: Shift,
        contextShifts: List<Shift>,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
        premiumProfiles: List<PremiumProfile> = emptyList(),
    ): ShiftClassification? {
        if (shift.endTime == null) return null
        if (!shift.isEmployeePaid) return null

        val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
        val worked = PayrollCalculator.payableNetMinutes(shift, resolved.rules) ?: return null
        if (worked <= 0) return null

        // Project hours must not consume a weekly allowance that belongs to real
        // employee work, nor count toward a seventh consecutive workday.
        val employeeContext = contextShifts.employeePaidOnly()

        val byCategory = if (resolved.regionCode == RegionCode.IL) {
            israeliCategories(shift, employeeContext, settings, profiles, premiumProfiles, resolved)
        } else {
            genericCategories(shift, employeeContext, settings, profiles, premiumProfiles, resolved)
        } ?: return null

        return ShiftClassification(
            payableMinutes = byCategory.values.sum(),
            workedMinutes = worked,
            byCategory = byCategory,
        )
    }

    /**
     * Classifies a whole month in one pass, rather than a month's worth of
     * from-scratch week rebuilds.
     *
     * [classify] answers for one shift, and to do that the Israeli engine derives
     * the shift's pay week from scratch — classifying every earlier shift of that
     * week, minute by minute, to accumulate the state. Calling it per shift for a
     * month therefore reclassifies the first shift of each week once per later
     * shift in it. Here the week is walked once and every shift takes its answer
     * from that walk.
     *
     * The arithmetic is unchanged: `IsraeliCompensationEngine.classifyWeek` is
     * asserted to return exactly what the per-shift path returns.
     *
     * @return a classification per reported shift, keyed by id. Shifts that
     *   classify to nothing — active ones, project time — are absent, exactly as
     *   [classify] returns null for them.
     */
    fun classifyMonth(
        shifts: List<Shift>,
        contextShifts: List<Shift>,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
        premiumProfiles: List<PremiumProfile> = emptyList(),
    ): Map<String, ShiftClassification> {
        val reported = shifts.employeePaidOnly().filter { it.endTime != null }
        if (reported.isEmpty()) return emptyMap()
        val context = contextShifts.employeePaidOnly().filter { it.endTime != null }
            .ifEmpty { reported }

        val out = HashMap<String, ShiftClassification>(reported.size)
        val reportedIds = reported.mapTo(HashSet()) { it.id }

        // Grouped by resolved region first: only the Israeli engine has a week to
        // walk. A generic-engine shift resolves its own prior minutes and gains
        // nothing from batching, so it keeps the single-shift path.
        val israeli = context.filter {
            CompensationResolver.resolveShiftCompensation(it, settings, profiles)
                .regionCode == RegionCode.IL
        }
        val israeliIds = israeli.mapTo(HashSet()) { it.id }

        israeli
            .groupBy { shift ->
                val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
                val zone = WorkTimezone.zoneFor(resolved, settings)
                zone to PayWeekMinutes.weekStart(shift, zone, resolved.rules.weekStartDay)
            }
            .forEach { (key, weekShifts) ->
                val (zone, _) = key
                val classified = IsraeliCompensationEngine.classifyWeek(
                    weekShifts, zone, settings, profiles, premiumProfiles,
                )
                weekShifts.filter { it.id in reportedIds }.forEach { shift ->
                    val segments = classified[shift.id] ?: return@forEach
                    val worked = PayrollCalculator.payableNetMinutes(
                        shift,
                        CompensationResolver.resolveShiftCompensation(shift, settings, profiles).rules,
                    ) ?: return@forEach
                    val byCategory = segments.groupingBy { it.category }
                        .fold(0) { acc, seg -> acc + seg.minutes }
                    out[shift.id] = ShiftClassification(
                        payableMinutes = byCategory.values.sum(),
                        workedMinutes = worked,
                        byCategory = byCategory,
                    )
                }
            }

        reported.filterNot { it.id in israeliIds }.forEach { shift ->
            classify(shift, context, settings, profiles, premiumProfiles)
                ?.let { out[shift.id] = it }
        }
        return out
    }

    private fun israeliCategories(
        shift: Shift,
        context: List<Shift>,
        settings: UserSettings,
        profiles: List<CompensationProfile>,
        premiumProfiles: List<PremiumProfile>,
        resolved: com.elmtrackr.app.domain.model.ResolvedCompensation,
    ): Map<PayCategory, Int>? {
        val zone = WorkTimezone.zoneFor(resolved, settings)
        val week = IsraeliCompensationEngine.weekStateBeforeShift(
            shift, context, resolved, zone, settings, profiles, premiumProfiles,
        )
        val segments = IsraeliCompensationEngine.classifyShiftSegments(
            shift = shift,
            weeklyRegularMinutesBefore = week.weeklyRegularMinutes,
            weeklyOvertimeMinutesBefore = week.weeklyOvertimeMinutes,
            resolved = resolved,
            zone = zone,
            premiumProfiles = premiumProfiles,
        )
        if (segments.isEmpty()) return null
        return segments.groupingBy { it.category }.fold(0) { acc, s -> acc + s.minutes }
    }

    private fun genericCategories(
        shift: Shift,
        context: List<Shift>,
        settings: UserSettings,
        profiles: List<CompensationProfile>,
        premiumProfiles: List<PremiumProfile>,
        resolved: com.elmtrackr.app.domain.model.ResolvedCompensation,
    ): Map<PayCategory, Int>? {
        val zone = WorkTimezone.zoneFor(resolved, settings)
        val prior = PayrollCalculator.priorStraightTimeMinutesBefore(
            shift, context, settings, profiles, zone, resolved.rules,
        )
        val seventhDay = PayrollCalculator.isSeventhConsecutiveWorkday(
            shift, context, resolved.rules, zone,
        )
        val plan = PayrollCalculator.genericPlan(
            shift, resolved, settings, prior, premiumProfiles, seventhDay,
        ) ?: return null

        // The same walk `calculateGenericShiftPay` performs, minus the money: the
        // last tier carries Int.MAX_VALUE and absorbs whatever is left.
        val byCategory = mutableMapOf<PayCategory, Int>()
        var remaining = plan.net
        for (tier in plan.tiers) {
            if (remaining <= 0) break
            val mins = if (tier.capMinutes == Int.MAX_VALUE) remaining else minOf(remaining, tier.capMinutes)
            byCategory[tier.category] = (byCategory[tier.category] ?: 0) + mins
            remaining -= mins
        }
        return byCategory.takeIf { it.isNotEmpty() }
    }
}

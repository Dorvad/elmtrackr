package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.time.WorkTimezone
import java.time.LocalDate
import java.time.YearMonth

/**
 * Builds the earnings base a leave estimate averages over.
 *
 * A deliberate helper rather than a call straight into the monthly pay summer,
 * for three reasons the brief for this feature is explicit about:
 *
 *  - **Deductions are excluded.** `totalGross` is the figure before deductions;
 *    `netGross` is after. Valuing a leave day off the net figure would price it
 *    below what the day would actually have earned.
 *  - **Project time is excluded.** The pay summer already drops it, and a project
 *    fee is not wages, so it must not raise the average that values a sick day.
 *  - **Paid leave is excluded.** Leave gross lives in its own tables and never
 *    reaches this function, which is what stops an estimate from compounding: a
 *    month containing estimated leave would otherwise raise the average used to
 *    estimate the next one.
 *
 * Only shifts at [workplaceId] are counted, so a user with two jobs averages each
 * one separately. Shifts recorded before workplaces existed carry no workplace,
 * and are counted for the user's default workplace — treating them as belonging
 * to nobody would leave a long-standing user with no pay history at all on the
 * day they first report leave.
 */
object LeaveEarningsBase {

    fun build(
        shifts: List<Shift>,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
        premiumProfiles: List<PremiumProfile> = emptyList(),
        workplaceId: String? = null,
        treatUnassignedAsThisWorkplace: Boolean = false,
        months: Int = 12,
        reference: YearMonth,
    ): LeaveEarningsHistory {
        val relevant = shifts.filter { shift ->
            shift.isCompleted &&
                shift.isEmployeePaid &&
                matchesWorkplace(shift, workplaceId, treatUnassignedAsThisWorkplace)
        }
        if (relevant.isEmpty()) return LeaveEarningsHistory.EMPTY

        val zone = WorkTimezone.zoneFor(settings)
        val byMonth = relevant.groupBy { shift ->
            YearMonth.from(WorkTimezone.shiftLocalDate(shift, zone))
        }

        // Walk back from the month before the reference: the reference month is
        // still in progress and averaging a part month in drags the figure down.
        val window = (1..months).map { reference.minusMonths(it.toLong()) }
        val entries = window.mapNotNull { yearMonth ->
            val monthShifts = byMonth[yearMonth] ?: return@mapNotNull null
            if (monthShifts.isEmpty()) return@mapNotNull null

            // Context is every relevant shift, not just the month's: weekly overtime
            // accumulates across a pay week that can straddle the 1st, and a month
            // summed without that context under-counts its first partial week.
            val summary = PayrollCalculator.sumMonthlyPay(
                shifts = monthShifts,
                settings = settings,
                profiles = profiles,
                premiumProfiles = premiumProfiles,
                contextShifts = relevant,
            )
            LeaveEarningsMonth(
                yearMonth = yearMonth,
                eligibleGross = summary.totalGross,
                daysWorked = monthShifts
                    .map { WorkTimezone.shiftLocalDate(it, zone) }
                    .distinct()
                    .size,
                minutesWorked = monthShifts.sumOf { shift -> netMinutes(shift) },
            )
        }
        return LeaveEarningsHistory(entries)
    }

    /** Dates the user has worked at a workplace, for proposing which days an absence affects. */
    fun workedDates(
        shifts: List<Shift>,
        settings: UserSettings,
        workplaceId: String? = null,
        treatUnassignedAsThisWorkplace: Boolean = false,
    ): List<LocalDate> {
        val zone = WorkTimezone.zoneFor(settings)
        return shifts
            .filter {
                it.isCompleted &&
                    it.isEmployeePaid &&
                    matchesWorkplace(it, workplaceId, treatUnassignedAsThisWorkplace)
            }
            .map { WorkTimezone.shiftLocalDate(it, zone) }
            .distinct()
    }

    private fun matchesWorkplace(
        shift: Shift,
        workplaceId: String?,
        treatUnassignedAsThisWorkplace: Boolean,
    ): Boolean = when {
        workplaceId == null -> true
        shift.workplaceId == workplaceId -> true
        shift.workplaceId == null -> treatUnassignedAsThisWorkplace
        else -> false
    }

    private fun netMinutes(shift: Shift): Int {
        val end = shift.endTime ?: return 0
        val gross = ((end.toEpochMilli() - shift.startTime.toEpochMilli()) / 60_000L).toInt()
        return (gross - shift.breakMinutes).coerceAtLeast(0)
    }
}

package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.compensation.ShiftClassifier
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The money and classification a row shows, with no locale or formatting in it. */
data class ShiftPayFacts(
    val netMinutes: Int,
    val weekend: Boolean,
    val hasOt: Boolean,
    val payGross: Double?,
)

/**
 * Everything the Shifts list needs that costs a payroll walk, computed once away from
 * the composition.
 *
 * The screen used to derive all of this inside a `remember` on the main thread: a full
 * `calculateShiftPayInContext` **and** a separate classification per row, plus a
 * `sumMonthlyPay` per week card — around ninety payroll walks for a thirty-shift month,
 * re-run whenever any of nine keys changed. On a slow device that is a visible stall on
 * every month change. Nothing here reads a `Locale`, so it moves to the view model
 * wholesale and the composable keeps only date and number formatting.
 */
data class ShiftsPayFacts(
    val perShift: Map<String, ShiftPayFacts>,
    val weekPay: Map<LocalDate, Double?>,
) {
    companion object {
        val EMPTY = ShiftsPayFacts(emptyMap(), emptyMap())
    }
}

/** Same 0=Sun … 6=Sat encoding the compensation rules use. */
private val WEEK_START_DAYS = listOf(
    java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY,
    java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY,
    java.time.DayOfWeek.SATURDAY,
)

internal fun buildShiftsPayFacts(
    shifts: List<Shift>,
    activeShift: Shift?,
    settings: UserSettings?,
    profiles: List<CompensationProfile>,
    premiumProfiles: List<PremiumProfile>,
    zone: ZoneId,
    weekStartDay: Int = ShiftWeekGrouper.resolveWeekStartDay(settings, profiles),
    payContextShifts: List<Shift> = shifts,
): ShiftsPayFacts {
    if (settings == null) return ShiftsPayFacts.EMPTY
    val displayShifts = buildList {
        addAll(shifts)
        if (activeShift != null && none { it.id == activeShift.id }) add(activeShift)
    }.distinctBy { it.id }
    if (displayShifts.isEmpty()) return ShiftsPayFacts.EMPTY

    val context = payContextShifts.ifEmpty { displayShifts }

    // One walk of each pay week for every row's overtime badge, instead of one
    // context-free classification per row. Context-aware on purpose: the money beside
    // the badge has been computed in pay-week context since Wave B, so a badge computed
    // without it said "no overtime" on a row whose pay included overtime minutes earned
    // through the week's accumulation.
    val classifications = ShiftClassifier.classifyMonth(
        shifts = displayShifts,
        contextShifts = context,
        settings = settings,
        profiles = profiles,
        premiumProfiles = premiumProfiles,
    )

    val perShift = HashMap<String, ShiftPayFacts>(displayShifts.size)
    for (shift in displayShifts) {
        val weekend = shift.isEmployeePaid &&
            CompensationResolver.isWeekendShift(shift, settings, profiles)
        val overtimeMinutes = classifications[shift.id]?.overtimeMinutes ?: 0
        perShift[shift.id] = ShiftPayFacts(
            netMinutes = ShiftDurationCalculator.netMinutes(shift) ?: 0,
            weekend = weekend,
            hasOt = shift.isEmployeePaid && overtimeMinutes > 0 &&
                !shift.isSpecialDay && !weekend,
            payGross = PayrollCalculator.calculateShiftPayInContext(
                shift, context, settings, profiles, premiumProfiles,
            )?.totalGross,
        )
    }

    val anchor = WEEK_START_DAYS[weekStartDay.coerceIn(0, 6)]
    val weekPay = displayShifts
        .groupBy {
            it.startTime.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(anchor))
        }
        .mapValues { (_, weekShifts) ->
            weekShifts.filter { it.isCompleted }.takeIf { it.isNotEmpty() }?.let { completed ->
                PayrollCalculator.sumMonthlyPay(
                    completed, settings, profiles, premiumProfiles,
                    contextShifts = context.ifEmpty { completed },
                ).totalGross
            }
        }

    return ShiftsPayFacts(perShift = perShift, weekPay = weekPay)
}

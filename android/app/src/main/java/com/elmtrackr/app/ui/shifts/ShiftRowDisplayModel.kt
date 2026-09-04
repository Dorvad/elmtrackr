package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.compensation.ShiftClassifier
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ShiftRowDisplayModel(
    val weekday: String,
    val dayNumber: String,
    val startText: String,
    val endText: String,
    val netMinutes: Int,
    val weekend: Boolean,
    val hasOt: Boolean,
    val payGross: Double?,
)

private val rowTimeFmt = DateTimeFormatter.ofPattern("HH:mm")

internal fun buildShiftRowDisplay(
    shift: Shift,
    settings: UserSettings?,
    profiles: List<CompensationProfile>,
    allShiftsForPay: List<Shift>,
    premiumProfiles: List<PremiumProfile> = emptyList(),
    // No default: a row rendered in the device zone instead of the work zone shows a
    // shift on the wrong date near midnight. Every caller already passes one.
    zone: ZoneId,
    locale: Locale = Locale.getDefault(),
    // Supplied by ShiftsViewModel, which computes it off the main thread. When absent
    // (tests, previews, any caller with nothing precomputed) the payroll walks happen
    // here, exactly as they always did.
    facts: ShiftPayFacts? = null,
): ShiftRowDisplayModel {
    val rowWeekdayFmt = DateTimeFormatter.ofPattern("EEE", locale)
    val zdt = shift.startTime.atZone(zone)
    val resolved = facts ?: computeShiftPayFacts(
        shift, settings, profiles, allShiftsForPay, premiumProfiles,
    )
    return ShiftRowDisplayModel(
        weekday = zdt.format(rowWeekdayFmt).uppercase(locale),
        dayNumber = zdt.dayOfMonth.toString(),
        startText = zdt.format(rowTimeFmt),
        endText = shift.endTime?.atZone(zone)?.format(rowTimeFmt) ?: "",
        netMinutes = resolved.netMinutes,
        weekend = resolved.weekend,
        hasOt = resolved.hasOt,
        payGross = resolved.payGross,
    )
}

/**
 * The fallback path for a single row with no precomputed facts. Keeps the badge rule
 * in one place; [buildShiftsPayFacts] is the batched equivalent the list uses.
 */
private fun computeShiftPayFacts(
    shift: Shift,
    settings: UserSettings?,
    profiles: List<CompensationProfile>,
    allShiftsForPay: List<Shift>,
    premiumProfiles: List<PremiumProfile>,
): ShiftPayFacts {
    // Weekend and overtime are pay classifications. Project time is paid by the
    // project's fee, so neither badge applies to it — and its pay is already null
    // because PayrollCalculator refuses project shifts.
    val weekend = shift.isEmployeePaid &&
        settings?.let { CompensationResolver.isWeekendShift(shift, it, profiles) } == true
    val context = allShiftsForPay.ifEmpty { listOf(shift) }
    val overtimeMinutes = settings?.let {
        ShiftClassifier.classify(shift, context, it, profiles, premiumProfiles)?.overtimeMinutes
    } ?: 0
    return ShiftPayFacts(
        netMinutes = ShiftDurationCalculator.netMinutes(shift) ?: 0,
        weekend = weekend,
        hasOt = shift.isEmployeePaid && overtimeMinutes > 0 && !shift.isSpecialDay && !weekend,
        payGross = settings?.let {
            PayrollCalculator.calculateShiftPayInContext(
                shift, context, it, profiles, premiumProfiles,
            )
        }?.totalGross,
    )
}

internal sealed interface ShiftsLazyListItem {
    val key: String

    data class SectionHeader(val section: ShiftWeekSection) : ShiftsLazyListItem {
        override val key: String = "week-${section.weekStart}"
    }

    data class ShiftEntry(
        val shift: Shift,
        val display: ShiftRowDisplayModel?,
        val isLastInSection: Boolean,
    ) : ShiftsLazyListItem {
        override val key: String = "shift-${shift.id}"
    }
}

/**
 * @param payContextShifts [shifts] plus the tail of the pay week containing the 1st,
 *   used only as pay-week context for the money figures. Defaults to [shifts], which
 *   is what every caller passed implicitly before the Shifts screen loaded the wider
 *   window.
 */
internal fun buildShiftsLazyListItems(
    shifts: List<Shift>,
    activeShift: Shift?,
    month: java.time.YearMonth,
    settings: UserSettings?,
    profiles: List<CompensationProfile>,
    premiumProfiles: List<PremiumProfile> = emptyList(),
    // No default: a row rendered in the device zone instead of the work zone shows a
    // shift on the wrong date near midnight. Every caller already passes one.
    zone: ZoneId,
    locale: Locale = Locale.getDefault(),
    payContextShifts: List<Shift> = shifts,
    // Precomputed off the main thread by ShiftsViewModel. Null means "work it out
    // here", which is what every test and preview does.
    payFacts: ShiftsPayFacts? = null,
): List<ShiftsLazyListItem> {
    val payContext = payContextShifts.ifEmpty { shifts }
    val sections = ShiftWeekGrouper.groupByWeek(
        shifts = shifts,
        activeShift = activeShift,
        month = month,
        settings = settings,
        profiles = profiles,
        premiumProfiles = premiumProfiles,
        zone = zone,
        locale = locale,
        payContextShifts = payContext,
        weekPay = payFacts?.weekPay,
    )
    return buildList {
        sections.forEach { section ->
            add(ShiftsLazyListItem.SectionHeader(section))
            section.shifts.forEachIndexed { index, shift ->
                add(
                    ShiftsLazyListItem.ShiftEntry(
                        shift = shift,
                        display = if (shift.isActive) {
                            null
                        } else {
                            buildShiftRowDisplay(
                                shift, settings, profiles, payContext, premiumProfiles,
                                zone = zone,
                                locale = locale,
                                facts = payFacts?.perShift?.get(shift.id),
                            )
                        },
                        isLastInSection = index == section.shifts.lastIndex,
                    ),
                )
            }
        }
    }
}

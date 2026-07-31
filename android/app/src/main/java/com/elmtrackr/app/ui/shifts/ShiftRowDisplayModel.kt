package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.compensation.CompensationResolver
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
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): ShiftRowDisplayModel {
    val rowWeekdayFmt = DateTimeFormatter.ofPattern("EEE", locale)
    val zdt = shift.startTime.atZone(zone)
    // Weekend and overtime are pay classifications. Project time is paid by the
    // project's fee, so neither badge applies to it — and its pay is already null
    // because PayrollCalculator refuses project shifts.
    val weekend = shift.isEmployeePaid &&
        settings?.let { CompensationResolver.isWeekendShift(shift, it, profiles) } == true
    val breakdown = settings?.let { MonthlyReportBuilder.buildShiftBreakdown(shift, it) }
    val hasOt = shift.isEmployeePaid &&
        (breakdown?.overtimeMinutes ?: 0) > 0 && !shift.isSpecialDay && !weekend
    val pay = settings?.let {
        PayrollCalculator.calculateShiftPayInContext(
            shift,
            allShiftsForPay.ifEmpty { listOf(shift) },
            it,
            profiles,
            premiumProfiles,
        )
    }
    return ShiftRowDisplayModel(
        weekday = zdt.format(rowWeekdayFmt).uppercase(locale),
        dayNumber = zdt.dayOfMonth.toString(),
        startText = zdt.format(rowTimeFmt),
        endText = shift.endTime?.atZone(zone)?.format(rowTimeFmt) ?: "",
        netMinutes = ShiftDurationCalculator.netMinutes(shift) ?: 0,
        weekend = weekend,
        hasOt = hasOt,
        payGross = pay?.totalGross,
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

internal fun buildShiftsLazyListItems(
    shifts: List<Shift>,
    activeShift: Shift?,
    month: java.time.YearMonth,
    settings: UserSettings?,
    profiles: List<CompensationProfile>,
    premiumProfiles: List<PremiumProfile> = emptyList(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): List<ShiftsLazyListItem> {
    val sections = ShiftWeekGrouper.groupByWeek(
        shifts = shifts,
        activeShift = activeShift,
        month = month,
        settings = settings,
        profiles = profiles,
        premiumProfiles = premiumProfiles,
        zone = zone,
        locale = locale,
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
                            buildShiftRowDisplay(shift, settings, profiles, shifts, premiumProfiles, zone = zone, locale = locale)
                        },
                        isLastInSection = index == section.shifts.lastIndex,
                    ),
                )
            }
        }
    }
}

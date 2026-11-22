package com.elmtrackr.app.ui.reports

import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.OvernightShiftDetector
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.ShiftBreakdown
import com.elmtrackr.app.domain.model.ShiftPayBreakdown
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import java.time.ZoneId

/** What one row of the Reports shift breakdown shows, priced once. */
data class ShiftReportRowPay(
    val breakdown: ShiftBreakdown,
    val weekend: Boolean,
    val overnight: Boolean,
    val pay: ShiftPayBreakdown?,
    val otMinutes: Int,
)

data class ShiftReportRowModel(val shift: Shift, val pay: ShiftReportRowPay)

/**
 * The shift breakdown's rows, newest first, each with its pay already worked out.
 *
 * Built where the rest of the month's payroll is built — in the ViewModel, on the
 * computation dispatcher — rather than row by row during composition. Each row walks
 * its shift's pay week, and in the Israeli engine re-classifies the week's earlier
 * shifts minute by minute; measured on a desktop JVM, a 28-shift month cost about
 * 18 ms of composition and a 54-shift month about 32 ms, all on the main thread,
 * every time the screen was composed with new inputs. A phone pays several times that.
 *
 * Same inputs and same calls the row used to make, so every figure is unchanged:
 * [shifts] is both what is listed and the context each row is priced against.
 */
internal fun buildShiftReportRows(
    shifts: List<Shift>,
    settings: UserSettings?,
    profiles: List<CompensationProfile>,
    premiumProfiles: List<PremiumProfile>,
    zone: ZoneId,
): List<ShiftReportRowModel> {
    if (settings == null || shifts.isEmpty()) return emptyList()
    return shifts.sortedByDescending { it.startTime }.map { shift ->
        ShiftReportRowModel(shift, shiftReportRowPay(shift, settings, profiles, shifts, premiumProfiles, zone))
    }
}

internal fun shiftReportRowPay(
    shift: Shift,
    settings: UserSettings,
    profiles: List<CompensationProfile>,
    allShifts: List<Shift>,
    premiumProfiles: List<PremiumProfile>,
    zone: ZoneId,
): ShiftReportRowPay {
    val breakdown = MonthlyReportBuilder.buildShiftBreakdown(
        shift, settings, profiles, premiumProfiles, allShifts,
    )
    return ShiftReportRowPay(
        breakdown = breakdown,
        weekend = CompensationResolver.isWeekendShift(shift, settings, profiles),
        overnight = OvernightShiftDetector.isOvernight(shift, zone),
        pay = PayrollCalculator.calculateShiftPayInContext(
            shift,
            allShifts.ifEmpty { listOf(shift) },
            settings,
            profiles,
            premiumProfiles,
        ),
        // The classifier's overtime — the minutes the pay engine actually paid at an
        // overtime rate, not clock time less breaks minus the raw daily standard.
        otMinutes = breakdown.overtimeMinutes,
    )
}

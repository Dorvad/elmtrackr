package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings

data class ReportInsights(
    val averageShiftMinutes: Int,
    val longestShiftMinutes: Int,
    val longestShift: Shift? = null,
    val overtimeShiftCount: Int,
    val weekendShiftCount: Int,
    val avgPayPerShift: Double? = null,
    val highestEarningShift: Shift? = null,
    val highestEarningAmount: Double? = null,
    val message: String,
)

object ReportInsightsBuilder {
    fun build(shifts: List<Shift>, settings: UserSettings): ReportInsights? {
        val completed = shifts.filter { it.isCompleted }
        if (completed.isEmpty()) return null

        val durations = completed.map { ShiftDurationCalculator.netMinutes(it) ?: 0 }
        val overtimeCount = durations.count { it > settings.dailyOvertimeThresholdMinutes }
        val weekendCount = completed.count { shift ->
            MonthlyReportBuilder.buildShiftBreakdown(shift, settings).weekendMinutes > 0
        }
        val total = durations.sum()

        // Longest shift
        var longestShift: Shift? = null
        var longestMins = 0
        for ((shift, mins) in completed.zip(durations)) {
            if (mins > longestMins) { longestMins = mins; longestShift = shift }
        }

        // Pay stats (only when hourly rate set)
        var totalPay = 0.0
        var highestEarningShift: Shift? = null
        var highestEarningAmount: Double? = null
        val hasPay = settings.hourlyRate?.let { it > 0 } == true
        if (hasPay) {
            for (shift in completed) {
                val pay = PayrollCalculator.calculateShiftPay(shift, settings)?.totalGross ?: 0.0
                totalPay += pay
                if (highestEarningAmount == null || pay > highestEarningAmount!!) {
                    highestEarningAmount = pay
                    highestEarningShift = shift
                }
            }
        }
        val avgPayPerShift = if (hasPay && completed.isNotEmpty()) totalPay / completed.size else null

        val message = when {
            overtimeCount > 0 -> "$overtimeCount shift${if (overtimeCount == 1) "" else "s"} crossed your daily overtime threshold."
            weekendCount > 0 -> "$weekendCount weekend shift${if (weekendCount == 1) "" else "s"} contributed this month."
            else -> "Your average completed shift was ${ShiftDurationCalculator.formatMinutes(total / completed.size)}."
        }

        return ReportInsights(
            averageShiftMinutes   = total / completed.size,
            longestShiftMinutes   = longestMins,
            longestShift          = longestShift,
            overtimeShiftCount    = overtimeCount,
            weekendShiftCount     = weekendCount,
            avgPayPerShift        = avgPayPerShift,
            highestEarningShift   = highestEarningShift,
            highestEarningAmount  = highestEarningAmount,
            message               = message,
        )
    }
}

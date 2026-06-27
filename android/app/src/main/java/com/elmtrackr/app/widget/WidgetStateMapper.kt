package com.elmtrackr.app.widget

import com.elmtrackr.app.domain.model.Shift
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object WidgetStateMapper {

    private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    fun map(
        shift: Shift?,
        lastCompletedShift: Shift? = null,
        pendingCount: Int = 0,
    ): WidgetShiftState {
        val zone = ZoneId.systemDefault()
        val now = Instant.now().atZone(zone)
        val dateLabel = now.format(dateFormatter)

        if (shift != null && shift.isActive) {
            val startFormatted = WidgetPreferences.formatShiftStart(shift.startTime, zone)
            return WidgetShiftState(
                isActive = true,
                shiftId = shift.id,
                startTimeLabel = startFormatted,
                dateLabel = dateLabel,
                lastPunchLabel = "Since $startFormatted",
                pendingCount = pendingCount,
                shiftStartEpochMillis = shift.startTime.toEpochMilli(),
            )
        }

        val lastEnd = lastCompletedShift?.endTime
        return if (lastEnd != null) {
            WidgetShiftState(
                isActive = false,
                shiftId = "",
                startTimeLabel = WidgetPreferences.formatShiftStart(lastEnd, zone),
                dateLabel = dateLabel,
                lastPunchLabel = WidgetPreferences.formatLastPunch(lastEnd, zone),
                pendingCount = pendingCount,
                lastPunchEndEpochMillis = lastEnd.toEpochMilli(),
            )
        } else {
            WidgetShiftState(
                isActive = false,
                shiftId = "",
                startTimeLabel = "--:--",
                dateLabel = dateLabel,
                lastPunchLabel = "",
                pendingCount = pendingCount,
            )
        }
    }
}

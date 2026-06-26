package com.elmtrackr.app.widget

import com.elmtrackr.app.domain.model.Shift
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object WidgetStateMapper {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    fun map(shift: Shift?, pendingCount: Int = 0): WidgetShiftState {
        val zone = ZoneId.systemDefault()
        val now = Instant.now().atZone(zone)
        val dateLabel = now.format(dateFormatter)
        return if (shift != null && shift.isActive) {
            val startFormatted = shift.startTime.atZone(zone).format(timeFormatter)
            WidgetShiftState(
                isActive = true,
                shiftId = shift.id,
                startTimeLabel = startFormatted,
                dateLabel = dateLabel,
                lastPunchLabel = "Today $startFormatted",
                pendingCount = pendingCount,
            )
        } else {
            // Use current time as the best approximation of when the shift ended.
            val nowFormatted = now.format(timeFormatter)
            WidgetShiftState(
                isActive = false,
                shiftId = "",
                startTimeLabel = nowFormatted,
                dateLabel = dateLabel,
                lastPunchLabel = "Today $nowFormatted",
                pendingCount = pendingCount,
            )
        }
    }
}

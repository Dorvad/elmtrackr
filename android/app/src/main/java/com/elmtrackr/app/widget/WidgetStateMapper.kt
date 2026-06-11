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
        val dateLabel = Instant.now().atZone(zone).format(dateFormatter)
        return if (shift != null && shift.isActive) {
            WidgetShiftState(
                isActive = true,
                shiftId = shift.id,
                startTimeLabel = shift.startTime.atZone(zone).format(timeFormatter),
                dateLabel = dateLabel,
                pendingCount = pendingCount,
            )
        } else {
            WidgetShiftState(
                isActive = false,
                shiftId = "",
                startTimeLabel = "",
                dateLabel = dateLabel,
                pendingCount = pendingCount,
            )
        }
    }
}

package com.elmtrackr.app.widget

data class WidgetShiftState(
    val isActive: Boolean,
    val shiftId: String,
    /** HH:MM of the active shift start (active) or approximate clock-out time (inactive). */
    val startTimeLabel: String,
    val dateLabel: String,
    /** "Today HH:MM" string used for the "Last punch" / "Since" subtitle line. */
    val lastPunchLabel: String,
    val pendingCount: Int,
)

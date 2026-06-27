package com.elmtrackr.app.widget

data class WidgetShiftState(
    val isActive: Boolean,
    val shiftId: String,
    /** HH:mm when clocked in; last clock-out time or placeholder when idle. */
    val startTimeLabel: String,
    val dateLabel: String,
    /** Human-readable subtitle (since / last out). */
    val lastPunchLabel: String,
    val pendingCount: Int,
    val shiftStartEpochMillis: Long = 0L,
    val lastPunchEndEpochMillis: Long = 0L,
)

package com.elmtrackr.app.widget

data class WidgetShiftState(
    val isActive: Boolean,
    val shiftId: String,
    val startTimeLabel: String,
    val dateLabel: String,
    val pendingCount: Int,
)

package com.elmtrackr.app.domain.model

data class TaskMonthlyBreakdown(
    val taskKey: String,
    val taskId: String?,
    val name: String,
    val icon: String?,
    val color: String?,
    val shiftCount: Int,
    val totalMinutes: Int,
    val regularMinutes: Int,
    val overtimeMinutes: Int,
    val weekendMinutes: Int,
    val totalPay: Double?,
    val averageShiftMinutes: Int,
)

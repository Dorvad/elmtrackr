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
    /** Gross per currency; null when no shift of the task was priced. */
    val totalPay: PayByCurrency?,
    val averageShiftMinutes: Int,
)

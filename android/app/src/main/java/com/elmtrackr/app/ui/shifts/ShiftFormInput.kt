package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.domain.model.RefundAction
import java.time.Instant

data class ShiftFormInput(
    val startTime: Instant,
    val endTime: Instant?,
    val breakMinutes: Int,
    val notes: String,
    val premiumProfileId: String? = null,
    val refundAction: RefundAction?,
    val compensationProfileId: String? = null,
    val taskId: String? = null,
)

package com.elmtrackr.app.domain.model

import java.time.Instant

/**
 * Core time-tracking record.
 * endTime == null means the shift is currently active (clocked in).
 */
data class Shift(
    val id: String,
    val userId: String,
    val startTime: Instant,
    val endTime: Instant?,
    val breakMinutes: Int = 0,
    val notes: String? = null,
    val isSpecialDay: Boolean = false,
    val refundAction: RefundAction? = null,
    val compensationProfileId: String? = null,
    val compensationSnapshot: com.elmtrackr.app.domain.model.CompensationSnapshot? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    val isActive: Boolean get() = endTime == null
    val isCompleted: Boolean get() = endTime != null
}

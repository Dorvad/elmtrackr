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
    val premiumProfileId: String? = null,
    /**
     * Per-shift override: pay this shift at the regular rate even if it falls
     * on a configured weekend day or carries special-day flags. Set from the
     * weekend/holiday switch on the edit form.
     */
    val forceRegularRate: Boolean = false,
    val refundAction: RefundAction? = null,
    val compensationProfileId: String? = null,
    val compensationSnapshot: com.elmtrackr.app.domain.model.CompensationSnapshot? = null,
    val taskId: String? = null,
    val taskNameSnapshot: String? = null,
    val taskIconSnapshot: String? = null,
    val taskHourlyRateSnapshot: Double? = null,
    /**
     * Optional Paid Projects link. Independent of [taskId]: a shift may carry
     * both a project and a task. Employee pay never reads it — CompensationResolver
     * substitutes a shift's *task* rate for the base pay rate, so a project fee
     * must never reach it. There is deliberately no project rate on a shift.
     */
    val projectId: String? = null,
    /** Kept so a deleted project still labels its shifts, like the task snapshots. */
    val projectNameSnapshot: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    val isActive: Boolean get() = endTime == null
    val isCompleted: Boolean get() = endTime != null
}

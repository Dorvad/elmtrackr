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
    val forceRegularRate: Boolean = false,
    /**
     * Whether this shift is paid as wages or tracked against [projectId].
     * Defaults to EMPLOYEE so any caller that does not set it creates ordinary
     * hourly work.
     */
    val compensationSource: com.elmtrackr.app.domain.model.CompensationSource =
        com.elmtrackr.app.domain.model.CompensationSource.EMPLOYEE,
    val projectId: String? = null,
)

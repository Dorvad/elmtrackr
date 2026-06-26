package com.elmtrackr.app.domain.model

import java.time.Instant

/** Travel-refund receipt attached to a shift (one per direction: to/from work). */
data class RefundClaim(
    val id: String,
    val shiftId: String,
    val userId: String,
    val direction: RefundDirection,
    val provider: RefundProvider,
    val amount: Double,
    val rideAt: Instant,
    val notes: String? = null,
    val receiptPath: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
)

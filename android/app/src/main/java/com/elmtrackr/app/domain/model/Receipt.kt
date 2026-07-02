package com.elmtrackr.app.domain.model

import java.time.Instant

data class Receipt(
    val id: String,
    val userId: String?,
    val refundClaimId: String?,
    val localImageUri: String,
    val merchantName: String?,
    val amount: Double?,
    val currency: String?,
    val receiptDate: Instant?,
    val rawOcrText: String?,
    val parserVersion: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

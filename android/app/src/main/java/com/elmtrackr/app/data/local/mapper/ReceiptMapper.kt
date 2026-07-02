package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.ReceiptEntity
import com.elmtrackr.app.domain.model.Receipt
import java.time.Instant

fun ReceiptEntity.toDomain(): Receipt = Receipt(
    id = id,
    userId = userId,
    refundClaimId = refundClaimId,
    localImageUri = localImageUri,
    merchantName = merchantName,
    amount = amount,
    currency = currency,
    receiptDate = receiptDate?.let(Instant::ofEpochMilli),
    rawOcrText = rawOcrText,
    parserVersion = parserVersion,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Receipt.toEntity(): ReceiptEntity = ReceiptEntity(
    id = id,
    userId = userId,
    refundClaimId = refundClaimId,
    localImageUri = localImageUri,
    merchantName = merchantName,
    amount = amount,
    currency = currency,
    receiptDate = receiptDate?.toEpochMilli(),
    rawOcrText = rawOcrText,
    parserVersion = parserVersion,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

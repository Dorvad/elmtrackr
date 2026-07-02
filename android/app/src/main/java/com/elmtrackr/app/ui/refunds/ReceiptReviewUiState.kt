package com.elmtrackr.app.ui.refunds

import com.elmtrackr.app.domain.model.ReceiptParseConfidence

data class ReceiptReviewUiState(
    val receiptId: String? = null,
    val localImagePath: String,
    val merchantName: String = "",
    val amountText: String = "",
    val currency: String = "",
    val receiptDateMillis: Long? = null,
    val rawOcrText: String? = null,
    val confidence: ReceiptParseConfidence = ReceiptParseConfidence.NONE,
    val ocrFailed: Boolean = false,
    val isSaving: Boolean = false,
)

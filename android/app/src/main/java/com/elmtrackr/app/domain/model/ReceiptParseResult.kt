package com.elmtrackr.app.domain.model

import com.elmtrackr.app.domain.receipt.ReceiptParser
import java.time.Instant

enum class ReceiptParseConfidence {
    HIGH,
    MEDIUM,
    LOW,
    NONE,
}

data class ReceiptParseResult(
    val merchantName: String?,
    val amount: Double?,
    val currency: String?,
    val receiptDate: Instant?,
    val rawOcrText: String,
    val confidence: ReceiptParseConfidence,
    val parserVersion: String = ReceiptParser.VERSION,
    /** True when the chosen amount sat on/next to a "total" label — used to arbitrate between OCR engines. */
    val amountNearTotalKeyword: Boolean = false,
)

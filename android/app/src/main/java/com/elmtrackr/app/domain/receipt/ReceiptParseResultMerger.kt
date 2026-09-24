package com.elmtrackr.app.domain.receipt

import com.elmtrackr.app.domain.model.ReceiptParseResult

/**
 * Merges the parse results of the Hebrew (Tesseract) and Latin (ML Kit) OCR
 * passes over the same receipt image.
 *
 * Arbitration rules:
 * - Amount: a value found next to a "total" label wins over one that wasn't;
 *   between two labeled values the more confident result wins, and when both
 *   engines agree on the number the Latin digits are used (ML Kit reads digits
 *   more reliably than Tesseract).
 * - Merchant: a Hebrew merchant name from the Hebrew pass is trusted (ML Kit
 *   cannot read Hebrew at all); otherwise the Latin pass names the merchant.
 * - Date/currency: taken from the amount's source first, then backfilled.
 * - A fused reading (Hebrew label, Latin digits on that same row) wins whenever
 *   it actually found a labeled total. That is the number the customer paid;
 *   a Hebrew-only total is often the same label glued to a misread number.
 */
object ReceiptParseResultMerger {

    fun merge(
        hebrew: ReceiptParseResult?,
        latin: ReceiptParseResult?,
        fused: ReceiptParseResult? = null,
    ): ReceiptParseResult {
        if (hebrew == null && latin == null && fused == null) {
            throw IllegalArgumentException("At least one parse result is required")
        }
        if (hebrew == null && latin == null) return requireNotNull(fused)
        if (hebrew == null && fused == null) return requireNotNull(latin)
        if (latin == null && fused == null) return requireNotNull(hebrew)

        val amountSource = pickAmount(hebrew, latin, fused)
        val other = when (amountSource) {
            hebrew -> latin ?: fused
            latin -> hebrew ?: fused
            else -> hebrew ?: latin
        }

        val merchant = when {
            hebrew != null && latin != null -> pickMerchant(hebrew, latin)
            else -> hebrew?.merchantName ?: latin?.merchantName ?: fused?.merchantName
        }
        val amount = amountSource?.amount
        val date = amountSource?.receiptDate
            ?: hebrew?.receiptDate
            ?: latin?.receiptDate
            ?: fused?.receiptDate
        val currency = amountSource?.currency
            ?: other?.currency
            ?: hebrew?.currency
            ?: latin?.currency
            ?: fused?.currency
        val nearTotal = amountSource?.amountNearTotalKeyword ?: false

        return ReceiptParseResult(
            merchantName = merchant,
            amount = amount,
            currency = if (amount != null) currency else hebrew?.currency ?: latin?.currency ?: fused?.currency,
            receiptDate = date,
            rawOcrText = combineRawText(
                listOfNotNull(hebrew?.rawOcrText, latin?.rawOcrText, fused?.rawOcrText),
            ),
            confidence = ReceiptParser.computeConfidence(
                amount = amount,
                amountNearTotal = nearTotal,
                date = date,
                merchant = merchant,
            ),
            parserVersion = ReceiptParser.VERSION,
            amountNearTotalKeyword = nearTotal,
        )
    }

    /**
     * The fused line is the one place a Hebrew total label and a Latin amount
     * were seen on the same row. It outranks either engine alone when that
     * label was found. Otherwise the two engines arbitrate as before, and a
     * fused amount only fills a gap.
     */
    private fun pickAmount(
        hebrew: ReceiptParseResult?,
        latin: ReceiptParseResult?,
        fused: ReceiptParseResult?,
    ): ReceiptParseResult? {
        if (fused?.amount != null && fused.amountNearTotalKeyword) return fused
        val base = when {
            hebrew == null -> latin
            latin == null -> hebrew
            else -> pickAmountSource(hebrew, latin)
        }
        if (base?.amountNearTotalKeyword == true) return base
        if (fused?.amount != null && (base?.amount == null || fused.confidence.ordinal < base.confidence.ordinal)) {
            return fused
        }
        return base
    }

    private fun pickAmountSource(hebrew: ReceiptParseResult, latin: ReceiptParseResult): ReceiptParseResult? = when {
        hebrew.amount == null && latin.amount == null -> null
        hebrew.amount == null -> latin
        latin.amount == null -> hebrew
        hebrew.amountNearTotalKeyword && !latin.amountNearTotalKeyword -> hebrew
        latin.amountNearTotalKeyword && !hebrew.amountNearTotalKeyword -> latin
        hebrew.amount == latin.amount -> latin
        // Both (or neither) sit next to a total label but disagree on the number:
        // let overall confidence break the tie, preferring the Hebrew pass that
        // can actually read Israeli receipt labels.
        latin.confidence.ordinal < hebrew.confidence.ordinal -> latin
        else -> hebrew
    }

    private fun pickMerchant(hebrew: ReceiptParseResult, latin: ReceiptParseResult): String? {
        val hebrewMerchant = hebrew.merchantName?.takeIf { name -> name.any { it in 'א'..'ת' } }
        return hebrewMerchant ?: latin.merchantName ?: hebrew.merchantName
    }

    private fun combineRawText(parts: List<String>): String =
        parts.filter { it.isNotBlank() }.joinToString(RAW_TEXT_SEPARATOR)

    private const val RAW_TEXT_SEPARATOR = "\n--- OCR ---\n"
}

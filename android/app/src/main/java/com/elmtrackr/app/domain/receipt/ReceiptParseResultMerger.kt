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
 */
object ReceiptParseResultMerger {

    fun merge(hebrew: ReceiptParseResult?, latin: ReceiptParseResult?): ReceiptParseResult {
        if (hebrew == null && latin == null) {
            throw IllegalArgumentException("At least one parse result is required")
        }
        if (hebrew == null) return requireNotNull(latin) { "At least one parse result is required" }
        if (latin == null) return hebrew

        val amountSource = pickAmountSource(hebrew, latin)
        val other = if (amountSource === hebrew) latin else hebrew

        val merchant = pickMerchant(hebrew, latin)
        val amount = amountSource?.amount
        val date = amountSource?.receiptDate ?: hebrew.receiptDate ?: latin.receiptDate
        val currency = amountSource?.currency ?: other.currency ?: hebrew.currency ?: latin.currency
        val nearTotal = amountSource?.amountNearTotalKeyword ?: false

        return ReceiptParseResult(
            merchantName = merchant,
            amount = amount,
            currency = if (amount != null) currency else hebrew.currency ?: latin.currency,
            receiptDate = date,
            rawOcrText = combineRawText(hebrew.rawOcrText, latin.rawOcrText),
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

    private fun combineRawText(hebrewRaw: String, latinRaw: String): String =
        listOf(hebrewRaw, latinRaw).filter { it.isNotBlank() }.joinToString(RAW_TEXT_SEPARATOR)

    private const val RAW_TEXT_SEPARATOR = "\n--- OCR ---\n"
}

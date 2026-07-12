package com.elmtrackr.app.domain.receipt

import com.elmtrackr.app.domain.model.ReceiptParseConfidence
import com.elmtrackr.app.domain.model.ReceiptParseResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

interface ReceiptTextRecognizer {
    suspend fun recognizeText(imagePath: String): Result<String>
}

interface ReceiptScanPipeline {
    suspend fun recognizeAndParse(imagePath: String): ReceiptParseResult
}

/**
 * Runs both OCR engines concurrently — ML Kit (Latin: digits, brand names) and
 * Tesseract (Hebrew: the labels that mark the total) — parses each output
 * independently, then merges the two results field by field.
 */
class DefaultReceiptScanPipeline @Inject constructor(
    @LatinOcr private val latinRecognizer: ReceiptTextRecognizer,
    @HebrewOcr private val hebrewRecognizer: ReceiptTextRecognizer,
) : ReceiptScanPipeline {

    private val parser = ReceiptParser()

    override suspend fun recognizeAndParse(imagePath: String): ReceiptParseResult = coroutineScope {
        val latinDeferred = async { latinRecognizer.recognizeText(imagePath) }
        val hebrewDeferred = async { hebrewRecognizer.recognizeText(imagePath) }
        val latin = latinDeferred.await()
        val hebrew = hebrewDeferred.await()

        val latinResult = latin.getOrNull()?.let(parser::parse)
        val hebrewResult = hebrew.getOrNull()?.let(parser::parse)

        if (latinResult == null && hebrewResult == null) {
            val error = latin.exceptionOrNull() ?: hebrew.exceptionOrNull()
            return@coroutineScope ReceiptParseResult(
                merchantName = null,
                amount = null,
                currency = null,
                receiptDate = null,
                rawOcrText = error?.message.orEmpty(),
                confidence = ReceiptParseConfidence.NONE,
                parserVersion = ReceiptParser.VERSION,
            )
        }

        ReceiptParseResultMerger.merge(hebrew = hebrewResult, latin = latinResult)
    }
}

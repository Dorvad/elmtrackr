package com.elmtrackr.app.domain.receipt

import com.elmtrackr.app.domain.model.ReceiptParseConfidence
import com.elmtrackr.app.domain.model.ReceiptParseResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

interface ReceiptTextRecognizer {
    suspend fun recognize(imagePath: String): Result<OcrPage>
}

interface ReceiptScanPipeline {
    suspend fun recognizeAndParse(imagePath: String): ReceiptParseResult
}

/**
 * Runs both OCR engines concurrently — ML Kit (Latin: digits, brand names) and
 * Tesseract (Hebrew: the labels that mark the total) — then lays the Latin
 * digits onto the Hebrew total line before parsing. Each engine is still
 * parsed on its own, so a fusion that finds nothing does not throw away a
 * good single-engine read.
 */
class DefaultReceiptScanPipeline @Inject constructor(
    @LatinOcr private val latinRecognizer: ReceiptTextRecognizer,
    @HebrewOcr private val hebrewRecognizer: ReceiptTextRecognizer,
) : ReceiptScanPipeline {

    private val parser = ReceiptParser()

    override suspend fun recognizeAndParse(imagePath: String): ReceiptParseResult = coroutineScope {
        val latinDeferred = async { latinRecognizer.recognize(imagePath) }
        val hebrewDeferred = async { hebrewRecognizer.recognize(imagePath) }
        val latin = latinDeferred.await()
        val hebrew = hebrewDeferred.await()

        val latinPage = latin.getOrNull()
        val hebrewPage = hebrew.getOrNull()
        val latinResult = latinPage?.text?.let(parser::parse)
        val hebrewResult = hebrewPage?.text?.let(parser::parse)
        val fusedResult = ReceiptOcrFusion.fuse(hebrewPage, latinPage)?.let(parser::parse)

        if (latinResult == null && hebrewResult == null && fusedResult == null) {
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

        ReceiptParseResultMerger.merge(hebrew = hebrewResult, latin = latinResult, fused = fusedResult)
    }
}

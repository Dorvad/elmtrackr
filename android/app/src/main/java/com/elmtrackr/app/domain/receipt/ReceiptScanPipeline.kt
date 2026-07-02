package com.elmtrackr.app.domain.receipt

import com.elmtrackr.app.domain.model.ReceiptParseConfidence
import com.elmtrackr.app.domain.model.ReceiptParseResult
import javax.inject.Inject

interface ReceiptTextRecognizer {
    suspend fun recognizeText(imagePath: String): Result<String>
}

interface ReceiptScanPipeline {
    suspend fun recognizeAndParse(imagePath: String): ReceiptParseResult
}

class DefaultReceiptScanPipeline @Inject constructor(
    private val textRecognizer: ReceiptTextRecognizer,
) : ReceiptScanPipeline {
    private val parser = ReceiptParser()
    override suspend fun recognizeAndParse(imagePath: String): ReceiptParseResult {
        val ocrResult = textRecognizer.recognizeText(imagePath)
        return ocrResult.fold(
            onSuccess = { text -> parser.parse(text) },
            onFailure = { error ->
                ReceiptParseResult(
                    merchantName = null,
                    amount = null,
                    currency = null,
                    receiptDate = null,
                    rawOcrText = error.message.orEmpty(),
                    confidence = ReceiptParseConfidence.NONE,
                    parserVersion = ReceiptParser.VERSION,
                )
            },
        )
    }
}

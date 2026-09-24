package com.elmtrackr.app.domain.receipt

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptScanPipelineTest {

    @Test
    fun `the scanned total is the latin amount on the hebrew total row`() = runTest {
        val pipeline = DefaultReceiptScanPipeline(
            latinRecognizer = page(
                OcrPage(
                    text = "LIME\n18.00\n42.00",
                    lines = listOf(
                        OcrLine("LIME", 0.05f, 0.10f),
                        OcrLine("18.00", 0.70f, 0.75f),
                        OcrLine("42.00", 0.84f, 0.90f),
                    ),
                ),
            ),
            hebrewRecognizer = page(
                OcrPage(
                    text = "ליים\nמע\"מ 1B.00\nסה\"כ 4.00",
                    lines = listOf(
                        OcrLine("ליים", 0.05f, 0.10f),
                        OcrLine("מע\"מ 1B.00", 0.70f, 0.75f),
                        OcrLine("סה\"כ 4.00", 0.84f, 0.90f),
                    ),
                ),
            ),
        )

        val result = pipeline.recognizeAndParse("receipt.jpg")

        assertEquals(42.0, result.amount!!, 0.001)
        assertTrue(result.amountNearTotalKeyword)
        assertTrue(result.merchantName?.contains("ליים") == true)
    }

    private fun page(ocr: OcrPage) = object : ReceiptTextRecognizer {
        override suspend fun recognize(imagePath: String): Result<OcrPage> = Result.success(ocr)
    }
}

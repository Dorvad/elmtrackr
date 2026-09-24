package com.elmtrackr.app.domain.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptOcrFusionTest {

    private val parser = ReceiptParser()

    @Test
    fun `latin digits on the same row replace the garbled hebrew amount`() {
        val hebrew = OcrPage(
            text = "ליים\nמע\"מ 18.00\nסה\"כ 4.00",
            lines = listOf(
                OcrLine("ליים", 0.05f, 0.10f),
                OcrLine("מע\"מ 18.00", 0.70f, 0.75f),
                OcrLine("סה\"כ 4.00", 0.84f, 0.90f),
            ),
        )
        val latin = OcrPage(
            text = "LIME\n18.00\n42.00",
            lines = listOf(
                OcrLine("LIME", 0.05f, 0.10f),
                OcrLine("18.00", 0.70f, 0.75f),
                OcrLine("42.00", 0.84f, 0.90f),
            ),
        )

        val fused = ReceiptOcrFusion.fuse(hebrew, latin)!!
        val result = parser.parse(fused)

        assertTrue(fused.lines().last().contains("42.00"))
        assertEquals(42.0, result.amount!!, 0.001)
        assertTrue(result.amountNearTotalKeyword)
    }

    @Test
    fun `lines without boxes still pair a bottom total with the bottom amount`() {
        val hebrew = OcrPage.fromText("מונית\nמע\"מ 18.00\nסה\"כ 4.00")
        val latin = OcrPage.fromText("Taxi\n18.00\n42.50")

        val result = parser.parse(ReceiptOcrFusion.fuse(hebrew, latin)!!)

        assertEquals(42.5, result.amount!!, 0.001)
        assertTrue(result.amountNearTotalKeyword)
    }

    private fun String.lines() = lineSequence().toList()
}

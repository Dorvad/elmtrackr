package com.elmtrackr.app.domain.receipt

import com.elmtrackr.app.domain.model.ReceiptParseConfidence
import com.elmtrackr.app.domain.model.ReceiptParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReceiptParseResultMergerTest {

    private fun result(
        merchant: String? = null,
        amount: Double? = null,
        currency: String? = null,
        date: Instant? = null,
        raw: String = "raw",
        confidence: ReceiptParseConfidence = ReceiptParseConfidence.LOW,
        nearTotal: Boolean = false,
    ) = ReceiptParseResult(
        merchantName = merchant,
        amount = amount,
        currency = currency,
        receiptDate = date,
        rawOcrText = raw,
        confidence = confidence,
        amountNearTotalKeyword = nearTotal,
    )

    @Test
    fun `labeled hebrew total wins over unlabeled latin amount`() {
        val hebrew = result(amount = 42.0, currency = "ILS", nearTotal = true, confidence = ReceiptParseConfidence.MEDIUM)
        val latin = result(amount = 38.5, confidence = ReceiptParseConfidence.LOW)

        val merged = ReceiptParseResultMerger.merge(hebrew = hebrew, latin = latin)

        assertEquals(42.0, merged.amount!!, 0.001)
        assertEquals("ILS", merged.currency)
        assertTrue(merged.amountNearTotalKeyword)
    }

    @Test
    fun `labeled latin total wins over unlabeled hebrew amount`() {
        val hebrew = result(amount = 17.0)
        val latin = result(amount = 17.9, nearTotal = true)

        val merged = ReceiptParseResultMerger.merge(hebrew = hebrew, latin = latin)

        assertEquals(17.9, merged.amount!!, 0.001)
    }

    @Test
    fun `hebrew merchant with hebrew letters preferred over latin garble`() {
        val hebrew = result(merchant = "מונית ישראל", amount = 42.0, nearTotal = true)
        val latin = result(merchant = "nnrn 7xw", amount = 42.0)

        val merged = ReceiptParseResultMerger.merge(hebrew = hebrew, latin = latin)

        assertEquals("מונית ישראל", merged.merchantName)
    }

    @Test
    fun `latin merchant used when hebrew pass finds no hebrew name`() {
        val hebrew = result(merchant = "||] |1)")
        val latin = result(merchant = "LIME", amount = 17.9, nearTotal = true)

        val merged = ReceiptParseResultMerger.merge(hebrew = hebrew, latin = latin)

        assertEquals("LIME", merged.merchantName)
    }

    @Test
    fun `fields are backfilled across engines and confidence recomputed`() {
        val date = Instant.parse("2026-03-12T00:00:00Z")
        val hebrew = result(merchant = "מונית ישראל", amount = 42.0, currency = "ILS", nearTotal = true)
        val latin = result(date = date)

        val merged = ReceiptParseResultMerger.merge(hebrew = hebrew, latin = latin)

        assertEquals(42.0, merged.amount!!, 0.001)
        assertEquals(date, merged.receiptDate)
        assertEquals(ReceiptParseConfidence.HIGH, merged.confidence)
    }

    @Test
    fun `single engine result passes through unchanged`() {
        val latin = result(merchant = "LIME", amount = 17.9)

        assertEquals(latin, ReceiptParseResultMerger.merge(hebrew = null, latin = latin))
        assertEquals(latin, ReceiptParseResultMerger.merge(hebrew = latin, latin = null))
    }

    @Test
    fun `raw text from both engines is preserved`() {
        val hebrew = result(raw = "טקסט עברי", amount = 42.0)
        val latin = result(raw = "latin text")

        val merged = ReceiptParseResultMerger.merge(hebrew = hebrew, latin = latin)

        assertTrue(merged.rawOcrText.contains("טקסט עברי"))
        assertTrue(merged.rawOcrText.contains("latin text"))
    }
}

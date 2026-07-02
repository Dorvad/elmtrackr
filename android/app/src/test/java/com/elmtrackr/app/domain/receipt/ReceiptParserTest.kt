package com.elmtrackr.app.domain.receipt

import com.elmtrackr.app.domain.model.ReceiptParseConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReceiptParserTest {

    private val parser = ReceiptParser(ZoneId.of("Asia/Jerusalem"))

    @Test
    fun `parse israeli taxi receipt prefers total near סהכ`() {
        val text = """
            מונית ישראל בע"מ
            תאריך: 12/03/2026 14:35
            נסיעה: רothschild -> דיזengoff
            מחיר נסיעה: 38.50 ₪
            סה"כ לתשלום: 42.00 ₪
            תודה שנסעתם איתנו
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(42.0, result.amount!!, 0.001)
        assertEquals("ILS", result.currency)
        assertNotNull(result.receiptDate)
        assertEquals(
            LocalDate.of(2026, 3, 12),
            result.receiptDate!!.atZone(ZoneId.of("Asia/Jerusalem")).toLocalDate(),
        )
        assertTrue(result.confidence == ReceiptParseConfidence.HIGH ||
            result.confidence == ReceiptParseConfidence.MEDIUM)
        assertTrue(result.merchantName?.contains("מונית") == true)
    }

    @Test
    fun `parse lime receipt with english total label`() {
        val text = """
            LIME
            Ride receipt
            Date 02/07/2026 08:12
            Trip fare 17.90 ILS
            Total amount 17.90 ILS
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(17.9, result.amount!!, 0.001)
        assertEquals("ILS", result.currency)
        assertEquals("LIME", result.merchantName)
        assertTrue(result.confidence != ReceiptParseConfidence.NONE)
    }

    @Test
    fun `parse receipt with לתשלום keyword`() {
        val text = """
            בolt
            01.07.2026
            שירות נסיעה
            סכום ביניים 26.00 ₪
            לתשלום 29.50 ₪
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(29.5, result.amount!!, 0.001)
        assertEquals("ILS", result.currency)
    }

    @Test
    fun `parse returns none confidence for empty text`() {
        val result = parser.parse("   \n\t  ")

        assertNull(result.amount)
        assertNull(result.merchantName)
        assertEquals(ReceiptParseConfidence.NONE, result.confidence)
    }

    @Test
    fun `parse keeps merchant from top meaningful line`() {
        val text = """
            GETT
            15/06/2026
            נסיעה מתל אביב
            סהכ 55.00 ₪
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals("GETT", result.merchantName)
        assertEquals(55.0, result.amount!!, 0.001)
    }

    @Test
    fun `normalize collapses whitespace and nbsp`() {
        val normalized = parser.normalize("Total\u00A0\u00A0amount:\u200F 12.50\u00A0₪")
        assertEquals("Total amount: 12.50 ₪", normalized)
    }

    @Test
    fun `parse receipt without amount still extracts merchant and date`() {
        val text = """
            Yango
            20/05/2026
            Thank you for riding with us
        """.trimIndent()

        val result = parser.parse(text)

        assertNull(result.amount)
        assertEquals("Yango", result.merchantName)
        assertNotNull(result.receiptDate)
        assertTrue(
            result.confidence == ReceiptParseConfidence.LOW ||
                result.confidence == ReceiptParseConfidence.NONE,
        )
    }
}

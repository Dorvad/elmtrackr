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
    fun `parse ride receipt without any total keyword picks the largest decimal amount`() {
        // The on-device recognizer is Latin-only, so Hebrew labels often come out
        // garbled and no total keyword survives; the fare must still be found.
        val text = """
            Bird
            08:12
            1.00
            11.50
            12.50
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(12.5, result.amount!!, 0.001)
    }

    @Test
    fun `parse ride receipt with integer total near keyword`() {
        val text = """
            Yango ride
            Total 24
            Thank you
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(24.0, result.amount!!, 0.001)
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
    fun `parse total labeled with hebrew gershayim quote variant`() {
        val text = """
            סופר יוחננוף
            תאריך: 05/06/2026
            חלב 6.90
            לחם 8.50
            סה״כ 15.40 ₪
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(15.4, result.amount!!, 0.001)
        assertTrue(result.amountNearTotalKeyword)
    }

    @Test
    fun `parse ignores vat line and picks the grand total`() {
        val text = """
            מסעדת השף
            עסקית 35.90 ₪
            מע"מ 17% 6.10 ₪
            סה"כ לתשלום 42.00 ₪
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(42.0, result.amount!!, 0.001)
    }

    @Test
    fun `parse ignores cash tendered and change lines`() {
        val text = """
            קיוסק מרכזי
            סה"כ 42.00 ₪
            מזומן 100.00 ₪
            עודף 58.00 ₪
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(42.0, result.amount!!, 0.001)
    }

    @Test
    fun `parse prefers total over subtotal in english`() {
        val text = """
            Coffee Corner
            Subtotal 26.00
            Total 29.50
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(29.5, result.amount!!, 0.001)
    }

    @Test
    fun `parse amount with shekel word as currency hint`() {
        val text = """
            חנות הספרים
            לתשלום 55 שח
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(55.0, result.amount!!, 0.001)
        assertEquals("ILS", result.currency)
    }

    // ── Amounts of a thousand and up ──────────────────────────────────────────

    /**
     * The worst of the lot, and it was one character of regex.
     *
     * `AMOUNT_PATTERN` offered a comma-grouped branch before a plain one, and the
     * grouped branch allowed *zero* groups — so on a bare `5310.00` it matched
     * `531` and the engine accepted it without ever trying the plain branch.
     * Every total of a thousand or more written without a thousands separator was
     * read as roughly a tenth of itself, decimals discarded, with no sign that
     * anything had gone wrong. Israeli thermal printers routinely omit the
     * separator, so this hit precisely the large receipts worth claiming.
     */
    @Test
    fun `a four figure total without a thousands separator is read whole`() {
        val text = """
            מוסך מרכזי
            סה"כ לתשלום 5310.00 ₪
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(5310.0, result.amount!!, 0.001)
    }

    @Test
    fun `a five figure total is read whole`() {
        val result = parser.parse("""
            סוכנות נסיעות
            לתשלום 12345.67
        """.trimIndent())

        assertEquals(12345.67, result.amount!!, 0.001)
    }

    /** The grouped form still parses; the branch was narrowed, not removed. */
    @Test
    fun `a total written with a thousands separator still parses`() {
        val result = parser.parse("""
            חנות רהיטים
            סה"כ לתשלום 1,250.50
        """.trimIndent())

        assertEquals(1250.5, result.amount!!, 0.001)
    }

    // ── Tax lines ─────────────────────────────────────────────────────────────

    /**
     * The most common shape an Israeli total takes, and the parser used to
     * penalise it.
     *
     * `סה"כ כולל מע"מ` carries a tax word, so a bare "מע"מ" match scored it as if
     * it were the tax line: it survived on the largest-amount tie-break but came
     * back at LOW confidence with `amountNearTotalKeyword = false`, which is what
     * the merger arbitrates on — so it lost to any English "total" the Latin pass
     * happened to find.
     */
    @Test
    fun `a total stated as including vat is the total`() {
        val text = """
            סופר פארם
            סה"כ לפני מע"מ 100.00
            מע"מ 18% 18.00
            סה"כ כולל מע"מ 118.00
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(118.0, result.amount!!, 0.001)
        assertTrue(result.amountNearTotalKeyword)
    }

    /**
     * And the pre-tax line is still not the total, even though it also says
     * סה"כ. Here the item costs more than the tax-exclusive subtotal is worth
     * confusing it with, so the largest-amount tie-break cannot rescue the
     * answer — only reading the qualifier does.
     */
    @Test
    fun `the pre-tax subtotal loses to the tax-inclusive total`() {
        val text = """
            אלקטרוניקה
            מקרר 4500.00
            סה"כ לפני מע"מ 4500.00
            מע"מ 18% 810.00
            סה"כ כולל מע"מ 5310.00
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(5310.0, result.amount!!, 0.001)
        assertTrue(result.amountNearTotalKeyword)
    }

    // ── Hebrew the way OCR actually reads it ──────────────────────────────────

    /**
     * `ך` for `כ` is the commonest thing an OCR engine does to Hebrew receipt
     * type, and before the final forms were folded it meant the total label was
     * not found at all.
     */
    @Test
    fun `a total label misread with a final kaf is still a total label`() {
        val result = parser.parse("""
            פיצריה
            סה"ך 88.00
        """.trimIndent())

        assertEquals(88.0, result.amount!!, 0.001)
        assertTrue(result.amountNearTotalKeyword)
    }

    @Test
    fun `a vat line misread with a final mem is still discounted`() {
        val result = parser.parse("""
            מרכול
            מע"ם 18% 12.00
            סה"כ 78.00
        """.trimIndent())

        assertEquals(78.0, result.amount!!, 0.001)
    }

    @Test
    fun `sum-including and charge-amount labels are recognised as totals`() {
        listOf(
            "בית קפה\nסכום כולל 47.00" to 47.0,
            "חניון העיר\nסכום החיוב 32.00" to 32.0,
            "מוסך\nיתרה לתשלום 250.00" to 250.0,
        ).forEach { (text, expected) ->
            val result = parser.parse(text)
            assertEquals(text, expected, result.amount!!, 0.001)
            assertTrue(text, result.amountNearTotalKeyword)
        }
    }

    /**
     * "שח" sits inside ordinary Hebrew words — משחק, שחור, משחקייה — and was
     * matched as a substring, so a shop with one in its name declared every
     * receipt priced in shekels whatever it actually said.
     */
    @Test
    fun `a merchant name containing the shekel letters is not a currency`() {
        val result = parser.parse("""
            משחקיית הילדים
            Total 45.00
        """.trimIndent())

        assertNull(result.currency)
    }

    @Test
    fun `the shekel word as a whole token is still a currency`() {
        val result = parser.parse("""
            חנות הספרים
            לתשלום 55 שח
        """.trimIndent())

        assertEquals("ILS", result.currency)
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

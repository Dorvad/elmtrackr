package com.elmtrackr.app.domain.money

import com.elmtrackr.app.domain.text.BidiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

/**
 * The currency requirements of the globalisation pass, asserted as properties
 * rather than as exact strings: the precise glyph and its position come from the
 * platform's CLDR data and move between Android releases, so pinning
 * "$1,234.57" would fail on a data update that is not a defect.
 */
class CurrencyGlobalizationTest {

    private val english = Locale.forLanguageTag("en-US")
    private val hebrew = Locale.forLanguageTag("he-IL")

    // ── Zero-decimal currencies ──────────────────────────────────────────────

    @Test
    fun `a zero-decimal currency shows no decimal part`() {
        // JPY has no minor unit. "¥1,235" is right; "¥1,234.57" is not money.
        val yen = Money.of(BigDecimal("1234.5678"), "JPY")

        assertEquals(0, yen.fractionDigits)
        listOf(english, hebrew).forEach { locale ->
            val text = MoneyFormat.format(yen, locale)
            assertFalse("$locale rendered a decimal separator: $text", text.contains("."))
        }
    }

    @Test
    fun `a zero-decimal amount rounds rather than truncates`() {
        assertEquals(BigDecimal("1235"), Money.of(BigDecimal("1234.5"), "JPY").amount)
        assertEquals(BigDecimal("1234"), Money.of(BigDecimal("1234.4"), "JPY").amount)
    }

    @Test
    fun `zero-decimal currencies stay exact when summed`() {
        val a = Money.of(BigDecimal("100"), "KRW")
        val b = Money.of(BigDecimal("250"), "KRW")

        assertEquals(Money.of(BigDecimal("350"), "KRW"), a + b)
        assertEquals(0, (a + b).fractionDigits)
    }

    // ── Three-decimal currencies ─────────────────────────────────────────────

    @Test
    fun `a three-decimal currency keeps all three digits`() {
        // KWD, BHD, OMR, JOD and TND all use three.
        listOf("KWD", "BHD", "OMR", "JOD", "TND").forEach { code ->
            val amount = Money.of(BigDecimal("1234.5678"), code)

            assertEquals("$code lost precision", 3, amount.fractionDigits)
            assertEquals(BigDecimal("1234.568"), amount.amount)
        }
    }

    @Test
    fun `a three-decimal amount renders three digits in both locales`() {
        val dinar = Money.of(BigDecimal("1234.568"), "KWD")

        listOf(english, hebrew).forEach { locale ->
            val digits = MoneyFormat.format(dinar, locale)
                .substringAfterLast('.', "")
                .takeWhile { it.isDigit() }
            assertEquals("$locale rendered the wrong scale", 3, digits.length)
        }
    }

    @Test
    fun `a three-decimal tax split still adds up exactly`() {
        val fee = ProjectFee.from(BigDecimal("1000"), "KWD", TaxMode.EXCLUSIVE, BigDecimal("18"))

        assertEquals(fee.clientTotal, fee.base + fee.tax)
        assertEquals(3, fee.clientTotal.fractionDigits)
    }

    // ── Ambiguous symbols ────────────────────────────────────────────────────

    @Test
    fun `CLDR keeps dollar symbols distinct between currencies`() {
        // The premise behind formattedWithCode: there is no symbol *collision*
        // to resolve, because CLDR already distinguishes these. If a future data
        // change made two of them identical, this fails and the assumption gets
        // revisited.
        listOf(english, hebrew).forEach { locale ->
            val symbols = listOf("USD", "CAD", "AUD", "NZD", "SGD", "HKD")
                .map { Currency.getInstance(it).getSymbol(locale) }

            assertEquals(
                "$locale produced a symbol collision: $symbols",
                symbols.size,
                symbols.toSet().size,
            )
        }
    }

    @Test
    fun `an amount whose symbol is a bare glyph is flagged as needing its code`() {
        // "$1,200.00" in a list that also holds shekels does not say which
        // currency the row is, even though "$" is unambiguously USD to CLDR.
        assertTrue(MoneyFormat.symbolNeedsCode(Money.of("1200", "USD"), english))
        assertTrue(MoneyFormat.symbolNeedsCode(Money.of("1200", "ILS"), english))
        assertTrue(MoneyFormat.symbolNeedsCode(Money.of("1200", "JPY"), hebrew))
    }

    @Test
    fun `a currency already rendered as its own code needs no help`() {
        // KWD has no distinct glyph in these locales, so it already reads as
        // "KWD" and repeating the code would be noise.
        val needsCode = MoneyFormat.symbolNeedsCode(Money.of("1200", "KWD"), english)

        assertEquals(
            Currency.getInstance("KWD").getSymbol(english) != "KWD",
            needsCode,
        )
    }

    @Test
    fun `an unknown code is never flagged, because it already prints as a code`() {
        assertFalse(MoneyFormat.symbolNeedsCode(Money.of("1200", "XYZ"), english))
    }

    @Test
    fun `the number-only rendering carries no symbol and no code`() {
        val text = MoneyFormat.formatNumber(Money.of("1234.56", "USD"), english)

        assertFalse(text.contains("$"))
        assertFalse(text.contains("USD"))
        assertTrue(text.contains("1,234.56"))
    }

    // ── ISO 4217 ─────────────────────────────────────────────────────────────

    @Test
    fun `real ISO 4217 codes are accepted as known`() {
        listOf("USD", "ILS", "EUR", "GBP", "JPY", "KWD").forEach {
            assertTrue("$it should be known", CurrencyScales.isKnownIso4217(it))
        }
    }

    @Test
    fun `a plausible-looking but unassigned code is not known`() {
        // The typo this catches: USB for USD.
        listOf("USB", "XXY", "ZZZ", "ABC").forEach {
            assertFalse("$it should not be known", CurrencyScales.isKnownIso4217(it))
        }
    }

    @Test
    fun `malformed codes are rejected by both checks`() {
        listOf(null, "", "US", "USDD", "us1", "12", "$").forEach {
            assertFalse("$it passed the syntax check", CurrencyScales.isValidCode(it))
            assertFalse("$it passed the ISO check", CurrencyScales.isKnownIso4217(it))
        }
    }

    @Test
    fun `lowercase input is normalised before either check`() {
        assertTrue(CurrencyScales.isValidCode("usd"))
        assertTrue(CurrencyScales.isKnownIso4217(" usd "))
    }

    @Test
    fun `an unknown code still loads and still displays`() {
        // The lenient check is what keeps a project saved under a code this
        // build has no data for from becoming unreadable. Losing access to your
        // own project would be a far worse failure than accepting an odd code.
        assertTrue(CurrencyScales.isValidCode("XYZ"))
        assertFalse(CurrencyScales.isKnownIso4217("XYZ"))

        val text = MoneyFormat.format(Money.of("1234.56", "XYZ"), english)
        assertTrue("the code should still be shown: $text", BidiText.strip(text).contains("XYZ"))
    }

    @Test
    fun `an unknown code falls back to two decimals`() {
        assertEquals(2, CurrencyScales.digitsFor("XYZ"))
    }

    // ── No conversion, no combining ──────────────────────────────────────────

    @Test
    fun `adding two currencies is refused rather than converted`() {
        val usd = Money.of("100", "USD")
        val ils = Money.of("100", "ILS")

        val failure = runCatching { usd + ils }.exceptionOrNull()

        assertTrue(failure is CurrencyMismatchException)
    }

    @Test
    fun `a multi-currency total offers no single figure`() {
        val totals = MoneyByCurrency.from(listOf(Money.of("100", "USD"), Money.of("100", "ILS")))

        assertEquals(listOf("ILS", "USD"), totals.currencies)
        // The only API that yields one Money refuses when there are two.
        assertEquals(null, totals.singleOrNull())
    }

    // ── Bidi inside formatted money ──────────────────────────────────────────

    @Test
    fun `the code-first fallback isolates the code`() {
        // Reached only for a currency the platform has no data for, where the
        // code is placed by hand rather than by a CLDR pattern.
        val text = MoneyFormat.format(Money.of("1234.56", "XYZ"), hebrew)

        assertTrue("expected the code isolated: $text", BidiText.containsIsolates(text))
        assertTrue(BidiText.strip(text).contains("XYZ"))
    }

    @Test
    fun `a known currency needs no isolates because CLDR supplies its own marks`() {
        // he-IL already embeds U+200F in its currency pattern, so adding
        // isolates on top would be redundant.
        val text = MoneyFormat.format(Money.of("1234.56", "USD"), hebrew)

        assertFalse(BidiText.containsIsolates(text))
    }
}

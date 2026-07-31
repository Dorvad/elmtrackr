package com.elmtrackr.app.domain.money

import com.elmtrackr.app.domain.text.BidiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

/**
 * Money and date rendering must go through the platform formatters. These tests
 * assert the *properties* that matter — the symbol appears, the digits are
 * grouped for the locale, the scale is right — rather than exact strings, which
 * vary with the JDK's CLDR data.
 */
class MoneyFormatTest {

    private val english = Locale.forLanguageTag("en-US")
    private val hebrew = Locale.forLanguageTag("he-IL")

    @Test
    fun `an amount is rendered with its currency symbol`() {
        val output = MoneyFormat.format(Money.of("1234.56", "USD"), english)
        assertTrue(output, output.contains("$"))
        assertTrue(output, output.contains("1,234.56"))
    }

    @Test
    fun `the shekel renders in Hebrew without hand-built concatenation`() {
        val output = MoneyFormat.format(Money.of("1234.56", "ILS"), hebrew)
        assertTrue(output, output.contains("₪"))
        // The number itself is present; where the symbol goes is the locale's call.
        assertTrue(output, output.contains("1,234.56") || output.contains("1234.56"))
    }

    @Test
    fun `the same amount renders differently per locale`() {
        val money = Money.of("1234.56", "EUR")
        val germany = Locale.forLanguageTag("de-DE")
        assertTrue(MoneyFormat.format(money, germany) != MoneyFormat.format(money, english))
    }

    @Test
    fun `a zero-decimal currency shows no fraction digits`() {
        val output = MoneyFormat.format(Money.of("1234", "JPY"), english)
        assertFalse(output, output.contains(".00"))
        assertTrue(output, output.contains("1,234"))
    }

    @Test
    fun `a currency the platform does not know still renders the code and number`() {
        val output = MoneyFormat.format(Money.of("100", "ZZZ"), english)
        assertTrue(output, output.contains("ZZZ"))
        assertTrue(output, output.contains("100"))
    }

    @Test
    fun `large amounts are grouped, not printed in scientific notation`() {
        val output = MoneyFormat.format(Money.of(BigDecimal("1234567.89"), "USD"), english)
        assertTrue(output, output.contains("1,234,567.89"))
        assertFalse(output, output.contains("E"))
    }

    @Test
    fun `tax rates render as percentages`() {
        assertTrue(MoneyFormat.formatTaxRate(BigDecimal("18"), english).contains("18"))
        assertTrue(MoneyFormat.formatTaxRate(BigDecimal("18"), english).contains("%"))
        assertTrue(MoneyFormat.formatTaxRate(BigDecimal("17.5"), english).contains("17.5"))
    }

    @Test
    fun `a whole tax rate shows no trailing decimals`() {
        assertFalse(MoneyFormat.formatTaxRate(BigDecimal("18.00"), english).contains(".0"))
    }

    /**
     * The unit words moved to the resource layer in the localisation pass, so
     * "h" and "m" are no longer built into the formatter — a Hebrew UI needs
     * שע׳ and דק׳, and only the resources know which language is on screen.
     * What stays here is the split into parts and the localised numerals; the
     * assembled string is covered by ProjectDurationRenderTest in both locales.
     */
    @Test
    fun `durations split into hour and minute parts`() {
        MoneyFormat.durationParts(480, english).let {
            assertEquals(8, it.hours)
            assertEquals(0, it.minutes)
        }
        MoneyFormat.durationParts(510, english).let {
            assertEquals(8, it.hours)
            assertEquals(30, it.minutes)
        }
        MoneyFormat.durationParts(0, english).let {
            assertEquals(0, it.hours)
            assertEquals(0, it.minutes)
        }
    }

    @Test
    fun `durations beyond a day still count in hours`() {
        val parts = MoneyFormat.durationParts(6001, english)

        assertEquals(100, parts.hours)
        assertEquals(1, parts.minutes)
    }

    @Test
    fun `duration numerals are grouped for the locale and isolated`() {
        val parts = MoneyFormat.durationParts(60 * 2400, english)

        // 2,400 hours: grouped by the locale, not printed as raw digits.
        assertEquals("2,400", BidiText.strip(parts.hoursText))
        assertTrue(BidiText.containsIsolates(parts.hoursText))
    }

    @Test
    fun `a negative duration is clamped rather than rendered with a sign`() {
        val parts = MoneyFormat.durationParts(-90, english)

        assertEquals(0, parts.hours)
        assertEquals(0, parts.minutes)
    }
}

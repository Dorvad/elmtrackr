package com.elmtrackr.app.domain.money

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

    @Test
    fun `durations render as hours and minutes`() {
        assertEquals("8h", MoneyFormat.formatMinutes(480, english))
        assertEquals("8h 30m", MoneyFormat.formatMinutes(510, english))
        assertEquals("0h", MoneyFormat.formatMinutes(0, english))
        assertEquals("40h", MoneyFormat.formatMinutes(2400, english))
    }

    @Test
    fun `durations beyond a day still read in hours`() {
        assertEquals("100h 1m", MoneyFormat.formatMinutes(6001, english))
    }
}

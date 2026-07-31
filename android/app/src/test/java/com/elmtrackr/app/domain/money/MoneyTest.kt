package com.elmtrackr.app.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `amount is held at the currency display scale`() {
        assertEquals(BigDecimal("10.00"), Money.of(BigDecimal("10"), "ILS").amount)
        assertEquals(BigDecimal("10.00"), Money.of(BigDecimal("10.004"), "USD").amount)
    }

    @Test
    fun `zero-decimal currencies keep no minor units`() {
        val yen = Money.of(BigDecimal("1234.56"), "JPY")
        assertEquals(0, yen.fractionDigits)
        assertEquals(BigDecimal("1235"), yen.amount)
    }

    @Test
    fun `currencies outside the app picker still scale correctly`() {
        // Not in CurrencyCode; resolved from the platform currency table.
        assertEquals(2, CurrencyScales.digitsFor("SEK"))
        assertEquals(0, CurrencyScales.digitsFor("KRW"))
        assertEquals(3, CurrencyScales.digitsFor("BHD"))
    }

    @Test
    fun `unknown currency codes fall back to two digits`() {
        assertEquals(2, CurrencyScales.digitsFor("ZZZ"))
        assertEquals(2, CurrencyScales.digitsFor(""))
    }

    @Test
    fun `rounding is half up at the display scale`() {
        assertEquals(BigDecimal("0.13"), Money.of(BigDecimal("0.125"), "USD").amount)
        assertEquals(BigDecimal("0.12"), Money.of(BigDecimal("0.124"), "USD").amount)
    }

    @Test
    fun `codes are normalised to upper case`() {
        assertEquals("ILS", Money.of(BigDecimal.ONE, " ils ").currencyCode)
    }

    @Test
    fun `equality ignores trailing-zero scale differences`() {
        assertEquals(Money.of("10", "ILS"), Money.of("10.00", "ILS"))
        assertEquals(Money.of("10", "ILS").hashCode(), Money.of("10.00", "ILS").hashCode())
    }

    @Test
    fun `same amount in a different currency is not equal`() {
        assertNotEquals(Money.of("10.00", "ILS"), Money.of("10.00", "USD"))
    }

    @Test
    fun `addition and subtraction stay exact`() {
        val a = Money.of("0.10", "USD")
        val b = Money.of("0.20", "USD")
        // The classic binary-floating-point failure: 0.1 + 0.2 != 0.3.
        assertEquals(Money.of("0.30", "USD"), a + b)
        assertEquals(Money.of("0.10", "USD"), (a + b) - b)
    }

    @Test
    fun `summing many small amounts does not drift`() {
        val cent = Money.of("0.01", "USD")
        val total = Money.sum(List(100) { cent }, "USD")
        assertEquals(Money.of("1.00", "USD"), total)
    }

    @Test
    fun `combining different currencies is refused`() {
        val ils = Money.of("10.00", "ILS")
        val usd = Money.of("10.00", "USD")
        assertThrows(CurrencyMismatchException::class.java) { ils + usd }
        assertThrows(CurrencyMismatchException::class.java) { ils - usd }
        assertThrows(CurrencyMismatchException::class.java) { ils.compareTo(usd) }
    }

    @Test
    fun `sign helpers reflect the amount`() {
        assertTrue(Money.zero("ILS").isZero)
        assertFalse(Money.zero("ILS").isPositive)
        assertTrue(Money.of("0.01", "ILS").isPositive)
        assertTrue(Money.of("-0.01", "ILS").isNegative)
    }

    @Test
    fun `negative balances clamp to zero`() {
        assertEquals(Money.zero("ILS"), Money.of("-5.00", "ILS").coerceAtLeastZero())
        assertEquals(Money.of("5.00", "ILS"), Money.of("5.00", "ILS").coerceAtLeastZero())
    }

    @Test
    fun `comparison orders amounts`() {
        assertTrue(Money.of("10.00", "ILS") > Money.of("9.99", "ILS"))
        assertEquals(0, Money.of("10.00", "ILS").compareTo(Money.of("10", "ILS")))
    }

    @Test
    fun `persisted form is plain decimal and round-trips`() {
        val money = Money.of("1234567.89", "ILS")
        assertEquals("1234567.89", money.toPersistedString())
        assertEquals(money, Money.fromPersisted(money.toPersistedString(), "ILS"))
    }

    @Test
    fun `very large amounts avoid scientific notation`() {
        val big = Money.of(BigDecimal("1E+9"), "USD")
        assertEquals("1000000000.00", big.toPersistedString())
    }

    @Test
    fun `unreadable persisted amounts read as zero instead of throwing`() {
        assertEquals(Money.zero("ILS"), Money.fromPersisted("not-a-number", "ILS"))
        assertEquals(Money.zero("ILS"), Money.fromPersisted(null, "ILS"))
        assertEquals(Money.zero("ILS"), Money.fromPersisted("", "ILS"))
    }

    @Test
    fun `multiplication uses calculation precision then rounds once`() {
        // 100.00 x 17.5% = 17.50 exactly.
        assertEquals(Money.of("17.50", "ILS"), Money.of("100.00", "ILS") * BigDecimal("0.175"))
        // A repeating factor rounds at the display scale, not before it.
        val third = BigDecimal.ONE.divide(BigDecimal("3"), MoneyPolicy.CALCULATION_CONTEXT)
        assertEquals(Money.of("33.33", "ILS"), Money.of("100.00", "ILS") * third)
    }

    @Test
    fun `calculation context is wide enough for repeating decimals`() {
        assertEquals(34, MoneyPolicy.CALCULATION_CONTEXT.precision)
    }

    @Test
    fun `code validation accepts ISO 4217 shapes only`() {
        assertTrue(CurrencyScales.isValidCode("ILS"))
        assertTrue(CurrencyScales.isValidCode("usd"))
        assertFalse(CurrencyScales.isValidCode("IL"))
        assertFalse(CurrencyScales.isValidCode("ILSX"))
        assertFalse(CurrencyScales.isValidCode("I1S"))
        assertFalse(CurrencyScales.isValidCode(null))
    }
}

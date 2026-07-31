package com.elmtrackr.app.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The type that makes cross-currency addition impossible.
 *
 * ElmTrackr has no exchange rates. The guarantee under test is that there is no
 * API here that returns one combined figure across currencies — the only way to
 * read a total is per currency, or via [MoneyByCurrency.singleOrNull] which
 * refuses when more than one is present.
 */
class MoneyByCurrencyTest {

    @Test
    fun `amounts in one currency add up`() {
        val totals = MoneyByCurrency.from(
            listOf(Money.of("100.00", "USD"), Money.of("50.50", "USD")),
        )

        assertEquals(Money.of("150.50", "USD"), totals["USD"])
        assertEquals(listOf("USD"), totals.currencies)
        assertEquals(Money.of("150.50", "USD"), totals.singleOrNull())
    }

    @Test
    fun `amounts in different currencies stay in separate buckets`() {
        val totals = MoneyByCurrency.from(
            listOf(
                Money.of("100.00", "USD"),
                Money.of("200.00", "ILS"),
                Money.of("50.00", "USD"),
            ),
        )

        assertEquals(Money.of("150.00", "USD"), totals["USD"])
        assertEquals(Money.of("200.00", "ILS"), totals["ILS"])
        assertEquals(listOf("ILS", "USD"), totals.currencies)
        assertEquals(2, totals.size)
    }

    @Test
    fun `there is no single total once two currencies are present`() {
        val totals = MoneyByCurrency.from(
            listOf(Money.of("100.00", "USD"), Money.of("200.00", "ILS")),
        )
        // The caller cannot accidentally render one combined number.
        assertNull(totals.singleOrNull())
    }

    @Test
    fun `an absent currency reads as zero in that currency, not as an error`() {
        val totals = MoneyByCurrency.of(Money.of("100.00", "USD"))

        assertEquals(Money.zero("EUR"), totals["EUR"])
        assertEquals("EUR", totals["EUR"].currencyCode)
    }

    @Test
    fun `zero buckets are dropped so a report shows only real currencies`() {
        val totals = MoneyByCurrency.from(
            listOf(Money.zero("USD"), Money.of("10.00", "ILS")),
        )

        assertEquals(listOf("ILS"), totals.currencies)
        assertEquals(1, totals.size)
    }

    @Test
    fun `an empty total is empty`() {
        assertTrue(MoneyByCurrency.EMPTY.isEmpty)
        assertFalse(MoneyByCurrency.EMPTY.isNotEmpty)
        assertTrue(MoneyByCurrency.EMPTY.currencies.isEmpty())
        assertNull(MoneyByCurrency.EMPTY.singleOrNull())
    }

    @Test
    fun `adding two totals merges per currency`() {
        val a = MoneyByCurrency.from(listOf(Money.of("100.00", "USD"), Money.of("10.00", "ILS")))
        val b = MoneyByCurrency.from(listOf(Money.of("25.00", "USD"), Money.of("5.00", "EUR")))

        val merged = a + b

        assertEquals(Money.of("125.00", "USD"), merged["USD"])
        assertEquals(Money.of("10.00", "ILS"), merged["ILS"])
        assertEquals(Money.of("5.00", "EUR"), merged["EUR"])
        assertEquals(listOf("EUR", "ILS", "USD"), merged.currencies)
    }

    @Test
    fun `adding a single amount merges into its own bucket`() {
        val totals = MoneyByCurrency.of(Money.of("100.00", "USD")).plus(Money.of("1.00", "USD"))
        assertEquals(Money.of("101.00", "USD"), totals["USD"])
    }

    @Test
    fun `subtraction is per currency and never negative`() {
        val billed = MoneyByCurrency.from(listOf(Money.of("100.00", "USD"), Money.of("50.00", "ILS")))
        val paid = MoneyByCurrency.from(listOf(Money.of("30.00", "USD"), Money.of("80.00", "ILS")))

        val outstanding = billed.minusFlooredAtZero(paid)

        assertEquals(Money.of("70.00", "USD"), outstanding["USD"])
        // Overpaid in shekels: a balance owed cannot be negative.
        assertEquals(Money.zero("ILS"), outstanding["ILS"])
    }

    @Test
    fun `entries come back in the same order as the currency list`() {
        val totals = MoneyByCurrency.from(
            listOf(Money.of("1.00", "USD"), Money.of("2.00", "EUR"), Money.of("3.00", "ILS")),
        )

        assertEquals(listOf("EUR", "ILS", "USD"), totals.currencies)
        assertEquals(
            listOf(Money.of("2.00", "EUR"), Money.of("3.00", "ILS"), Money.of("1.00", "USD")),
            totals.entries(),
        )
    }

    @Test
    fun `a zero-decimal currency keeps its own scale`() {
        val totals = MoneyByCurrency.from(listOf(Money.of("1000", "JPY"), Money.of("500", "JPY")))
        assertEquals(Money.of("1500", "JPY"), totals["JPY"])
    }
}

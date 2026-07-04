package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.CurrencyCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyFormatterTest {
    @Test
    fun `formats each supported currency with its configured symbol`() {
        val expected = mapOf(
            CurrencyCode.ILS to "₪1,234.50",
            CurrencyCode.USD to "$1,234.50",
            CurrencyCode.EUR to "€1,234.50",
            CurrencyCode.GBP to "£1,234.50",
            CurrencyCode.CAD to "CA$1,234.50",
            CurrencyCode.AUD to "A$1,234.50",
            CurrencyCode.JPY to "¥1,235",
            CurrencyCode.CHF to "CHF 1,234.50",
        )

        expected.forEach { (currency, value) ->
            assertEquals(value, MoneyFormatter.format(1234.5, currency))
        }
    }

    @Test
    fun `unknown persisted currency falls back to ILS`() {
        assertEquals(CurrencyCode.ILS, CurrencyCode.from("unknown"))
    }

    @Test
    fun `string overload respects zero-decimal currencies like JPY`() {
        val formatted = MoneyFormatter.format(1234.5, "JPY")
        assertTrue("JPY must not show decimal minor units: $formatted", !formatted.contains("."))
        assertTrue(formatted.contains("1,235"))
    }

    @Test
    fun `string overload keeps two decimals for USD`() {
        assertTrue(MoneyFormatter.format(1234.5, "USD").contains("1,234.50"))
    }
}

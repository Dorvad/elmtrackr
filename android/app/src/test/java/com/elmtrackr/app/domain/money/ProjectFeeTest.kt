package com.elmtrackr.app.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * The single source of truth for project tax. The load-bearing property in every
 * case is that the three displayed numbers reconcile:
 * base + tax == client total.
 */
class ProjectFeeTest {

    private fun fee(
        entered: String,
        mode: TaxMode,
        rate: String = "0",
        currency: String = "ILS",
        label: String? = "VAT",
    ) = ProjectFee.from(BigDecimal(entered), currency, mode, BigDecimal(rate), label)

    // ── Tax disabled ──────────────────────────────────────────────────────────

    @Test
    fun `tax disabled charges the fee as entered`() {
        val result = fee("5000", TaxMode.NONE)
        assertEquals(Money.of("5000.00", "ILS"), result.base)
        assertEquals(Money.zero("ILS"), result.tax)
        assertEquals(Money.of("5000.00", "ILS"), result.clientTotal)
        assertEquals(TaxMode.NONE, result.taxMode)
    }

    @Test
    fun `tax disabled clears the rate and the label`() {
        val result = ProjectFee.from(BigDecimal("100"), "ILS", TaxMode.NONE, BigDecimal("18"), "VAT")
        assertEquals(BigDecimal.ZERO, result.taxRatePercent)
        assertNull(result.taxLabel)
    }

    @Test
    fun `a zero rate behaves exactly like tax disabled`() {
        val result = fee("100", TaxMode.EXCLUSIVE, rate = "0")
        assertEquals(TaxMode.NONE, result.taxMode)
        assertEquals(Money.zero("ILS"), result.tax)
        assertEquals(result.base, result.clientTotal)
    }

    // ── Tax-exclusive entry ───────────────────────────────────────────────────

    @Test
    fun `tax-exclusive adds tax on top of the entered fee`() {
        val result = fee("10000", TaxMode.EXCLUSIVE, rate = "18")
        assertEquals(Money.of("10000.00", "ILS"), result.base)
        assertEquals(Money.of("1800.00", "ILS"), result.tax)
        assertEquals(Money.of("11800.00", "ILS"), result.clientTotal)
    }

    @Test
    fun `tax-exclusive preserves the fee the user typed`() {
        val result = fee("1234.56", TaxMode.EXCLUSIVE, rate = "17")
        assertEquals(Money.of("1234.56", "ILS"), result.base)
        assertEquals(Money.of("1234.56", "ILS"), result.enteredAmount)
    }

    @Test
    fun `tax-exclusive reconciles for a fractional rate`() {
        val result = fee("999.99", TaxMode.EXCLUSIVE, rate = "17.5")
        assertEquals(result.clientTotal, result.base + result.tax)
    }

    // ── Tax-inclusive entry ───────────────────────────────────────────────────

    @Test
    fun `tax-inclusive preserves the client total the user typed`() {
        val result = fee("11800", TaxMode.INCLUSIVE, rate = "18")
        assertEquals(Money.of("11800.00", "ILS"), result.clientTotal)
        assertEquals(Money.of("10000.00", "ILS"), result.base)
        assertEquals(Money.of("1800.00", "ILS"), result.tax)
    }

    @Test
    fun `tax-inclusive entered amount is the client total`() {
        val result = fee("11800", TaxMode.INCLUSIVE, rate = "18")
        assertEquals(result.clientTotal, result.enteredAmount)
    }

    @Test
    fun `inclusive and exclusive are inverses of each other`() {
        val exclusive = fee("10000", TaxMode.EXCLUSIVE, rate = "18")
        val inclusive = ProjectFee.from(
            exclusive.clientTotal.amount, "ILS", TaxMode.INCLUSIVE, BigDecimal("18"), "VAT",
        )
        assertEquals(exclusive.base, inclusive.base)
        assertEquals(exclusive.tax, inclusive.tax)
        assertEquals(exclusive.clientTotal, inclusive.clientTotal)
    }

    // ── Repeating decimals ────────────────────────────────────────────────────

    @Test
    fun `inclusive 100 at 17 percent reconciles despite a repeating quotient`() {
        // 100 x 100 / 117 = 85.470085470085... — base rounds to 85.47 and tax is
        // taken as the residual so the three numbers still add up.
        val result = fee("100", TaxMode.INCLUSIVE, rate = "17")
        assertEquals(Money.of("100.00", "ILS"), result.clientTotal)
        assertEquals(Money.of("85.47", "ILS"), result.base)
        assertEquals(Money.of("14.53", "ILS"), result.tax)
        assertEquals(result.clientTotal, result.base + result.tax)
    }

    @Test
    fun `inclusive 100 at 3 percent reconciles`() {
        // 100 x 100 / 103 = 97.0873786407767...
        val result = fee("100", TaxMode.INCLUSIVE, rate = "3")
        assertEquals(Money.of("97.09", "ILS"), result.base)
        assertEquals(Money.of("2.91", "ILS"), result.tax)
        assertEquals(result.clientTotal, result.base + result.tax)
    }

    @Test
    fun `inclusive with a repeating rate reconciles`() {
        val result = fee("100", TaxMode.INCLUSIVE, rate = "33.333333")
        assertEquals(Money.of("100.00", "ILS"), result.clientTotal)
        assertEquals(result.clientTotal, result.base + result.tax)
    }

    @Test
    fun `base plus tax equals total for a wide sweep of amounts and rates`() {
        val rates = listOf("3", "5", "7.5", "12", "17", "18", "19", "20", "21", "23", "33.333333")
        val amounts = listOf("0.01", "0.03", "1", "9.99", "33.33", "100", "100.01", "999.99", "123456.78")
        for (rate in rates) {
            for (amount in amounts) {
                for (mode in listOf(TaxMode.EXCLUSIVE, TaxMode.INCLUSIVE)) {
                    val result = fee(amount, mode, rate = rate)
                    assertEquals(
                        "base + tax != total for $amount at $rate% ($mode)",
                        result.clientTotal,
                        result.base + result.tax,
                    )
                }
            }
        }
    }

    @Test
    fun `inclusive always preserves the entered total exactly`() {
        val rates = listOf("3", "17", "18", "33.333333")
        val amounts = listOf("0.01", "1", "99.99", "100", "12345.67")
        for (rate in rates) {
            for (amount in amounts) {
                val result = fee(amount, TaxMode.INCLUSIVE, rate = rate)
                assertEquals(Money.of(amount, "ILS"), result.clientTotal)
            }
        }
    }

    // ── Currencies ────────────────────────────────────────────────────────────

    @Test
    fun `a zero-decimal currency reconciles at its own scale`() {
        val result = fee("10000", TaxMode.INCLUSIVE, rate = "10", currency = "JPY")
        assertEquals(0, result.clientTotal.fractionDigits)
        assertEquals(Money.of("10000", "JPY"), result.clientTotal)
        assertEquals(Money.of("9091", "JPY"), result.base)
        assertEquals(Money.of("909", "JPY"), result.tax)
        assertEquals(result.clientTotal, result.base + result.tax)
    }

    @Test
    fun `each fee keeps its own currency`() {
        assertEquals("ILS", fee("100", TaxMode.EXCLUSIVE, rate = "18", currency = "ILS").currencyCode)
        assertEquals("USD", fee("100", TaxMode.EXCLUSIVE, rate = "18", currency = "USD").currencyCode)
        assertEquals("EUR", fee("100", TaxMode.EXCLUSIVE, rate = "18", currency = "eur").currencyCode)
    }

    // ── Rate normalisation and labels ─────────────────────────────────────────

    @Test
    fun `rates outside zero to one hundred are clamped`() {
        assertEquals(BigDecimal.ZERO, ProjectFee.normalizeRate(TaxMode.EXCLUSIVE, BigDecimal("-5")))
        assertEquals(BigDecimal("100"), ProjectFee.normalizeRate(TaxMode.EXCLUSIVE, BigDecimal("250")))
        assertEquals(BigDecimal("18"), ProjectFee.normalizeRate(TaxMode.EXCLUSIVE, BigDecimal("18")))
        assertEquals(BigDecimal.ZERO, ProjectFee.normalizeRate(TaxMode.NONE, BigDecimal("18")))
    }

    @Test
    fun `a blank label is stored as absent`() {
        val result = ProjectFee.from(BigDecimal("100"), "ILS", TaxMode.EXCLUSIVE, BigDecimal("18"), "   ")
        assertNull(result.taxLabel)
    }

    @Test
    fun `the label is generic and user supplied`() {
        listOf("VAT", "GST", "Sales Tax", "Tax", "מעממ").forEach { label ->
            val result = ProjectFee.from(BigDecimal("100"), "ILS", TaxMode.EXCLUSIVE, BigDecimal("18"), label)
            assertEquals(label, result.taxLabel)
        }
    }

    // ── Persistence reconstruction ────────────────────────────────────────────

    @Test
    fun `persisted amounts are trusted when they reconcile`() {
        val restored = ProjectFee.fromPersisted(
            base = Money.of("10000.00", "ILS"),
            tax = Money.of("1800.00", "ILS"),
            clientTotal = Money.of("11800.00", "ILS"),
            taxMode = TaxMode.EXCLUSIVE,
            taxRatePercent = BigDecimal("18"),
            taxLabel = "VAT",
        )
        assertEquals(Money.of("1800.00", "ILS"), restored.tax)
    }

    @Test
    fun `an inconsistent persisted triple is repaired rather than throwing`() {
        val restored = ProjectFee.fromPersisted(
            base = Money.of("100.00", "ILS"),
            // Wrong on disk: does not bridge base and total.
            tax = Money.of("5.00", "ILS"),
            clientTotal = Money.of("118.00", "ILS"),
            taxMode = TaxMode.EXCLUSIVE,
            taxRatePercent = BigDecimal("18"),
            taxLabel = "VAT",
        )
        assertEquals(Money.of("18.00", "ILS"), restored.tax)
        assertEquals(restored.clientTotal, restored.base + restored.tax)
    }

    @Test
    fun `a persisted fee reconstructs what from produced`() {
        val original = fee("100", TaxMode.INCLUSIVE, rate = "17")
        val restored = ProjectFee.fromPersisted(
            original.base, original.tax, original.clientTotal,
            original.taxMode, original.taxRatePercent, original.taxLabel,
        )
        assertEquals(original, restored)
    }

    @Test
    fun `zero builds a disabled tax fee`() {
        val result = ProjectFee.zero("USD")
        assertEquals(Money.zero("USD"), result.clientTotal)
        assertEquals(TaxMode.NONE, result.taxMode)
    }

    @Test
    fun `tax mode parses persisted values safely`() {
        assertEquals(TaxMode.INCLUSIVE, TaxMode.fromPersisted("inclusive"))
        assertEquals(TaxMode.EXCLUSIVE, TaxMode.fromPersisted("EXCLUSIVE"))
        assertEquals(TaxMode.NONE, TaxMode.fromPersisted("none"))
        assertEquals(TaxMode.NONE, TaxMode.fromPersisted("who-knows"))
        assertEquals(TaxMode.NONE, TaxMode.fromPersisted(null))
        assertEquals("inclusive", TaxMode.INCLUSIVE.toWire())
    }
}

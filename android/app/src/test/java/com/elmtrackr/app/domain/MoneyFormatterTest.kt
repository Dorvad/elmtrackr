package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.MoneyFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

/**
 * Money as CLDR writes it, in each language the app ships.
 *
 * The old tests pinned the hand-concatenated symbol-first output — `"₪1,234.50"`,
 * `"CHF 1,234.50"` — produced by gluing [CurrencyCode.symbol] onto a [Locale.US] number.
 * Those literals were the defect, not the contract: a Hebrew reader writes
 * `1,234.50 ₪`, and the placement lived in a string concatenation no locale could reach.
 *
 * Where a literal still appears below it is because that exact string is worth pinning —
 * chiefly that English output did *not* change. Everything else is asserted as a
 * property, so the tests state the rule rather than restating one JDK's CLDR tables.
 */
class MoneyFormatterTest {

    private lateinit var original: Locale

    @Before fun captureLocale() { original = Locale.getDefault() }
    @After fun restoreLocale() { Locale.setDefault(original) }

    private val hebrew = Locale.forLanguageTag("iw")
    private val arabic = Locale.forLanguageTag("ar")
    private val russian = Locale.forLanguageTag("ru")

    @Test
    fun `English output is unchanged for every currency but CHF`() {
        // The regression guard that matters most: an English-reading user should see no
        // difference at all from this change.
        val expected = mapOf(
            CurrencyCode.ILS to "₪1,234.50",
            CurrencyCode.USD to "$1,234.50",
            CurrencyCode.EUR to "€1,234.50",
            CurrencyCode.GBP to "£1,234.50",
            CurrencyCode.CAD to "CA$1,234.50",
            CurrencyCode.AUD to "A$1,234.50",
            CurrencyCode.JPY to "¥1,235",
        )
        expected.forEach { (currency, value) ->
            assertEquals(value, MoneyFormatter.format(1234.5, currency, Locale.US))
        }
    }

    @Test
    fun `CHF loses the space the old hand-rolled rule inserted`() {
        // The one visible English change. The old code added a space whenever the symbol
        // ended in a letter; CLDR does not for en-US. Pinned rather than left to be
        // discovered as a surprise in a screenshot review.
        assertEquals("CHF1,234.50", MoneyFormatter.format(1234.5, CurrencyCode.CHF, Locale.US))
    }

    @Test
    fun `Hebrew puts the symbol after the number`() {
        // The defect this wave existed to fix. Asserted positionally rather than as a
        // literal because the string also carries RTL direction marks.
        val formatted = MoneyFormatter.format(1234.5, CurrencyCode.ILS, hebrew)
        assertTrue("expected a shekel sign in $formatted", formatted.contains("₪"))
        assertTrue(
            "the symbol should follow the digits in Hebrew: $formatted",
            formatted.indexOf("₪") > formatted.indexOfFirst { it in '0'..'9' },
        )
    }

    @Test
    fun `English puts the symbol before the number`() {
        val formatted = MoneyFormatter.format(1234.5, CurrencyCode.ILS, Locale.US)
        assertTrue(formatted.indexOf("₪") < formatted.indexOfFirst { it in '0'..'9' })
    }

    @Test
    fun `Arabic renders the amount in Arabic-Indic digits`() {
        val formatted = MoneyFormatter.format(1234.5, CurrencyCode.ILS, arabic)
        assertTrue("expected Arabic-Indic digits in $formatted", formatted.any { it in '٠'..'٩' })
        assertFalse("expected no ASCII digits in $formatted", formatted.any { it in '0'..'9' })
    }

    @Test
    fun `Russian uses a comma decimal and a non-ASCII group separator`() {
        val formatted = MoneyFormatter.format(1234.5, CurrencyCode.ILS, russian)
        assertTrue("expected a comma decimal in $formatted", formatted.contains("234,50"))
        assertFalse("expected no ASCII comma grouping in $formatted", formatted.contains("1,234"))
    }

    @Test
    fun `zero-decimal currencies keep no minor units in any language`() {
        for (locale in listOf(Locale.US, hebrew, arabic, russian)) {
            val formatted = MoneyFormatter.format(1234.5, "JPY", locale)
            val separator = java.text.DecimalFormatSymbols(locale).decimalSeparator
            assertFalse(
                "JPY must show no minor units in $locale: $formatted",
                formatted.contains(separator),
            )
        }
    }

    @Test
    fun `the CurrencyCode and String overloads agree`() {
        // Two entry points, one implementation. They used to share a private helper and
        // could have drifted; now both go through MoneyFormat.formatAmount.
        for (locale in listOf(Locale.US, hebrew, arabic, russian)) {
            for (currency in CurrencyCode.entries) {
                assertEquals(
                    "$currency in $locale",
                    MoneyFormatter.format(1234.5, currency, locale),
                    MoneyFormatter.format(1234.5, currency.name, locale),
                )
            }
        }
    }

    @Test
    fun `the Double path matches the Money path byte for byte`() {
        // The invariant that makes MoneyFormatter safe to keep as a separate object: it
        // must be a front for MoneyFormat, not a second implementation. If these two ever
        // diverge, the same amount reads differently on a screen that holds a Money than
        // on one that holds a Double.
        for (locale in listOf(Locale.US, hebrew, arabic, russian)) {
            for (currency in CurrencyCode.entries) {
                val viaMoney = MoneyFormat.format(
                    Money.of(BigDecimal("1234.50"), currency.name),
                    locale,
                )
                assertEquals(
                    "$currency in $locale",
                    viaMoney,
                    MoneyFormatter.format(1234.50, currency, locale),
                )
            }
        }
    }

    @Test
    fun `a currency the platform has no data for still renders its code and amount`() {
        val formatted = MoneyFormatter.format(1234.5, "XYZ", Locale.US)
        assertTrue("expected the code in $formatted", formatted.contains("XYZ"))
        assertTrue("expected the amount in $formatted", formatted.contains("1,234.50"))
    }

    @Test
    fun `a blank or malformed currency code does not throw`() {
        // Reachable from synced data: currencyCode is a free-text column.
        for (code in listOf("", "   ", "12", "toolongcode", "$$")) {
            MoneyFormatter.format(1234.5, code, Locale.US)
        }
    }

    @Test
    fun `unknown persisted currency falls back to ILS`() {
        assertEquals(CurrencyCode.ILS, CurrencyCode.from("unknown"))
    }

    @Test
    fun `the default locale is used when none is passed`() {
        Locale.setDefault(russian)
        assertTrue(MoneyFormatter.format(1234.5, CurrencyCode.ILS).contains("234,50"))
    }
}

package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.CurrencyCode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Hours render for the reader; the CSV renders for a parser.
 *
 * These tests replace a set that pinned [Locale.US] for both. That pinning existed for a
 * real reason — hours and money share a row and must share a separator — but it made
 * them agree on a convention three of the app's four languages do not use. The rule now
 * pinned here is the corrected one: [HoursFormatter.decimal] follows the locale,
 * [HoursFormatter.csv] never does, and money follows [HoursFormatter.decimal].
 */
class HoursFormatterTest {

    private lateinit var original: Locale

    @Before fun captureLocale() { original = Locale.getDefault() }
    @After fun restoreLocale() { Locale.setDefault(original) }

    private val hebrew = Locale.forLanguageTag("iw")
    private val arabic = Locale.forLanguageTag("ar")
    private val russian = Locale.forLanguageTag("ru")

    @Test
    fun `formats minutes as decimal hours`() {
        assertEquals("0.0", HoursFormatter.decimal(0, Locale.US))
        assertEquals("1.0", HoursFormatter.decimal(60, Locale.US))
        assertEquals("8.5", HoursFormatter.decimal(510, Locale.US))
        assertEquals("93.4", HoursFormatter.decimal(5604, Locale.US))
    }

    @Test
    fun `rounds to one decimal place for display`() {
        // 8h 35m is 8.5833… — the display format keeps one place.
        assertEquals("8.6", HoursFormatter.decimal(515, Locale.US))
    }

    @Test
    fun `no group separator, however many hours`() {
        // A month of hours is four digits. A group separator here would be the one thing
        // that survives into a CSV cell even at ROOT and breaks the parse.
        assertEquals("2000.0", HoursFormatter.decimal(120_000, Locale.US))
        assertFalse(HoursFormatter.csv(120_000).contains(","))
    }

    @Test
    fun `each language gets its own decimal separator`() {
        // Asserted against the locale's own symbol table rather than a literal, so the
        // test states the rule instead of restating one JDK's CLDR data.
        for (locale in listOf(Locale.US, hebrew, arabic, russian)) {
            val separator = DecimalFormatSymbols(locale).decimalSeparator
            assertTrue(
                "$locale hours should carry its own decimal separator '$separator'",
                HoursFormatter.decimal(510, locale).contains(separator),
            )
        }
    }

    @Test
    fun `Hebrew keeps the period it always used`() {
        // Worth pinning explicitly: Hebrew is the app's primary language and its number
        // conventions match English, so localising hours must NOT change what an Israeli
        // reader sees. The old Locale.US pinning was wrong about symbol placement, not
        // about this.
        assertEquals("8.5", HoursFormatter.decimal(510, hebrew))
    }

    @Test
    fun `Russian uses a comma`() {
        assertEquals("8,5", HoursFormatter.decimal(510, russian))
    }

    @Test
    fun `Arabic uses Arabic-Indic digits`() {
        val formatted = HoursFormatter.decimal(510, arabic)
        assertFalse("expected no ASCII digits in $formatted", formatted.any { it in '0'..'9' })
        assertTrue("expected Arabic-Indic digits in $formatted", formatted.any { it in '٠'..'٩' })
    }

    @Test
    fun `the CSV column is ASCII with a period, in every language`() {
        // The whole reason csv() does not take a locale. buildCsvContent joins on ','
        // and escapes only the notes field.
        Locale.setDefault(russian)
        assertEquals("8.58", HoursFormatter.csv(515))
        assertEquals("42.00", HoursFormatter.csv(2520))

        Locale.setDefault(arabic)
        assertEquals("8.58", HoursFormatter.csv(515))
        assertTrue(HoursFormatter.csv(515).all { it in '0'..'9' || it == '.' })
    }

    @Test
    fun `a CSV hours value can never introduce a column`() {
        // Stated as the property that actually matters, so it holds for any locale the
        // app is translated into later.
        for (tag in listOf("en-US", "iw", "ar", "ru", "de-DE", "fr-FR", "hi-IN", "fa-IR")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            for (minutes in listOf(0, 1, 515, 2520, 120_000)) {
                val cell = HoursFormatter.csv(minutes)
                assertFalse("$tag produced a comma in '$cell'", cell.contains(","))
                assertTrue("$tag produced a non-ASCII cell '$cell'", cell.all { it.code < 128 })
            }
        }
    }

    @Test
    fun `hours and money still agree on the separator in every language`() {
        // The invariant the old Locale.US pinning was protecting, kept.
        for (locale in listOf(Locale.US, hebrew, arabic, russian)) {
            val separator = DecimalFormatSymbols(locale).decimalSeparator
            val hours = HoursFormatter.decimal(510, locale)
            val money = MoneyFormatter.format(1234.50, CurrencyCode.ILS, locale)
            assertTrue("$locale hours '$hours' lost the separator", hours.contains(separator))
            assertTrue("$locale money '$money' lost the separator", money.contains(separator))
        }
    }

    @Test
    fun `the default locale is used when none is passed`() {
        Locale.setDefault(russian)
        assertEquals("8,5", HoursFormatter.decimal(510))
    }
}

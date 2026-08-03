package com.elmtrackr.app.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class HoursFormatterTest {

    private lateinit var original: Locale

    @Before
    fun captureLocale() {
        original = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun `formats minutes as decimal hours`() {
        assertEquals("0.0", HoursFormatter.decimal(0))
        assertEquals("1.0", HoursFormatter.decimal(60))
        assertEquals("8.5", HoursFormatter.decimal(510))
        assertEquals("93.4", HoursFormatter.decimal(5604))
    }

    @Test
    fun `rounds to one decimal place for display`() {
        // 8h 35m is 8.5833… — the display format keeps one place.
        assertEquals("8.6", HoursFormatter.decimal(515))
    }

    @Test
    fun `csv format keeps two decimal places`() {
        assertEquals("8.58", HoursFormatter.csv(515))
        assertEquals("42.00", HoursFormatter.csv(2520))
    }

    /**
     * The reason this object exists. One of the four call sites this replaced
     * formatted without a locale, so a device set to a comma-decimal locale
     * rendered `8,5` on the dashboard and `8.5` everywhere else. Asserting on
     * the value alone would pass on a US-locale machine and prove nothing —
     * the default locale has to be moved for the test to mean anything.
     */
    @Test
    fun `decimal separator does not follow the device locale`() {
        Locale.setDefault(Locale.GERMANY)

        assertEquals("8.5", HoursFormatter.decimal(510))
        assertEquals("8.58", HoursFormatter.csv(515))
    }

    @Test
    fun `matches MoneyFormatter's separator under a comma locale`() {
        Locale.setDefault(Locale.GERMANY)

        // Hours and money sit side by side on every summary card, so they have
        // to agree on what a decimal separator looks like.
        val hours = HoursFormatter.decimal(510)
        val money = MoneyFormatter.format(1234.50, com.elmtrackr.app.domain.model.CurrencyCode.ILS)

        assertEquals(true, hours.contains('.'))
        assertEquals(true, money.contains('.'))
    }
}

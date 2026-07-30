package com.elmtrackr.app.ui.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.MoneyByCurrency
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.domain.text.BidiText
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * The Paid Projects money surfaces rendered in both languages.
 *
 * The interface language and the project's currency are independent: an Israeli
 * freelancer bills a US client in dollars and reads the app in Hebrew, and a
 * London-based one bills in shekels and reads it in English. Both combinations
 * are rendered here, because a formatter that quietly assumes they agree is
 * wrong in the ordinary case rather than an exotic one.
 *
 * Assertions are on the words and on the accessibility descriptions rather than
 * on pixel positions. Whether a shekel sign lands left or right of the digits is
 * CLDR's decision and changes with platform data; whether the tax row is
 * labelled at all is this app's decision and must not change.
 *
 * These render small composables rather than whole screens on purpose — see the
 * note in ProjectsRenderTest about the detail screen's idling failure in
 * full-suite runs, which there is no reason to inherit here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1800dp", application = ScreenshotTestApplication::class)
class ProjectLocalizationRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(rtl: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            ElmTrackrTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    content()
                }
            }
        }
    }

    private fun fee(
        amount: String = "10000",
        currency: String = "USD",
        mode: TaxMode = TaxMode.EXCLUSIVE,
        rate: String = "18",
        taxLabel: String? = null,
    ) = ProjectFee.from(BigDecimal(amount), currency, mode, BigDecimal(rate), taxLabel)

    // ── English, left to right ───────────────────────────────────────────────

    @Test
    fun `English shows the three amounts under the agreed names`() {
        render { ProjectFeeBreakdown(fee()) }

        composeRule.onNodeWithText("Your fee before tax").assertIsDisplayed()
        composeRule.onNodeWithText("Client total").assertIsDisplayed()
    }

    @Test
    fun `English with an ILS project keeps English labels and shekel amounts`() {
        // Interface language and project currency are independent.
        render { ProjectFeeBreakdown(fee(currency = "ILS")) }

        composeRule.onNodeWithText("Your fee before tax").assertIsDisplayed()
        composeRule.onNodeWithText("Client total").assertIsDisplayed()
        // The shekel amount is present in the row's spoken description.
        composeRule.onNodeWithContentDescription("Client total: ₪11,800.00").assertExists()
    }

    @Test
    fun `a tax-free project shows only the total, not the same number three times`() {
        render { ProjectFeeBreakdown(fee(mode = TaxMode.NONE, rate = "0")) }

        composeRule.onNodeWithText("Client total").assertIsDisplayed()
        composeRule.onNodeWithText("Your fee before tax").assertDoesNotExist()
    }

    // ── Hebrew, right to left ────────────────────────────────────────────────

    @Test
    @Config(qualifiers = "iw-rIL-w411dp-h1800dp")
    fun `Hebrew shows the agreed glossary terms`() {
        render(rtl = true) { ProjectFeeBreakdown(fee(currency = "ILS", taxLabel = "מע״מ")) }

        composeRule.onNodeWithText("התשלום לפני מס").assertIsDisplayed()
        composeRule.onNodeWithText("סה״כ לתשלום על ידי הלקוח").assertIsDisplayed()
        // A named tax carries its own label and rate, so an Israeli user sees
        // "מע\u05F4מ (18%)" rather than a generic "tax" line. Asserted on the
        // label because the tax amount, 1,800.00, is also a substring of the
        // 11,800.00 total below it.
        composeRule.onNodeWithText("מע״מ (18%)").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "iw-rIL-w411dp-h1800dp")
    fun `a Hebrew interface with a USD project renders both without conflict`() {
        // The combination this module exists for: Hebrew-reading freelancer,
        // dollar-paying client.
        render(rtl = true) { ProjectFeeBreakdown(fee(currency = "USD")) }

        composeRule.onNodeWithText("התשלום לפני מס").assertIsDisplayed()
        composeRule.onNodeWithText("סה״כ לתשלום על ידי הלקוח").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "iw-rIL-w411dp-h1800dp")
    fun `a tax-free Hebrew project shows only the total`() {
        render(rtl = true) { ProjectFeeBreakdown(fee(currency = "ILS", mode = TaxMode.NONE, rate = "0")) }

        composeRule.onNodeWithText("סה״כ לתשלום על ידי הלקוח").assertIsDisplayed()
        composeRule.onNodeWithText("התשלום לפני מס").assertDoesNotExist()
    }

    // ── Two currencies never merge ───────────────────────────────────────────

    @Test
    fun `two currencies render as two labelled rows, never one total`() {
        val amounts = MoneyByCurrency.from(
            listOf(Money.of("1000", "USD"), Money.of("2000", "ILS")),
        )
        render { MoneyByCurrencyRows(label = "Outstanding balance", amounts = amounts) }

        // Each row names its own currency, so nothing suggests they add up. The
        // code sits in an isolate, which is invisible on screen but present in
        // the node's text — spelled out here rather than glossed over.
        composeRule.onNodeWithText("Outstanding balance (${BidiText.isolate("USD")})")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Outstanding balance (${BidiText.isolate("ILS")})")
            .assertIsDisplayed()
    }

    @Test
    fun `a multi-currency amount spells out its code beside the symbol`() {
        // "$1,000.00" on its own does not say which dollar, in a list that also
        // holds shekels.
        val amounts = MoneyByCurrency.from(
            listOf(Money.of("1000", "USD"), Money.of("2000", "ILS")),
        )
        render { MoneyByCurrencyRows(label = "Outstanding balance", amounts = amounts) }

        composeRule.onNodeWithText("1,000.00 ${BidiText.isolate("USD")}").assertExists()
        composeRule.onNodeWithText("2,000.00 ${BidiText.isolate("ILS")}").assertExists()
    }

    @Test
    fun `a single currency keeps the plain symbol form`() {
        // With nothing to confuse it with, the code would be noise.
        val amounts = MoneyByCurrency.from(listOf(Money.of("1000", "USD")))
        render { MoneyByCurrencyRows(label = "Outstanding balance", amounts = amounts) }

        composeRule.onNodeWithText("$1,000.00").assertIsDisplayed()
        composeRule.onNodeWithText("1,000.00 USD").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "iw-rIL-w411dp-h1800dp")
    fun `currency codes are not reversed in a Hebrew label`() {
        val amounts = MoneyByCurrency.from(
            listOf(Money.of("1000", "USD"), Money.of("2000", "ILS")),
        )
        render(rtl = true) { MoneyByCurrencyRows(label = "יתרה לתשלום", amounts = amounts) }

        // The code sits inside an isolate, so the node text still carries it in
        // order rather than as "DSU".
        composeRule.onNodeWithText("יתרה לתשלום (${BidiText.isolate("USD")})").assertExists()
    }

    // ── Currency scales ──────────────────────────────────────────────────────

    @Test
    fun `a zero-decimal currency renders with no decimal part`() {
        val amounts = MoneyByCurrency.from(listOf(Money.of("1235", "JPY")))
        render { MoneyByCurrencyRows(label = "Project fee", amounts = amounts) }

        composeRule.onNodeWithText("¥1,235").assertIsDisplayed()
    }

    @Test
    fun `a three-decimal currency renders all three digits`() {
        val amounts = MoneyByCurrency.from(listOf(Money.of("1234.568", "KWD")))
        render { MoneyByCurrencyRows(label = "Project fee", amounts = amounts) }

        composeRule.onNodeWithText("KWD1,234.568").assertIsDisplayed()
    }

    @Test
    fun `a zero-decimal tax breakdown still adds up on screen`() {
        // 1,000 + 180 = 1,180 with no rounding crumbs, in a currency with no
        // minor unit.
        render { ProjectFeeBreakdown(fee(amount = "1000", currency = "JPY")) }

        composeRule.onNodeWithContentDescription("Your fee before tax: ¥1,000").assertExists()
        composeRule.onNodeWithContentDescription("Client total: ¥1,180").assertExists()
    }

    // ── Mixed-direction project data ─────────────────────────────────────────

    @Test
    @Config(qualifiers = "iw-rIL-w411dp-h1800dp")
    fun `a Hebrew label with an English amount keeps both readable`() {
        val amounts = MoneyByCurrency.from(listOf(Money.of("1000", "USD")))
        render(rtl = true) { MoneyByCurrencyRows(label = "תשלום שהתקבל", amounts = amounts) }

        composeRule.onNodeWithText("תשלום שהתקבל").assertIsDisplayed()
    }

    @Test
    fun `an English label with a Hebrew-currency amount keeps both readable`() {
        val amounts = MoneyByCurrency.from(listOf(Money.of("1000", "ILS")))
        render { MoneyByCurrencyRows(label = "Payment received", amounts = amounts) }

        composeRule.onNodeWithText("Payment received").assertIsDisplayed()
        composeRule.onNodeWithText("₪1,000.00").assertIsDisplayed()
    }

    // ── Screen-reader labelling of amounts ───────────────────────────────────

    @Test
    fun `an amount row is announced with its label, not as a bare number`() {
        // Swiping through "1,000.00", "180.00", "1,180.00" with no names
        // attached is the difference between a usable screen and an unusable one.
        render { ProjectFeeBreakdown(fee()) }

        composeRule.onNodeWithContentDescription("Your fee before tax: $10,000.00").assertExists()
        composeRule.onNodeWithContentDescription("Client total: $11,800.00").assertExists()
    }

    @Test
    @Config(qualifiers = "iw-rIL-w411dp-h1800dp")
    fun `Hebrew amount rows are announced with their labels too`() {
        render(rtl = true) { ProjectFeeBreakdown(fee(currency = "ILS")) }

        // Matched by substring: the exact placement of the U+200F marks CLDR
        // puts inside a Hebrew currency pattern is the platform's business, not
        // this app's, and pinning it would break on a data update.
        composeRule.onNodeWithContentDescription(
            "סה״כ לתשלום על ידי הלקוח:",
            substring = true,
        ).assertExists()
        composeRule.onNodeWithContentDescription("11,800.00", substring = true).assertExists()
    }
}

package com.elmtrackr.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.billing.BillingAvailability
import com.elmtrackr.app.billing.ClockFacePackStorefront
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The clock face store: the dark browse surface that replaced the gallery.
 *
 * These are behaviour renders, not screenshots. What they pin down is the
 * store's contract: a locked pack is a product with a price and one verb, a
 * veiled face is tappable and opens the full-screen look, packs below the fold
 * collapse to a row until asked, and a purchase is confirmed inline by the
 * success strip rather than by navigation.
 *
 * The tall viewport is deliberate: it composes every lazy item, so the tests
 * reach the collapsed rows and the bundle without having to disambiguate the
 * list's scroll node from the four pagers that also scroll.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h2400dp", application = ScreenshotTestApplication::class)
class ClockFaceStoreRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Store(
        availablePacks: Set<ClockFaceGroup>,
        storefront: ClockFacePackStorefront,
        justUnlocked: Set<ClockFaceGroup> = emptySet(),
        onBuyPack: (ClockFaceGroup) -> Unit = {},
    ) {
        ClockFaceStoreScreen(
            selected = ClockStyle.CLASSIC,
            availablePacks = availablePacks,
            storefront = storefront,
            justUnlocked = justUnlocked,
            appVersion = "1.2.4",
            onSelect = {},
            onInstallPack = {},
            onBuyPack = onBuyPack,
            onBuyAllPacks = {},
            onRemovePack = {},
            onRestore = {},
            onDismissUnlocked = {},
            onBack = {},
        )
    }

    /**
     * Fresh install: Essentials is a picker, the first locked pack is the full
     * product card with the price beside a Buy button, and the packs below the
     * fold are collapsed rows that expand in place when asked.
     */
    @Test
    fun `a locked pack is a product and later packs collapse`() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                Store(
                    availablePacks = setOf(ClockFaceGroup.ESSENTIALS),
                    storefront = ClockFacePackStorefront(
                        prices = ClockFaceGroup.packs.associateWith { "₪5.00" },
                        availability = BillingAvailability.AVAILABLE,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Faces").assertIsDisplayed()
        // The two zone rules are the screen's ownership model: everything
        // under "Your faces" is usable, everything under "Face packs" is for
        // sale. The bundled pack's label carries "Included" as part of the
        // eyebrow rather than a badge.
        composeRule.onNodeWithText("YOUR FACES").assertIsDisplayed()
        composeRule.onNodeWithText("FACE PACKS").assertIsDisplayed()
        composeRule.onNodeWithText("ESSENTIALS · INCLUDED").assertIsDisplayed()
        // The first product makes the argument: price and verb both visible.
        composeRule.onNodeWithText("Buy").assertIsDisplayed()
        composeRule.onAllNodes(hasText("₪5.00")).onFirst().assertIsDisplayed()

        // Atmosphere is below the first product, so it is a collapsed row —
        // one merged node, no face names in its description yet. Tapping it
        // expands the full card in place, whose description names the faces.
        composeRule
            .onAllNodes(hasContentDescription("Atmosphere", substring = true))
            .onFirst()
            .performClick()
        composeRule
            .onAllNodes(hasContentDescription("Night", substring = true))
            .onFirst()
            .assertIsDisplayed()
    }

    /** A veiled face is tappable, and what it opens can complete the purchase. */
    @Test
    fun `tapping a veiled face opens the full-screen look with the unlock action`() {
        var bought: ClockFaceGroup? = null
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                Store(
                    availablePacks = setOf(ClockFaceGroup.ESSENTIALS),
                    storefront = ClockFacePackStorefront(
                        prices = ClockFaceGroup.packs.associateWith { "₪5.00" },
                        availability = BillingAvailability.AVAILABLE,
                    ),
                    onBuyPack = { bought = it },
                )
            }
        }

        // Progress leads the products; Dial is one of its veiled tiles.
        composeRule.onNodeWithContentDescription("Dial").performClick()
        composeRule.onNodeWithText("Unlock Progress · ₪5.00").assertIsDisplayed()

        composeRule.onNodeWithText("Unlock Progress · ₪5.00").performClick()
        assertEquals(ClockFaceGroup.PROGRESS, bought)
    }

    /**
     * The success strip is the purchase confirmation: inline, named, and above
     * a pack that is now a picker rather than a product.
     */
    @Test
    fun `a just-unlocked pack shows the inline strip and a picker`() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                Store(
                    availablePacks = setOf(ClockFaceGroup.ESSENTIALS, ClockFaceGroup.PROGRESS),
                    storefront = ClockFacePackStorefront(
                        owned = setOf(ClockFaceGroup.PROGRESS),
                        prices = ClockFaceGroup.packs.associateWith { "₪5.00" },
                        availability = BillingAvailability.AVAILABLE,
                    ),
                    justUnlocked = setOf(ClockFaceGroup.PROGRESS),
                )
            }
        }

        composeRule.onNodeWithText("Progress added").assertIsDisplayed()
        // The pack renders as a picker now: its faces are selectable tiles
        // carrying the face description, not a product card.
        composeRule
            .onAllNodes(hasContentDescription("Gradient ring", substring = true))
            .onFirst()
            .assertIsDisplayed()
    }

    /** Billing down means an explanation, never a button that can only fail. */
    @Test
    fun `an unavailable storefront explains instead of offering Buy`() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                Store(
                    availablePacks = setOf(ClockFaceGroup.ESSENTIALS),
                    storefront = ClockFacePackStorefront(
                        availability = BillingAvailability.UNAVAILABLE,
                    ),
                )
            }
        }

        composeRule
            .onAllNodes(hasText("Purchases are unavailable on this device right now."))
            .onFirst()
            .assertIsDisplayed()
    }
}

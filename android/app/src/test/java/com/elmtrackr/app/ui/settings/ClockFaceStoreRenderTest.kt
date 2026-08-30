package com.elmtrackr.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
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
 * The clock face store: a library tab and a shop tab under one title.
 *
 * These are behaviour renders, not screenshots. What they pin down is the
 * store's contract: ownership is a tab, never a badge to decode; every pack
 * on the shelf shows its price at all times and the chip is the buy button;
 * cards open **and close**; a face thumbnail opens the full-screen look; and
 * a purchase is confirmed by the strip on the shelf, with the pack itself
 * arriving under Your faces.
 *
 * The tall viewport is deliberate: it composes every lazy item, so the tests
 * reach every card without having to disambiguate the list's scroll node from
 * the pagers that also scroll.
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
        startInShop: Boolean = false,
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
            startInShop = startInShop,
        )
    }

    private fun freshInstallStorefront() = ClockFacePackStorefront(
        prices = ClockFaceGroup.packs.associateWith { "₪5.00" },
        availability = BillingAvailability.AVAILABLE,
    )

    /**
     * Fresh install: the user lands on Your faces, where Essentials is a
     * picker and one quiet row admits the shop exists. Switching tabs puts
     * every pack on the shelf, priced, and a card's header toggles its
     * showroom open and closed again.
     */
    @Test
    fun `the shop is a tab where every pack is priced and cards toggle`() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                Store(
                    availablePacks = setOf(ClockFaceGroup.ESSENTIALS),
                    storefront = freshInstallStorefront(),
                )
            }
        }

        // The library first: the bundled pack's tiles under its eyebrow.
        composeRule.onNodeWithText("Faces").assertIsDisplayed()
        composeRule.onNodeWithText("ESSENTIALS · INCLUDED").assertIsDisplayed()
        composeRule.onNodeWithText("₪5.00").assertDoesNotExist()

        composeRule.onNodeWithText("Shop").performClick()
        // Five packs on the shelf, each with its price chip always visible.
        composeRule.onAllNodes(hasText("₪5.00")).assertCountEquals(ClockFaceGroup.packs.size)
        composeRule
            .onAllNodes(hasContentDescription("Payday", substring = true))
            .onFirst()
            .assertIsDisplayed()

        // Collapsed, a pack's face name appears once (its thumbnail). Opening
        // the showroom adds the hero caption; closing removes it again.
        composeRule.onAllNodes(hasText("Aurora")).assertCountEquals(1)
        composeRule
            .onAllNodes(hasContentDescription("Progress", substring = true))
            .onFirst()
            .performClick()
        composeRule.onAllNodes(hasText("Aurora")).assertCountEquals(2)
        composeRule
            .onAllNodes(hasContentDescription("Progress", substring = true))
            .onFirst()
            .performClick()
        composeRule.onAllNodes(hasText("Aurora")).assertCountEquals(1)
    }

    /** A face thumbnail is tappable, and what it opens can complete the purchase. */
    @Test
    fun `tapping a face thumbnail opens the full-screen look with the unlock action`() {
        var bought: ClockFaceGroup? = null
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                Store(
                    availablePacks = setOf(ClockFaceGroup.ESSENTIALS),
                    storefront = freshInstallStorefront(),
                    startInShop = true,
                    onBuyPack = { bought = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Dial").performClick()
        composeRule.onNodeWithText("Unlock Progress · ₪5.00").assertIsDisplayed()

        composeRule.onNodeWithText("Unlock Progress · ₪5.00").performClick()
        assertEquals(ClockFaceGroup.PROGRESS, bought)
    }

    /**
     * The success strip is the purchase confirmation: inline on the shelf,
     * named, and itself the way over to the pack's new home on Your faces.
     */
    @Test
    fun `a just-unlocked pack shows the strip on the shelf and lands under your faces`() {
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
                    startInShop = true,
                )
            }
        }

        composeRule.onNodeWithText("Progress added").assertIsDisplayed()
        // The strip leads back to the library, where the pack is a picker now.
        composeRule.onNodeWithText("Progress added").performClick()
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
                    startInShop = true,
                )
            }
        }

        composeRule
            .onAllNodes(hasText("Purchases are unavailable on this device right now."))
            .onFirst()
            .assertIsDisplayed()
    }
}

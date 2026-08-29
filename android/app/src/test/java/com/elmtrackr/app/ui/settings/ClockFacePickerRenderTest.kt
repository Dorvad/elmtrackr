package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.billing.BillingAvailability
import com.elmtrackr.app.billing.ClockFacePackProducts
import com.elmtrackr.app.billing.ClockFacePackStorefront
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The progressive-disclosure picker: four faces on the appearance screen, the
 * rest behind a button.
 *
 * The semantics assertions are not incidental. Each tile is a preview canvas plus
 * two text lines, which TalkBack reads as separate unlabelled nodes, and the
 * selected state is shown only by a check badge that carries no description. The
 * tile merges its children into one labelled, selectable node — merges rather
 * than clears, because clearing would take the card's click action with it and
 * leave a tile screen readers cannot activate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class ClockFacePickerRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the quick picker offers four faces and a way to see the rest`() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ClockFaceQuickPicker(
                    selected = ClockStyle.CLASSIC,
                    recents = emptyList(),
                    availableFaces = ClockStyle.entries,
                    onSelect = {},
                    onBrowseAll = {},
                )
            }
        }

        // Four tiles, not twenty. Selectable is what a tile is; nothing else on
        // this control reports a selected state.
        assertEquals(
            CLOCK_FACE_QUICK_PICK_COUNT,
            composeRule.onAllNodes(isSelectable()).fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithText("Browse all ${ClockStyle.entries.size} faces").assertIsDisplayed()
    }

    @Test
    fun `each tile is one labelled node a screen reader can activate`() {
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ClockFaceQuickPicker(
                    selected = ClockStyle.MINIMAL,
                    recents = emptyList(),
                    availableFaces = ClockStyle.entries,
                    onSelect = {},
                    onBrowseAll = {},
                )
            }
        }

        // "Clean display" is Minimal's description, so this is the selected tile.
        composeRule.onAllNodes(hasContentDescription("Clean display", substring = true))
            .onFirst()
            .assertIsSelected()
            .assertHasClickAction()
    }

    @Test
    fun `the selected face leads the row even when it is not in the history`() {
        var chosen: ClockStyle? = null
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ClockFaceQuickPicker(
                    selected = ClockStyle.VINYL,
                    recents = listOf(ClockStyle.SPROUT, ClockStyle.METRO),
                    availableFaces = ClockStyle.entries,
                    onSelect = { chosen = it },
                    onBrowseAll = {},
                )
            }
        }

        composeRule.onAllNodes(hasContentDescription("A record spinning", substring = true))
            .onFirst()
            .assertIsSelected()

        composeRule.onAllNodes(hasContentDescription("one station per hour", substring = true))
            .onFirst()
            .performClick()
        assertEquals(ClockStyle.METRO, chosen)
    }

    /**
     * Selecting from the store answers the question the screen was opened to
     * ask, so it returns rather than waiting for a separate back tap. The
     * store replaces the gallery as the browse surface; the contract is the
     * same, even though the store forces the dark theme on its own.
     */
    @Test
    fun `the store reports a selection and returns`() {
        var chosen: ClockStyle? = null
        var wentBack = false
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                ClockFaceStoreScreen(
                    selected = ClockStyle.CLASSIC,
                    availablePacks = ClockFaceGroup.entries.toSet(),
                    storefront = ClockFacePackStorefront(
                        owned = ClockFacePackProducts.purchasablePacks.toSet(),
                        availability = BillingAvailability.AVAILABLE,
                    ),
                    justUnlocked = emptySet(),
                    appVersion = "1.2.4",
                    onSelect = { chosen = it },
                    onInstallPack = {},
                    onBuyPack = {},
                    onBuyAllPacks = {},
                    onRemovePack = {},
                    onRestore = {},
                    onDismissUnlocked = {},
                    onBack = { wentBack = true },
                )
            }
        }

        // The bundled pack labels its tiles as an eyebrow in the owned zone.
        composeRule.onNodeWithText("ESSENTIALS", substring = true).assertIsDisplayed()
        composeRule.onAllNodes(hasContentDescription("Distraction free", substring = true))
            .onFirst()
            .performClick()

        assertEquals(ClockStyle.FOCUS, chosen)
        assertEquals(true, wentBack)
    }
}

package com.elmtrackr.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.navigation.BottomNavItem
import com.elmtrackr.app.navigation.ElmBottomNav
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The navigation surfaces, in both feature states, both languages, and at a
 * font scale that does not fit.
 *
 * Adding a fifth destination is the one change in this module that alters a
 * surface the user did not navigate to, so it gets its own coverage: the tab
 * has to be reachable, announce its own name and selected state, and stay
 * legible when the labels no longer fit — without the bar ever becoming
 * something the user has to scroll.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp", application = ScreenshotTestApplication::class)
class NavigationAccessibilityRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun renderBar(
        paidProjects: Boolean,
        current: String = BottomNavItem.DASHBOARD.route,
        fontScale: Float = 1f,
        rtl: Boolean = false,
        onNavigate: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            val base = LocalDensity.current
            ElmTrackrTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(base.density, fontScale),
                ) {
                    ElmBottomNav(
                        currentRoute = current,
                        onNavigate = onNavigate,
                        items = BottomNavItem.visibleItems(paidProjects),
                    )
                }
            }
        }
    }

    private fun tabCount(): Int = composeRule
        .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        .fetchSemanticsNodes()
        .size

    // ── Four destinations ────────────────────────────────────────────────────

    @Test
    fun `with the feature off there are four tabs and no Projects tab`() {
        renderBar(paidProjects = false)

        assertEquals(4, tabCount())
        composeRule.onNodeWithText("Projects").assertDoesNotExist()
    }

    @Test
    fun `each of the four tabs is announced by name`() {
        renderBar(paidProjects = false)

        // The selected one carries its state in the description; the rest are
        // announced by name alone.
        composeRule.onNodeWithContentDescription("Home, selected").assertIsDisplayed()
        listOf("Shifts", "Reports", "Settings").forEach {
            composeRule.onNodeWithContentDescription(it).assertIsDisplayed()
        }
    }

    @Test
    fun `the selected tab carries the selected state, the others do not`() {
        renderBar(paidProjects = false)

        composeRule.onNodeWithContentDescription("Home, selected").assertIsSelected()
        composeRule.onNodeWithContentDescription("Reports").assertIsNotSelected()
    }

    // ── Five destinations ────────────────────────────────────────────────────

    @Test
    fun `with the feature on there are five tabs and Settings is still last`() {
        renderBar(paidProjects = true)

        assertEquals(5, tabCount())
        assertEquals(
            BottomNavItem.SETTINGS,
            BottomNavItem.visibleItems(paidProjectsEnabled = true).last(),
        )
    }

    @Test
    fun `the Projects tab is announced by name and is reachable`() {
        var navigated: String? = null
        renderBar(paidProjects = true, onNavigate = { navigated = it })

        composeRule.onNodeWithContentDescription("Projects").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Projects").performClick()

        assertEquals(BottomNavItem.PROJECTS.route, navigated)
    }

    @Test
    fun `the Projects tab announces its selected state when it is current`() {
        renderBar(paidProjects = true, current = BottomNavItem.PROJECTS.route)

        composeRule.onNodeWithContentDescription("Projects, selected").assertIsSelected()
        composeRule.onNodeWithContentDescription("Home").assertIsNotSelected()
    }

    @Test
    fun `five never becomes six`() {
        // The product constraint, asserted where the bar is actually built.
        renderBar(paidProjects = true)

        assertTrue(tabCount() <= BottomNavItem.MAX_PRIMARY_DESTINATIONS)
    }

    // ── Hebrew ───────────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = "iw-rIL-w411dp-h891dp")
    fun `the five tabs are announced in Hebrew`() {
        renderBar(paidProjects = true, rtl = true, current = BottomNavItem.PROJECTS.route)

        assertEquals(5, tabCount())
        composeRule.onNodeWithContentDescription("פרויקטים, נבחר").assertIsSelected()
        composeRule.onNodeWithContentDescription("הגדרות").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "iw-rIL-w411dp-h891dp")
    fun `the Hebrew bar mirrors without losing any tab`() {
        renderBar(paidProjects = true, rtl = true)

        // Mirroring is a layout concern; what must not happen is a destination
        // falling off the end of a reversed row.
        assertEquals(5, tabCount())
        listOf("בית", "משמרות", "דוחות", "פרויקטים", "הגדרות").forEach {
            composeRule.onNodeWithContentDescription(it, substring = true).assertExists()
        }
    }

    // ── Text scaling ─────────────────────────────────────────────────────────

    @Test
    fun `at the default font scale five tabs still show their labels`() {
        renderBar(paidProjects = true, fontScale = 1f)

        composeRule.onNodeWithText("Projects").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun `at a large font scale five tabs drop their labels rather than clip them`() {
        // "Proje…" in five columns is worse than five clear icons. The bar does
        // not scroll and no destination is removed.
        renderBar(paidProjects = true, fontScale = 1.6f)

        composeRule.onNodeWithText("Projects").assertDoesNotExist()
        assertEquals(5, tabCount())
    }

    @Test
    fun `dropping the labels does not cost the screen reader anything`() {
        // The whole point: the label is a visual affordance, the description is
        // the accessible one, and only the first is given up.
        renderBar(paidProjects = true, fontScale = 1.6f, current = BottomNavItem.PROJECTS.route)

        composeRule.onNodeWithContentDescription("Projects, selected").assertIsSelected()
        composeRule.onNodeWithContentDescription("Home").assertIsDisplayed()
    }

    @Test
    fun `four tabs keep their labels a little longer than five do`() {
        // More room per column, so the threshold is higher. At 1.4 the
        // four-destination bar still labels its tabs.
        renderBar(paidProjects = false, fontScale = 1.4f)

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun `five tabs have already dropped their labels at that same scale`() {
        renderBar(paidProjects = true, fontScale = 1.4f)

        composeRule.onNodeWithText("Settings").assertDoesNotExist()
        assertEquals(5, tabCount())
    }

    @Test
    fun `every tab keeps a usable touch target at a large font scale`() {
        renderBar(paidProjects = true, fontScale = 1.6f)

        // Material's minimum is 48dp; the icon box plus padding clears it even
        // with the label gone.
        composeRule.onNodeWithContentDescription("Projects").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `every tab keeps a usable touch target with five items at default scale`() {
        renderBar(paidProjects = true)

        BottomNavItem.visibleItems(paidProjectsEnabled = true).forEach { item ->
            val label = when (item) {
                BottomNavItem.DASHBOARD -> "Home"
                BottomNavItem.SHIFTS -> "Shifts"
                BottomNavItem.REPORTS -> "Reports"
                BottomNavItem.PROJECTS -> "Projects"
                BottomNavItem.SETTINGS -> "Settings"
            }
            composeRule.onNodeWithContentDescription(label, substring = true)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    // ── Tablet rail ──────────────────────────────────────────────────────────

    private fun renderRail(
        paidProjects: Boolean,
        current: String = BottomNavItem.DASHBOARD.route,
        fontScale: Float = 1f,
        rtl: Boolean = false,
        onNavigate: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            val base = LocalDensity.current
            ElmTrackrTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(base.density, fontScale),
                ) {
                    ElmSideNavigation(
                        currentRoute = current,
                        onNavigate = onNavigate,
                        items = BottomNavItem.visibleItems(paidProjects),
                    )
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "sw600dp-w800dp-h1280dp")
    fun `the rail shows the same five destinations as the bar`() {
        // One destination list feeds both surfaces, so they cannot disagree about
        // which tabs exist. This asserts it at the rendered level rather than
        // trusting the shared list.
        renderRail(paidProjects = true)

        assertEquals(5, tabCount())
        composeRule.onNodeWithText("Projects").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "sw600dp-w800dp-h1280dp")
    fun `the rail shows four destinations when the feature is off`() {
        renderRail(paidProjects = false)

        assertEquals(4, tabCount())
        composeRule.onNodeWithText("Projects").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "sw600dp-w800dp-h1280dp")
    fun `the rail announces each destination and its selected state`() {
        renderRail(paidProjects = true, current = BottomNavItem.PROJECTS.route)

        composeRule.onNodeWithContentDescription("Projects, selected").assertIsSelected()
        composeRule.onNodeWithContentDescription("Reports").assertIsNotSelected()
    }

    @Test
    @Config(qualifiers = "sw600dp-w800dp-h1280dp")
    fun `the rail is reachable`() {
        var navigated: String? = null
        renderRail(paidProjects = true, onNavigate = { navigated = it })

        composeRule.onNodeWithContentDescription("Projects").performClick()

        assertEquals(BottomNavItem.PROJECTS.route, navigated)
    }

    @Test
    @Config(qualifiers = "sw600dp-w800dp-h1280dp")
    fun `the rail keeps its labels at a large font scale`() {
        // The rail lays destinations out vertically on a full-width row, so a
        // long label grows into space it already has. There is nothing to give
        // up here, unlike the bar.
        renderRail(paidProjects = true, fontScale = 1.6f)

        composeRule.onNodeWithText("Projects").assertIsDisplayed()
        assertEquals(5, tabCount())
    }

    @Test
    @Config(qualifiers = "iw-rIL-sw600dp-w800dp-h1280dp")
    fun `the rail renders the five destinations in Hebrew`() {
        renderRail(paidProjects = true, rtl = true, current = BottomNavItem.PROJECTS.route)

        assertEquals(5, tabCount())
        composeRule.onNodeWithContentDescription("פרויקטים, נבחר").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "sw600dp-w800dp-h1280dp")
    fun `every rail destination keeps a usable touch target`() {
        renderRail(paidProjects = true)

        listOf("Home", "Shifts", "Reports", "Projects", "Settings").forEach {
            composeRule.onNodeWithContentDescription(it, substring = true)
                .assertHeightIsAtLeast(48.dp)
        }
    }
}

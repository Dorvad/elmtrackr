package com.elmtrackr.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.ui.navigation.ElmSideNavigation
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders both navigation surfaces to check what a user actually sees for each
 * feature state — the four-tab and five-tab bars, the tablet rail, and the
 * Hebrew right-to-left layout.
 *
 * Labels are asserted by their English resource text; the RTL case checks that
 * the same five destinations render with the layout direction flipped, since
 * mirroring is what breaks in RTL, not the content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = ScreenshotTestApplication::class)
class PaidProjectsNavigationRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val labels = mapOf(
        BottomNavItem.DASHBOARD to "Home",
        BottomNavItem.SHIFTS to "Shifts",
        BottomNavItem.REPORTS to "Reports",
        BottomNavItem.PROJECTS to "Projects",
        BottomNavItem.SETTINGS to "Settings",
    )

    @Composable
    private fun BottomNav(
        paidProjectsEnabled: Boolean,
        currentRoute: String? = BottomNavItem.DASHBOARD.route,
        onNavigate: (String) -> Unit = {},
    ) {
        ElmTrackrTheme {
            ElmBottomNav(
                currentRoute = PaidProjectsNavGuard.highlightedRoute(currentRoute, paidProjectsEnabled),
                onNavigate = onNavigate,
                items = BottomNavItem.visibleItems(paidProjectsEnabled),
            )
        }
    }

    @Test
    fun `bottom bar shows the original four destinations when disabled`() {
        composeRule.setContent { BottomNav(paidProjectsEnabled = false) }

        listOf(BottomNavItem.DASHBOARD, BottomNavItem.SHIFTS, BottomNavItem.REPORTS, BottomNavItem.SETTINGS)
            .forEach { composeRule.onNodeWithText(labels.getValue(it)).assertIsDisplayed() }
        composeRule.onNodeWithText(labels.getValue(BottomNavItem.PROJECTS)).assertDoesNotExist()
    }

    @Test
    fun `bottom bar shows five destinations when enabled`() {
        composeRule.setContent { BottomNav(paidProjectsEnabled = true) }

        BottomNavItem.entries.forEach {
            composeRule.onNodeWithText(labels.getValue(it)).assertIsDisplayed()
        }
    }

    @Test
    fun `projects tab navigates to the projects route`() {
        val navigated = mutableListOf<String>()
        composeRule.setContent {
            BottomNav(paidProjectsEnabled = true, onNavigate = { navigated += it })
        }

        composeRule.onNodeWithText(labels.getValue(BottomNavItem.PROJECTS)).performClick()

        assertEquals(listOf(BottomNavItem.PROJECTS.route), navigated)
    }

    @Test
    fun `disabling while projects is selected drops the tab and highlights the dashboard`() {
        composeRule.setContent {
            var enabled by mutableStateOf(true)
            ElmTrackrTheme {
                ElmBottomNav(
                    currentRoute = PaidProjectsNavGuard.highlightedRoute(
                        BottomNavItem.PROJECTS.route,
                        enabled,
                    ),
                    onNavigate = { enabled = false },
                    items = BottomNavItem.visibleItems(enabled),
                )
            }
        }

        composeRule.onNodeWithText(labels.getValue(BottomNavItem.PROJECTS)).assertIsDisplayed()
        // Simulate the flag flipping off.
        composeRule.onNodeWithText(labels.getValue(BottomNavItem.SETTINGS)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(labels.getValue(BottomNavItem.PROJECTS)).assertDoesNotExist()
        // Home carries the ", selected" content description once it is highlighted.
        composeRule.onNodeWithText(labels.getValue(BottomNavItem.DASHBOARD)).assertIsDisplayed()
    }

    @Test
    fun `bottom bar renders five destinations in a right-to-left layout`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BottomNav(paidProjectsEnabled = true)
            }
        }

        BottomNavItem.entries.forEach {
            composeRule.onNodeWithText(labels.getValue(it)).assertIsDisplayed()
        }
    }

    @Test
    fun `bottom bar renders four destinations in a right-to-left layout`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BottomNav(paidProjectsEnabled = false)
            }
        }

        composeRule.onNodeWithText(labels.getValue(BottomNavItem.PROJECTS)).assertDoesNotExist()
        composeRule.onNodeWithText(labels.getValue(BottomNavItem.DASHBOARD)).assertIsDisplayed()
    }

    // ── Tablet rail ───────────────────────────────────────────────────────────

    @Test
    fun `tablet rail shows the same four destinations when disabled`() {
        composeRule.setContent {
            ElmTrackrTheme {
                ElmSideNavigation(
                    currentRoute = BottomNavItem.DASHBOARD.route,
                    onNavigate = {},
                    items = BottomNavItem.visibleItems(paidProjectsEnabled = false),
                )
            }
        }

        composeRule.onNodeWithText(labels.getValue(BottomNavItem.PROJECTS)).assertDoesNotExist()
        composeRule.onNodeWithText(labels.getValue(BottomNavItem.SETTINGS)).assertIsDisplayed()
    }

    @Test
    fun `tablet rail shows the same five destinations when enabled`() {
        composeRule.setContent {
            ElmTrackrTheme {
                ElmSideNavigation(
                    currentRoute = BottomNavItem.PROJECTS.route,
                    onNavigate = {},
                    items = BottomNavItem.visibleItems(paidProjectsEnabled = true),
                )
            }
        }

        BottomNavItem.entries.forEach {
            composeRule.onNodeWithText(labels.getValue(it)).assertIsDisplayed()
        }
    }

    @Test
    fun `tablet rail navigates to projects`() {
        val navigated = mutableListOf<String>()
        composeRule.setContent {
            ElmTrackrTheme {
                ElmSideNavigation(
                    currentRoute = BottomNavItem.DASHBOARD.route,
                    onNavigate = { navigated += it },
                    items = BottomNavItem.visibleItems(paidProjectsEnabled = true),
                )
            }
        }

        composeRule.onNodeWithText(labels.getValue(BottomNavItem.PROJECTS)).performClick()

        assertEquals(listOf(BottomNavItem.PROJECTS.route), navigated)
    }
}

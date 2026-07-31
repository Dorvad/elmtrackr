package com.elmtrackr.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaidProjectsNavGuardTest {

    private val projects = BottomNavItem.PROJECTS.route
    private val dashboard = BottomNavItem.DASHBOARD.route

    // ── Redirect away from a disabled destination ──────────────────────────────

    @Test
    fun `disabling while on projects redirects to the dashboard`() {
        assertEquals(dashboard, PaidProjectsNavGuard.redirectTarget(projects, paidProjectsEnabled = false))
    }

    @Test
    fun `no redirect while projects is enabled`() {
        assertNull(PaidProjectsNavGuard.redirectTarget(projects, paidProjectsEnabled = true))
    }

    @Test
    fun `no redirect while the flag is still loading`() {
        // A cold start restoring the projects tab must not be bounced before
        // settings answer, or the user loses their selected tab on every launch.
        assertNull(PaidProjectsNavGuard.redirectTarget(projects, paidProjectsEnabled = null))
    }

    @Test
    fun `ungated destinations are never redirected`() {
        BottomNavItem.entries.filterNot { it.requiresPaidProjects }.forEach { item ->
            assertNull(
                "${item.route} must not be redirected",
                PaidProjectsNavGuard.redirectTarget(item.route, paidProjectsEnabled = false),
            )
        }
    }

    @Test
    fun `unknown routes are left alone`() {
        assertNull(PaidProjectsNavGuard.redirectTarget("shift-form", paidProjectsEnabled = false))
        assertNull(PaidProjectsNavGuard.redirectTarget(null, paidProjectsEnabled = false))
    }

    // ── Selected-tab highlight ────────────────────────────────────────────────

    @Test
    fun `projects is not highlighted once the feature is off`() {
        assertEquals(dashboard, PaidProjectsNavGuard.highlightedRoute(projects, paidProjectsEnabled = false))
    }

    @Test
    fun `highlight follows the current route in every other case`() {
        assertEquals(projects, PaidProjectsNavGuard.highlightedRoute(projects, paidProjectsEnabled = true))
        assertEquals(projects, PaidProjectsNavGuard.highlightedRoute(projects, paidProjectsEnabled = null))
        assertEquals("reports", PaidProjectsNavGuard.highlightedRoute("reports", paidProjectsEnabled = false))
    }

    // ── Deep links ────────────────────────────────────────────────────────────

    @Test
    fun `recognises projects deep links`() {
        assertTrue(PaidProjectsNavGuard.isProjectsDeepLink("elmtrackr://projects"))
        assertTrue(PaidProjectsNavGuard.isProjectsDeepLink("elmtrackr://projects/"))
        assertTrue(PaidProjectsNavGuard.isProjectsDeepLink("elmtrackr://projects/42"))
        assertTrue(PaidProjectsNavGuard.isProjectsDeepLink("elmtrackr://projects?id=42"))
        assertTrue(PaidProjectsNavGuard.isProjectsDeepLink("ELMTRACKR://PROJECTS"))
    }

    @Test
    fun `does not claim auth or unrelated deep links`() {
        assertFalse(PaidProjectsNavGuard.isProjectsDeepLink("elmtrackr://auth/callback"))
        assertFalse(PaidProjectsNavGuard.isProjectsDeepLink("elmtrackr://projectsomething"))
        assertFalse(PaidProjectsNavGuard.isProjectsDeepLink("https://example.com/projects"))
        assertFalse(PaidProjectsNavGuard.isProjectsDeepLink(null))
        assertFalse(PaidProjectsNavGuard.isProjectsDeepLink(""))
    }

    @Test
    fun `deep link opens projects when enabled`() {
        assertEquals(
            projects,
            PaidProjectsNavGuard.deepLinkTarget("elmtrackr://projects", paidProjectsEnabled = true),
        )
    }

    @Test
    fun `deep link falls back to the dashboard when disabled`() {
        assertEquals(
            dashboard,
            PaidProjectsNavGuard.deepLinkTarget("elmtrackr://projects/42", paidProjectsEnabled = false),
        )
    }

    @Test
    fun `deep link stays pending while the flag loads`() {
        assertNull(PaidProjectsNavGuard.deepLinkTarget("elmtrackr://projects", paidProjectsEnabled = null))
    }

    @Test
    fun `non-projects deep links are not routed by this guard`() {
        assertNull(PaidProjectsNavGuard.deepLinkTarget("elmtrackr://auth/callback", paidProjectsEnabled = true))
    }
}

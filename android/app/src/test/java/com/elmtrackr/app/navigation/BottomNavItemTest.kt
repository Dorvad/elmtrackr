package com.elmtrackr.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavItemTest {

    private val originalFourRoutes = listOf("dashboard", "shifts", "reports", "settings")

    @Test
    fun `never more than five primary destinations`() {
        assertTrue(
            "A bottom bar must not carry more than ${BottomNavItem.MAX_PRIMARY_DESTINATIONS} destinations",
            BottomNavItem.entries.size <= BottomNavItem.MAX_PRIMARY_DESTINATIONS,
        )
    }

    @Test
    fun `four destinations when paid projects is disabled`() {
        val routes = BottomNavItem.visibleItems(paidProjectsEnabled = false).map { it.route }
        assertEquals(4, routes.size)
        assertEquals(originalFourRoutes, routes)
    }

    @Test
    fun `five destinations when paid projects is enabled`() {
        val routes = BottomNavItem.visibleItems(paidProjectsEnabled = true).map { it.route }
        assertEquals(5, routes.size)
        assertEquals(listOf("dashboard", "shifts", "reports", "projects", "settings"), routes)
    }

    @Test
    fun `enabling paid projects preserves the original four and their order`() {
        val enabled = BottomNavItem.visibleItems(paidProjectsEnabled = true)
            .map { it.route }
            .filter { it in originalFourRoutes }
        assertEquals(originalFourRoutes, enabled)
    }

    @Test
    fun `projects is the only gated destination`() {
        val gated = BottomNavItem.entries.filter { it.requiresPaidProjects }
        assertEquals(listOf(BottomNavItem.PROJECTS), gated)
    }

    @Test
    fun `routes are unique`() {
        val routes = BottomNavItem.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `dashboard is first tab in both configurations`() {
        assertEquals("dashboard", BottomNavItem.visibleItems(false).first().route)
        assertEquals("dashboard", BottomNavItem.visibleItems(true).first().route)
    }

    @Test
    fun `settings stays last in both configurations`() {
        assertEquals("settings", BottomNavItem.visibleItems(false).last().route)
        assertEquals("settings", BottomNavItem.visibleItems(true).last().route)
    }

    @Test
    fun `does not contain account tab`() {
        assertFalse(BottomNavItem.entries.any { it.route == "account" })
    }

    @Test
    fun `fromRoute resolves known routes and rejects unknown ones`() {
        assertEquals(BottomNavItem.PROJECTS, BottomNavItem.fromRoute("projects"))
        assertEquals(null, BottomNavItem.fromRoute("nope"))
        assertEquals(null, BottomNavItem.fromRoute(null))
    }
}

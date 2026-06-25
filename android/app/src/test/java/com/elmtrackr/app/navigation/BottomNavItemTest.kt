package com.elmtrackr.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BottomNavItemTest {

    @Test
    fun `has exactly four tabs`() {
        assertEquals(4, BottomNavItem.entries.size)
    }

    @Test
    fun `tab routes are dashboard shifts reports settings`() {
        val routes = BottomNavItem.entries.map { it.route }
        assertEquals(listOf("dashboard", "shifts", "reports", "settings"), routes)
    }

    @Test
    fun `does not contain account tab`() {
        assertFalse(BottomNavItem.entries.any { it.route == "account" })
    }

    @Test
    fun `dashboard is first tab`() {
        assertEquals("dashboard", BottomNavItem.entries.first().route)
    }
}

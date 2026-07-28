package com.elmtrackr.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockControllerTest {

    @Test
    fun `unconfigured lock fails closed`() {
        // A process cold-started by a widget tap or notification action reaches the guards
        // before the DataStore preference is read. Treating that as unlocked let headless
        // punches through with no authentication, so unknown must block and must not
        // expose content.
        AppLockController.resetForTest()

        assertFalse(AppLockController.isConfigured())
        assertTrue(AppLockController.shouldBlockSensitiveActions())
        assertFalse(AppLockController.isUnlocked())
    }

    @Test
    fun `resolving the preference to disabled clears the block`() {
        AppLockController.resetForTest()
        AppLockController.configure(enabled = false)

        assertTrue(AppLockController.isConfigured())
        assertFalse(AppLockController.shouldBlockSensitiveActions())
    }

    @Test
    fun `resolving the preference to enabled starts locked`() {
        // Default initiallyUnlocked = !enabled: a freshly resolved process has not
        // authenticated, so an enabled lock is engaged.
        AppLockController.resetForTest()
        AppLockController.configure(enabled = true)

        assertTrue(AppLockController.shouldBlockSensitiveActions())
    }

    @Test
    fun `disabled lock is always unlocked`() {
        AppLockController.configure(enabled = false)
        AppLockController.lock()
        assertTrue(AppLockController.isUnlocked())
        assertFalse(AppLockController.shouldBlockSensitiveActions())
    }

    @Test
    fun `enabled lock blocks after lock call`() {
        AppLockController.configure(enabled = true, initiallyUnlocked = true)
        AppLockController.lock()
        assertFalse(AppLockController.isUnlocked())
        assertTrue(AppLockController.shouldBlockSensitiveActions())
    }

    @Test
    fun `unlock clears block`() {
        AppLockController.configure(enabled = true, initiallyUnlocked = false)
        assertTrue(AppLockController.shouldBlockSensitiveActions())
        AppLockController.unlock()
        assertTrue(AppLockController.isUnlocked())
        assertFalse(AppLockController.shouldBlockSensitiveActions())
    }
}

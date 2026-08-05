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

    // ── Unenforceable lock ────────────────────────────────────────────────────
    // A user who enables app lock and then removes their device screen lock can
    // never satisfy the prompt, and the switch that turns the lock off sits
    // behind that same prompt. The only escape was clearing app data, which
    // destroys unsynced shifts.

    @Test
    fun `an unenforceable lock stops holding the user out`() {
        AppLockController.resetForTest()
        AppLockController.configure(enabled = true)
        assertFalse(AppLockController.isUnlocked())

        AppLockController.setEnforceable(false)

        assertTrue(AppLockController.isUnlocked())
        assertTrue(AppLockController.isSuspended())
    }

    @Test
    fun `an unenforceable lock stops blocking the widget and the watch`() {
        // Same reasoning as the gate: there is no way to authenticate past it, so
        // a punch from the widget or the watch would be permanently refused.
        AppLockController.resetForTest()
        AppLockController.configure(enabled = true)
        assertTrue(AppLockController.shouldBlockSensitiveActions())

        AppLockController.setEnforceable(false)

        assertFalse(AppLockController.shouldBlockSensitiveActions())
    }

    @Test
    fun `enrolling a credential again resumes the lock`() {
        // Nothing is written to the preference while suspended, so the lock has to
        // re-engage on its own rather than needing the user to switch it back on.
        AppLockController.resetForTest()
        AppLockController.configure(enabled = true)
        AppLockController.setEnforceable(false)
        assertTrue(AppLockController.isUnlocked())

        AppLockController.setEnforceable(true)

        assertFalse(AppLockController.isUnlocked())
        assertTrue(AppLockController.shouldBlockSensitiveActions())
        assertFalse(AppLockController.isSuspended())
    }

    @Test
    fun `an unenforceable device does not unlock a lock that is switched off`() {
        AppLockController.resetForTest()
        AppLockController.configure(enabled = false)
        AppLockController.setEnforceable(false)

        assertFalse(AppLockController.isSuspended())
    }

    @Test
    fun `an unresolved preference still fails closed on an unenforceable device`() {
        // Suspension must not become a second route past the tri-state guard: a
        // cold-started process has not read the preference yet, and "unknown"
        // stays closed regardless of what the device can authenticate with.
        AppLockController.resetForTest()
        AppLockController.setEnforceable(false)

        assertFalse(AppLockController.isUnlocked())
        assertTrue(AppLockController.shouldBlockSensitiveActions())
    }

    @Test
    fun `enforcement is the default before anything checks the device`() {
        AppLockController.resetForTest()
        AppLockController.configure(enabled = true)

        assertFalse(AppLockController.isUnlocked())
        assertFalse(AppLockController.isSuspended())
    }
}

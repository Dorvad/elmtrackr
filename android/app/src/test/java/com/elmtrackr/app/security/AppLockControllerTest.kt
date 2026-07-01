package com.elmtrackr.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockControllerTest {

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

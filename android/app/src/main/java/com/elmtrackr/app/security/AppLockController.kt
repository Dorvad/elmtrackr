package com.elmtrackr.app.security

import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-process lock session for biometric app-lock.
 * When enabled, backgrounding the app clears [isUnlocked] until the user re-authenticates.
 */
object AppLockController {

    private val lockEnabled = AtomicBoolean(false)
    private val unlocked = AtomicBoolean(true)

    fun configure(enabled: Boolean, initiallyUnlocked: Boolean = !enabled) {
        lockEnabled.set(enabled)
        unlocked.set(initiallyUnlocked || !enabled)
    }

    fun isLockEnabled(): Boolean = lockEnabled.get()

    fun isUnlocked(): Boolean = !lockEnabled.get() || unlocked.get()

    fun lock() {
        if (lockEnabled.get()) {
            unlocked.set(false)
        }
    }

    fun unlock() {
        unlocked.set(true)
    }

    fun shouldBlockSensitiveActions(): Boolean = lockEnabled.get() && !unlocked.get()
}

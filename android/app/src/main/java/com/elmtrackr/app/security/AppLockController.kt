package com.elmtrackr.app.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * In-process lock session for biometric app-lock.
 * When enabled, backgrounding the app clears [isUnlocked] until the user re-authenticates.
 *
 * State is backed by Compose snapshot state so that reads inside a composable (e.g. the
 * app-lock gate) are observed: calling [unlock]/[lock] invalidates the reading composable and
 * triggers recomposition. A plain [java.util.concurrent.atomic.AtomicBoolean] would be invisible
 * to Compose, so the gate would never leave the lock screen after a successful unlock.
 */
object AppLockController {

    private var lockEnabled by mutableStateOf(false)
    private var unlocked by mutableStateOf(true)

    fun configure(enabled: Boolean, initiallyUnlocked: Boolean = !enabled) {
        lockEnabled = enabled
        unlocked = initiallyUnlocked || !enabled
    }

    fun isLockEnabled(): Boolean = lockEnabled

    fun isUnlocked(): Boolean = !lockEnabled || unlocked

    fun lock() {
        if (lockEnabled) {
            unlocked = false
        }
    }

    fun unlock() {
        unlocked = true
    }

    fun shouldBlockSensitiveActions(): Boolean = lockEnabled && !unlocked
}

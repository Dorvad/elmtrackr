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
 *
 * [lockEnabled] is deliberately tri-state. The preference lives in DataStore and is read
 * asynchronously, so a process cold-started by a widget tap or a notification action reaches the
 * guards before the value is known. Treating "unknown" as "unlocked" let those headless punches
 * through with no authentication; unknown therefore fails closed, and callers resolve the real
 * value (see [AppLockActionGuard]) before deciding.
 */
object AppLockController {

    private var lockEnabled by mutableStateOf<Boolean?>(null)
    private var unlocked by mutableStateOf(false)

    fun configure(enabled: Boolean, initiallyUnlocked: Boolean = !enabled) {
        lockEnabled = enabled
        unlocked = initiallyUnlocked || !enabled
    }

    /** False until the persisted preference has been read at least once. */
    fun isConfigured(): Boolean = lockEnabled != null

    fun isLockEnabled(): Boolean = lockEnabled == true

    /** Unknown counts as locked: never expose content before the preference is known. */
    fun isUnlocked(): Boolean = when (lockEnabled) {
        null -> false
        false -> true
        else -> unlocked
    }

    fun lock() {
        if (lockEnabled == true) {
            unlocked = false
        }
    }

    fun unlock() {
        unlocked = true
    }

    /** Restores the un-configured state so tests can exercise the fail-closed path. */
    internal fun resetForTest() {
        lockEnabled = null
        unlocked = false
    }

    /** Unknown counts as blocked; resolve the preference first to avoid false positives. */
    fun shouldBlockSensitiveActions(): Boolean = when (lockEnabled) {
        null -> true
        false -> false
        else -> !unlocked
    }
}

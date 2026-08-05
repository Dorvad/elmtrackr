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

    /**
     * Whether the device can still satisfy the lock. Defaults to true so the
     * fail-closed contract above holds until something actually checks.
     *
     * See [setEnforceable] for why this exists.
     */
    private var lockEnforceable by mutableStateOf(true)

    fun configure(enabled: Boolean, initiallyUnlocked: Boolean = !enabled) {
        lockEnabled = enabled
        unlocked = initiallyUnlocked || !enabled
    }

    /** False until the persisted preference has been read at least once. */
    fun isConfigured(): Boolean = lockEnabled != null

    fun isLockEnabled(): Boolean = lockEnabled == true

    /**
     * Records whether the device still has a credential the lock can be checked
     * against, as decided by [BiometricCapability.canEnforceAppLock].
     *
     * A user who turns app lock on and then removes their device screen lock
     * leaves the lock permanently unsatisfiable: the prompt has nothing to
     * authenticate with, so it can never succeed, and the switch that would turn
     * the lock off sits behind the same prompt. The only escape was clearing app
     * data, which destroys unsynced shifts — a lockout that costs the user their
     * work.
     *
     * Suspending enforcement in that one state gives up very little. Removing a
     * device screen lock requires entering the credential first, so anyone who
     * could reach this state already held what the lock was protecting against.
     * Nothing is written to the preference, so the lock resumes by itself the
     * moment a credential is enrolled again.
     */
    fun setEnforceable(value: Boolean) {
        lockEnforceable = value
    }

    /** True when the lock is on but currently unenforceable — Settings says so. */
    fun isSuspended(): Boolean = lockEnabled == true && !lockEnforceable

    /** Unknown counts as locked: never expose content before the preference is known. */
    fun isUnlocked(): Boolean = when (lockEnabled) {
        null -> false
        false -> true
        else -> unlocked || !lockEnforceable
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
        lockEnforceable = true
    }

    /** Unknown counts as blocked; resolve the preference first to avoid false positives. */
    fun shouldBlockSensitiveActions(): Boolean = when (lockEnabled) {
        null -> true
        false -> false
        else -> !unlocked && lockEnforceable
    }
}

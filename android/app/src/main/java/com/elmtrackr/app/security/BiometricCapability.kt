package com.elmtrackr.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

enum class BiometricAvailability {
    AVAILABLE,
    NOT_ENROLLED,
    UNAVAILABLE,
}

object BiometricCapability {

    fun check(context: Context): BiometricAvailability {
        val manager = BiometricManager.from(context)
        return when (
            manager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }
    }

    /**
     * Whether an enabled app lock can still be enforced. Feeds
     * [AppLockController.setEnforceable]; see that method for why suspending
     * enforcement is the right answer rather than a hole.
     *
     * Only [BiometricAvailability.NOT_ENROLLED] suspends it. That is the one
     * unambiguous "there is no credential to check against" answer, and it is
     * exactly the state a user reaches by removing their device screen lock.
     *
     * [BiometricAvailability.UNAVAILABLE] deliberately keeps enforcing. It folds
     * together a sensor that is merely busy with hardware that will never work,
     * and the transient half of that must not open the lock — a lock that lifts
     * while the sensor is occupied is worth nothing. A device with a PIN but no
     * fingerprint reader is not affected either way: the check asks for
     * `DEVICE_CREDENTIAL` too, so a PIN alone already answers SUCCESS.
     */
    fun canEnforceAppLock(availability: BiometricAvailability): Boolean =
        availability != BiometricAvailability.NOT_ENROLLED

    fun canEnforceAppLock(context: Context): Boolean = canEnforceAppLock(check(context))
}

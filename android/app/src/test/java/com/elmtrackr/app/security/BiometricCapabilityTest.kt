package com.elmtrackr.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers which availability answers suspend app-lock enforcement. The mapping
 * from `BiometricManager` status codes to [BiometricAvailability] needs a device
 * and is not exercised here; this pins the policy applied to the result.
 */
class BiometricCapabilityTest {

    @Test
    fun `an enrolled credential enforces the lock`() {
        assertTrue(BiometricCapability.canEnforceAppLock(BiometricAvailability.AVAILABLE))
    }

    @Test
    fun `no enrolled credential suspends the lock`() {
        // The lockout case: the prompt has nothing to authenticate against, so
        // enforcing would hold the user out permanently.
        assertFalse(BiometricCapability.canEnforceAppLock(BiometricAvailability.NOT_ENROLLED))
    }

    @Test
    fun `an unavailable sensor keeps enforcing`() {
        // UNAVAILABLE folds a sensor that is merely busy together with hardware
        // that will never work. Opening the lock for the transient half would make
        // the lock worthless, so this case stays closed on purpose.
        assertTrue(BiometricCapability.canEnforceAppLock(BiometricAvailability.UNAVAILABLE))
    }

    @Test
    fun `every availability value has a decision`() {
        // Guards against a new enum entry defaulting to "enforce" unnoticed, which
        // for a genuinely unsatisfiable state would reintroduce the lockout.
        BiometricAvailability.entries.forEach { availability ->
            val enforced = BiometricCapability.canEnforceAppLock(availability)
            assertTrue(
                "$availability must be a deliberate decision",
                enforced == (availability != BiometricAvailability.NOT_ENROLLED),
            )
        }
    }
}

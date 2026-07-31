package com.elmtrackr.app.domain.projects

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaidProjectsDiscoveryTest {

    @Test
    fun `shown to an existing user who has not seen it`() {
        assertTrue(
            PaidProjectsDiscovery.shouldShow(
                onboardingCompleted = true,
                paidProjectsEnabled = false,
                dismissed = false,
            ),
        )
    }

    @Test
    fun `never shown again after a dismissal`() {
        assertFalse(
            PaidProjectsDiscovery.shouldShow(
                onboardingCompleted = true,
                paidProjectsEnabled = false,
                dismissed = true,
            ),
        )
    }

    @Test
    fun `not shown once the feature is already on`() {
        assertFalse(
            PaidProjectsDiscovery.shouldShow(
                onboardingCompleted = true,
                paidProjectsEnabled = true,
                dismissed = false,
            ),
        )
    }

    @Test
    fun `not shown mid-onboarding where the wizard already offers it`() {
        assertFalse(
            PaidProjectsDiscovery.shouldShow(
                onboardingCompleted = false,
                paidProjectsEnabled = false,
                dismissed = false,
            ),
        )
    }
}

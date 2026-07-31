package com.elmtrackr.app.domain.projects

/**
 * Decides whether the one-time "Paid Projects is available" entry should be
 * shown to an existing user.
 *
 * Existing users are never pushed back through onboarding, so the settings
 * screen is where they find out the module exists. The entry appears once and
 * stays gone after a dismissal or after the feature is switched on.
 */
object PaidProjectsDiscovery {

    /**
     * @param onboardingCompleted false for users still in the wizard — they are
     * offered the feature there and must not also see the nudge.
     * @param paidProjectsEnabled a user who already has the module on has
     * nothing to discover.
     * @param dismissed set once the user taps "Not now"; never re-shown.
     */
    fun shouldShow(
        onboardingCompleted: Boolean,
        paidProjectsEnabled: Boolean,
        dismissed: Boolean,
    ): Boolean = onboardingCompleted && !paidProjectsEnabled && !dismissed
}

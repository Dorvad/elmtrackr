package com.elmtrackr.app.fake

import com.elmtrackr.app.data.local.preferences.OnboardingPreferences

class FakeOnboardingPreferences : OnboardingPreferences {
    var onboardingCompleted: Boolean = false

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        onboardingCompleted = completed
    }
}

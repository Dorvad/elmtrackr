package com.elmtrackr.app.data.local.preferences

interface OnboardingPreferences {
    suspend fun setOnboardingCompleted(completed: Boolean)
}

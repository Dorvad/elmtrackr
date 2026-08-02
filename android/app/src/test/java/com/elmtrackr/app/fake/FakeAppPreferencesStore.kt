package com.elmtrackr.app.fake

import com.elmtrackr.app.data.local.preferences.AppPreferenceValues
import com.elmtrackr.app.data.local.preferences.AppPreferencesStore

class FakeAppPreferencesStore(
    private var firstClockInCelebrated: Boolean = false,
    var firstClockInCelebrationPending: Boolean = false,
    var onboardingCompleted: Boolean = false,
) : AppPreferencesStore {
    override suspend fun currentPreferences(): AppPreferenceValues =
        AppPreferenceValues(
            firstClockInCelebrated = firstClockInCelebrated,
            firstClockInCelebrationPending = firstClockInCelebrationPending,
            onboardingCompleted = onboardingCompleted,
        )

    override suspend fun setFirstClockInCelebrated(celebrated: Boolean) {
        firstClockInCelebrated = celebrated
    }

    override suspend fun setFirstClockInCelebrationPending(pending: Boolean) {
        firstClockInCelebrationPending = pending
    }
}

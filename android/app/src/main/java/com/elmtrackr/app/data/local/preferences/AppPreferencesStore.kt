package com.elmtrackr.app.data.local.preferences

/** Subset of app preferences used by dashboard / first-run flows. */
interface AppPreferencesStore {
    suspend fun currentPreferences(): AppPreferenceValues
    suspend fun setFirstClockInCelebrated(celebrated: Boolean)
}

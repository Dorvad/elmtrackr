package com.elmtrackr.app.data.local.preferences

/** Subset of app preferences used by dashboard / first-run flows. */
interface AppPreferencesStore {
    suspend fun currentPreferences(): AppPreferenceValues
    suspend fun setFirstClockInCelebrated(celebrated: Boolean)

    /** Set by headless punches (widget/Wear) so the dashboard can still show the one-time celebration. */
    suspend fun setFirstClockInCelebrationPending(pending: Boolean)
}

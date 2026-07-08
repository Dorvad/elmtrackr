package com.elmtrackr.app.data.local.preferences

import kotlinx.coroutines.flow.Flow

/** Persistence for the dashboard getting-started checklist. */
interface SetupChecklistPreferences {
    val preferences: Flow<AppPreferenceValues>
    suspend fun setSetupChecklistDismissed(dismissed: Boolean)
    suspend fun markSetupStepVisited(stepKey: String)
    suspend fun setSetupChecklistCelebrated(celebrated: Boolean)
}

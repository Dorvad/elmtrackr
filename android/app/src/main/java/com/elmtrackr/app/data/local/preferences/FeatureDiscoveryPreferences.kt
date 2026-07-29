package com.elmtrackr.app.data.local.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Dismissal state for one-time feature-discovery entries.
 *
 * Device-local on purpose: a discovery nudge is a UI hint, not user data, and
 * this mirrors how the dashboard setup checklist stores its dismissal.
 */
interface FeatureDiscoveryPreferences {
    val preferences: Flow<AppPreferenceValues>
    suspend fun setPaidProjectsDiscoveryDismissed(dismissed: Boolean)
}

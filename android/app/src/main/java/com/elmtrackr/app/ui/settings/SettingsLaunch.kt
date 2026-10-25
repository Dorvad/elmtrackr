package com.elmtrackr.app.ui.settings

/**
 * Settings sub-screens other features (e.g. the dashboard setup checklist) can
 * open directly, without exposing the internal [SettingsDestination] state.
 */
enum class SettingsLaunchRequest {
    APPEARANCE,
    COMPENSATION,
    PREMIUM,
    PROFILE,
    FEATURES,
    SECURITY,

    /** The dashboard's sync notice: what is waiting, what failed, and "Sync now". */
    SYNC_DETAILS,
}

internal fun SettingsLaunchRequest.toDestination(): SettingsDestination = when (this) {
    SettingsLaunchRequest.APPEARANCE -> SettingsDestination.APPEARANCE
    SettingsLaunchRequest.COMPENSATION -> SettingsDestination.COMPENSATION
    SettingsLaunchRequest.PREMIUM -> SettingsDestination.PREMIUM
    SettingsLaunchRequest.PROFILE -> SettingsDestination.PROFILE
    SettingsLaunchRequest.FEATURES -> SettingsDestination.FEATURES
    SettingsLaunchRequest.SECURITY -> SettingsDestination.SECURITY
    SettingsLaunchRequest.SYNC_DETAILS -> SettingsDestination.SYNC_DETAILS
}

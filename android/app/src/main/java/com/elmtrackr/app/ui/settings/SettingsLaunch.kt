package com.elmtrackr.app.ui.settings

/**
 * Settings sub-screens other features (e.g. the dashboard setup checklist) can
 * open directly, without exposing the internal [SettingsDestination] state.
 */
enum class SettingsLaunchRequest {
    APPEARANCE,
    COMPENSATION,
    PREMIUM,
}

internal fun SettingsLaunchRequest.toDestination(): SettingsDestination = when (this) {
    SettingsLaunchRequest.APPEARANCE -> SettingsDestination.APPEARANCE
    SettingsLaunchRequest.COMPENSATION -> SettingsDestination.COMPENSATION
    SettingsLaunchRequest.PREMIUM -> SettingsDestination.PREMIUM
}

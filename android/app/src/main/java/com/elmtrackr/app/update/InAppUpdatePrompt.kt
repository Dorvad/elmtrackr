package com.elmtrackr.app.update

/**
 * UI prompts surfaced by [InAppUpdateManager] through [InAppUpdateHost].
 */
sealed interface InAppUpdatePrompt {
    /** A flexible update finished downloading; the user should restart to install. */
    data object RestartReady : InAppUpdatePrompt

    /** In-app update flows could not run; offer the Play Store listing instead. */
    data object PlayStore : InAppUpdatePrompt
}

package com.elmtrackr.app.update

/**
 * Pure time-window rules for when we may re-offer a flexible in-app update or
 * show the Play Store fallback snackbar. Kept free of Android types for tests.
 */
object InAppUpdateCooldown {
    /** Wait this long after the user dismisses a flexible update before re-prompting. */
    const val FLEXIBLE_DISMISS_COOLDOWN_MS = 48L * 60 * 60 * 1000

    /** Minimum gap between Play Store fallback snackbars. */
    const val PLAY_STORE_FALLBACK_COOLDOWN_MS = 24L * 60 * 60 * 1000

    fun shouldOfferFlexiblePrompt(lastDismissedAtMs: Long?, nowMs: Long): Boolean {
        if (lastDismissedAtMs == null) return true
        return nowMs - lastDismissedAtMs >= FLEXIBLE_DISMISS_COOLDOWN_MS
    }

    fun shouldShowPlayStoreFallback(lastShownAtMs: Long?, nowMs: Long): Boolean {
        if (lastShownAtMs == null) return true
        return nowMs - lastShownAtMs >= PLAY_STORE_FALLBACK_COOLDOWN_MS
    }
}

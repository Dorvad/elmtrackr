package com.elmtrackr.app.data.local.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Which of the app's one-time explanations and nudges the user has already had.
 *
 * Device-local on purpose: a nudge is a UI hint, not user data, and this mirrors
 * how the dashboard setup checklist stores its dismissal.
 *
 * The entries here dismiss differently by design. The Paid Projects wizard and
 * the projects guide are one-time, so a boolean retires each for good. The refund
 * reminder recurs every month, so it stores *which* month was dismissed and
 * re-arms on its own — see [AppPreferenceKeys.REFUND_REMINDER_DISMISSED_MONTH].
 */
interface FeatureDiscoveryPreferences {
    val preferences: Flow<AppPreferenceValues>
    suspend fun setPaidProjectsDiscoveryDismissed(dismissed: Boolean)

    /**
     * Records that the Projects tab's guide has been shown.
     *
     * Separate from [setPaidProjectsDiscoveryDismissed], which retires a
     * different thing: that one is the dashboard announcement asking whether to
     * turn the module *on*, and it is answered before the user has seen the tab.
     * This one is the how-to inside the tab, and a user who enabled the module
     * from the announcement still needs it.
     */
    suspend fun setProjectsGuideSeen(seen: Boolean)

    /** @param month `YYYY-MM`, as printed by [java.time.YearMonth.toString]. */
    suspend fun setRefundReminderDismissedMonth(month: String)
}

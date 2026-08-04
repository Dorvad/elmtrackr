package com.elmtrackr.app.data.local.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Dismissal state for the dashboard's discovery entries and nudges.
 *
 * Device-local on purpose: a nudge is a UI hint, not user data, and this mirrors
 * how the dashboard setup checklist stores its dismissal.
 *
 * The two entries here dismiss differently by design. The Paid Projects wizard is
 * a one-time announcement, so a boolean retires it for good. The refund reminder
 * recurs every month, so it stores *which* month was dismissed and re-arms on its
 * own — see [AppPreferenceKeys.REFUND_REMINDER_DISMISSED_MONTH].
 */
interface FeatureDiscoveryPreferences {
    val preferences: Flow<AppPreferenceValues>
    suspend fun setPaidProjectsDiscoveryDismissed(dismissed: Boolean)

    /** @param month `YYYY-MM`, as printed by [java.time.YearMonth.toString]. */
    suspend fun setRefundReminderDismissedMonth(month: String)
}

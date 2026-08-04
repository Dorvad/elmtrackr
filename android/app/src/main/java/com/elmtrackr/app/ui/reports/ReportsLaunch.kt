package com.elmtrackr.app.ui.reports

/**
 * A request from another destination to open Reports on a particular tab.
 *
 * Reports owns its tab selection, so the tab cannot be a navigation argument
 * without the route carrying UI state. This mirrors [com.elmtrackr.app.ui.settings.SettingsLaunchRequest]:
 * the scaffold holds the request, the screen consumes it once and clears it, so a
 * later return to the tab shows wherever the user actually left off.
 *
 * Only the cases another screen genuinely needs are listed. Hours is the default
 * and needs no request.
 */
enum class ReportsLaunchRequest {
    /** The dashboard's refund reminder: the claims it counts live on this tab. */
    REFUNDS,
}

internal fun ReportsLaunchRequest.toTab(): ReportTab = when (this) {
    ReportsLaunchRequest.REFUNDS -> ReportTab.REFUNDS
}

package com.elmtrackr.app.update

import com.elmtrackr.app.BuildConfig
import com.elmtrackr.app.monitoring.CrashReporting
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Lightweight breadcrumbs for the in-app update funnel. Visible in Sentry when
 * crash reporting is enabled; otherwise no-ops.
 */
object UpdateDiagnostics {

    fun recordCheck(
        status: InAppUpdateStatus,
        action: InAppUpdateAction,
        flexibleFlowStarted: Boolean,
    ) {
        breadcrumb(
            message = "update_check",
            data = mapOf(
                "update_available" to status.updateAvailable,
                "action" to action.name,
                "priority" to status.priority,
                "staleness_days" to (status.stalenessDays?.toString() ?: "null"),
                "flexible_allowed" to status.flexibleAllowed,
                "immediate_allowed" to status.immediateAllowed,
                "update_in_progress" to status.updateInProgress,
                "flexible_flow_started" to flexibleFlowStarted,
                "version_code" to BuildConfig.VERSION_CODE,
            ),
        )
    }

    fun recordUserDismissed() {
        breadcrumb(message = "flexible_update_dismissed")
    }

    fun recordFlowFailed(resultCode: Int) {
        breadcrumb(
            message = "update_flow_failed",
            data = mapOf("result_code" to resultCode),
            level = SentryLevel.WARNING,
        )
    }

    fun recordPlayStoreFallback(reason: String, status: InAppUpdateStatus) {
        breadcrumb(
            message = "play_store_fallback",
            data = mapOf(
                "reason" to reason,
                "priority" to status.priority,
                "flexible_allowed" to status.flexibleAllowed,
                "immediate_allowed" to status.immediateAllowed,
            ),
            level = SentryLevel.INFO,
        )
    }

    private fun breadcrumb(
        message: String,
        data: Map<String, Any> = emptyMap(),
        level: SentryLevel = SentryLevel.DEBUG,
    ) {
        if (!CrashReporting.isAvailable()) return
        val crumb = Breadcrumb()
        crumb.category = "in_app_update"
        crumb.message = message
        crumb.level = level
        data.forEach { (key, value) -> crumb.setData(key, value) }
        Sentry.addBreadcrumb(crumb)
    }
}

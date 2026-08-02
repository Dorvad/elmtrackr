package com.elmtrackr.app.review

import com.elmtrackr.app.monitoring.CrashReporting
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Breadcrumbs for the review-prompt funnel, mirroring
 * [com.elmtrackr.app.update.UpdateDiagnostics]. Visible in Sentry when crash
 * reporting is enabled; otherwise no-ops. Never touches prompt state.
 */
object ReviewDiagnostics {

    fun recordEvaluation(inputs: ReviewPromptInputs, verdict: ReviewPromptVerdict) {
        breadcrumb(
            message = "review_eligibility_evaluated",
            data = mapOf(
                "eligible" to verdict.eligible,
                "strong_milestone" to verdict.hasStrongMilestone,
                "blockers" to verdict.blockers.joinToString(",").ifEmpty { "none" },
                "valid_shift_count" to inputs.validShiftCount,
                "usage_day_count" to inputs.usageDayCount,
            ),
        )
    }

    fun recordLaunchHandedToPlay() {
        breadcrumb(message = "review_flow_handed_to_play")
    }

    fun recordLaunchFailed(e: Exception) {
        breadcrumb(
            message = "review_flow_unavailable",
            data = mapOf("error" to (e.message ?: e.javaClass.simpleName)),
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
        crumb.category = "in_app_review"
        crumb.message = message
        crumb.level = level
        data.forEach { (key, value) -> crumb.setData(key, value) }
        Sentry.addBreadcrumb(crumb)
    }
}

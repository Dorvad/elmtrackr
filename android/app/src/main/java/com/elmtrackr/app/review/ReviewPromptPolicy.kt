package com.elmtrackr.app.review

import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.Shift

/**
 * Everything [ReviewPromptPolicy] needs to decide whether asking for a Play
 * Store review is appropriate right now. One flat struct, deliberately free of
 * Play / Android types, so the decision can be unit tested on the JVM and
 * rendered verbatim by the debug inspector.
 *
 * Timestamps are epoch millis; null means "never happened / unknown".
 */
data class ReviewPromptInputs(
    val nowMs: Long,
    /** Completed shifts long enough to count as real work (see [ReviewPromptPolicy.countValidShifts]). */
    val validShiftCount: Int = 0,
    /** When the user last viewed a completed (fully past) monthly report with shifts in it. */
    val monthlyReportViewedAt: Long? = null,
    /** When the user last exported a report (CSV or PDF) successfully. */
    val reportExportedAt: Long? = null,
    /** Number of distinct calendar days the app has been opened on. */
    val usageDayCount: Int = 0,
    /** First recorded app usage; null until the first foreground is recorded. */
    val firstUsedAt: Long? = null,
    val lastClockInAt: Long? = null,
    /** Last error, failed purchase, missed reminder or similar negative moment. */
    val lastDiscouragingEventAt: Long? = null,
    /** Last time a review flow was attempted, whether or not Play showed UI. */
    val lastAttemptAt: Long? = null,
    /** True while first-run onboarding (or its replay) has not been completed. */
    val onboardingActive: Boolean = false,
    /** True while a shift is currently clocked in. */
    val shiftActive: Boolean = false,
)

/** Why a review request is currently not allowed. Empty list = eligible. */
enum class ReviewPromptBlocker {
    ONBOARDING_ACTIVE,
    SHIFT_ACTIVE,
    APP_TOO_NEW,
    TOO_SOON_AFTER_CLOCK_IN,
    RECENT_DISCOURAGING_EVENT,
    RECENTLY_ATTEMPTED,
    NO_STRONG_MILESTONE,
    NOT_ENOUGH_USAGE_DAYS,
}

data class ReviewPromptVerdict(
    val eligible: Boolean,
    val hasStrongMilestone: Boolean,
    val blockers: List<ReviewPromptBlocker>,
)

/**
 * Pure eligibility rules for the Google Play in-app review prompt.
 *
 * A request is allowed only after the user has demonstrably gotten value out
 * of the app — a strong milestone (five valid shifts, a completed monthly
 * report viewed, or a report exported) plus continued usage across several
 * days — and only at a calm moment: never during onboarding, right after
 * install or clock-in, close to an error, while a shift is running, or inside
 * the long cooldown after a previous attempt.
 *
 * The policy never knows whether Play actually displayed the review UI (the
 * API gives no signal, by design), so "attempted" means we asked Play, not
 * that the user saw anything.
 */
object ReviewPromptPolicy {

    /** Valid shifts needed for the shift-count milestone. */
    const val STRONG_MILESTONE_SHIFTS = 5

    /** A completed shift shorter than this is noise, not a milestone. */
    const val MIN_VALID_SHIFT_MINUTES = 15

    /** Continued-usage requirement: distinct days the app was opened on. */
    const val MIN_USAGE_DAYS = 3

    /** Never prompt within this window after the first recorded usage. */
    const val MIN_APP_AGE_MS = 3L * 24 * 60 * 60 * 1000

    /** Quiet period after a clock-in, even if the shift has already ended. */
    const val CLOCK_IN_QUIET_MS = 30L * 60 * 1000

    /** Quiet period after an error, failed purchase or missed reminder. */
    const val DISCOURAGING_EVENT_QUIET_MS = 24L * 60 * 60 * 1000

    /** Long cooldown between review attempts, whatever their outcome. */
    const val ATTEMPT_COOLDOWN_MS = 90L * 24 * 60 * 60 * 1000

    fun evaluate(inputs: ReviewPromptInputs): ReviewPromptVerdict {
        val blockers = mutableListOf<ReviewPromptBlocker>()
        val now = inputs.nowMs

        if (inputs.onboardingActive) blockers += ReviewPromptBlocker.ONBOARDING_ACTIVE
        if (inputs.shiftActive) blockers += ReviewPromptBlocker.SHIFT_ACTIVE

        // No recorded first usage counts as "brand new install": stay silent.
        val appAge = inputs.firstUsedAt?.let { now - it }
        if (appAge == null || appAge < MIN_APP_AGE_MS) {
            blockers += ReviewPromptBlocker.APP_TOO_NEW
        }

        if (inputs.lastClockInAt != null && now - inputs.lastClockInAt < CLOCK_IN_QUIET_MS) {
            blockers += ReviewPromptBlocker.TOO_SOON_AFTER_CLOCK_IN
        }
        if (inputs.lastDiscouragingEventAt != null &&
            now - inputs.lastDiscouragingEventAt < DISCOURAGING_EVENT_QUIET_MS
        ) {
            blockers += ReviewPromptBlocker.RECENT_DISCOURAGING_EVENT
        }
        if (inputs.lastAttemptAt != null && now - inputs.lastAttemptAt < ATTEMPT_COOLDOWN_MS) {
            blockers += ReviewPromptBlocker.RECENTLY_ATTEMPTED
        }

        val hasStrongMilestone = inputs.validShiftCount >= STRONG_MILESTONE_SHIFTS ||
            inputs.monthlyReportViewedAt != null ||
            inputs.reportExportedAt != null
        if (!hasStrongMilestone) blockers += ReviewPromptBlocker.NO_STRONG_MILESTONE
        if (inputs.usageDayCount < MIN_USAGE_DAYS) {
            blockers += ReviewPromptBlocker.NOT_ENOUGH_USAGE_DAYS
        }

        return ReviewPromptVerdict(
            eligible = blockers.isEmpty(),
            hasStrongMilestone = hasStrongMilestone,
            blockers = blockers,
        )
    }

    /**
     * Counts shifts that represent real, finished work: clocked out and at
     * least [MIN_VALID_SHIFT_MINUTES] long after breaks. Counting from the
     * database (rather than incrementing a stored counter) keeps the milestone
     * honest when shifts are edited or deleted later.
     */
    fun countValidShifts(shifts: List<Shift>): Int = shifts.count { shift ->
        shift.isCompleted && (ShiftDurationCalculator.netMinutes(shift) ?: 0) >= MIN_VALID_SHIFT_MINUTES
    }
}

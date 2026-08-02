package com.elmtrackr.app.review

/**
 * The narrow surface feature code uses to feed the review-prompt heuristics.
 * ViewModels depend on this interface (not on [ReviewPromptCoordinator]) so
 * they stay constructible in JVM tests with a no-op fake.
 *
 * All methods are fire-and-forget and must never block or throw into callers.
 */
interface ReviewPromptRecorder {
    /** Call when the app comes to the foreground; feeds usage-day tracking. */
    fun noteAppForegrounded()

    /** Call on clock-in; opens the post-clock-in quiet period. */
    fun noteClockIn()

    /** Call after a shift was clocked out successfully. May trigger a review request. */
    fun noteShiftCompleted()

    /** Call when a completed (fully past) monthly report with shifts was viewed. */
    fun noteMonthlyReportViewed()

    /** Call after a report export was handed to the share sheet without error. */
    fun noteReportExported()

    /** Call on errors, failed purchases, missed reminders — suppresses prompts for a while. */
    fun noteDiscouragingEvent()
}

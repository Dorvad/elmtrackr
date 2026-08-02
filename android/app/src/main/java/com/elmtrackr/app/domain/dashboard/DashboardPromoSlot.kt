package com.elmtrackr.app.domain.dashboard

import java.time.YearMonth

/**
 * Which nudge, if any, the dashboard shows.
 *
 * Four promotional surfaces had been added independently — a first-run welcome
 * card, an end-of-month refund reminder, the setup checklist and the
 * post-project-shift summary — each gated only on its own condition. Nothing
 * arbitrated between them, so in the worst case all four stacked above the
 * month summary, all using the same card treatment and all competing with the
 * clock for attention.
 *
 * One slot, one occupant, decided here rather than by whichever `if` happened to
 * come first in the layout.
 */
enum class DashboardPromo {
    /** Unresolved travel refunds, shown only in the last days of the month. */
    REFUND_REMINDER,

    /** Shown until the first shift exists. */
    FIRST_RUN_WELCOME,

    /** The guided getting-started checklist. */
    SETUP_CHECKLIST,

    /** Recap of the project shift that just ended. */
    PROJECT_SUMMARY,
}

data class DashboardPromoInputs(
    val unresolvedRefundCount: Int,
    val isRefundWindow: Boolean,
    val refundReminderDismissed: Boolean,
    val hasNoShifts: Boolean,
    val setupChecklistAvailable: Boolean,
    val projectSummaryAvailable: Boolean,
    /** The update wizard is a full-screen dialog; nothing should stack under it. */
    val updateWizardVisible: Boolean = false,
)

/**
 * Returns the eligible nudges in priority order. The dashboard renders the
 * first, if any.
 *
 * The ordering is by how time-bounded each one is, not by importance:
 *
 * 1. [DashboardPromo.REFUND_REMINDER] — only appears in a few days each month
 *    and expires on its own, so a later slot may never come round.
 * 2. [DashboardPromo.FIRST_RUN_WELCOME] — only meaningful before the first
 *    shift exists, which is a window of minutes for an engaged user. Suppressed
 *    entirely while the checklist is available, since they ask for the same tap.
 * 3. [DashboardPromo.SETUP_CHECKLIST] — persistent and dismissible; it can wait.
 * 4. [DashboardPromo.PROJECT_SUMMARY] — the shift it recaps is already saved and
 *    visible in the list, so this is the most redundant of the four.
 *
 * The full list is returned rather than a single value so callers can express
 * "show the top one" while tests can assert the whole ranking.
 */
fun resolveDashboardPromos(inputs: DashboardPromoInputs): List<DashboardPromo> {
    if (inputs.updateWizardVisible) return emptyList()

    return buildList {
        if (inputs.unresolvedRefundCount > 0 &&
            inputs.isRefundWindow &&
            !inputs.refundReminderDismissed
        ) {
            add(DashboardPromo.REFUND_REMINDER)
        }
        // The welcome card yields to the checklist rather than outranking it. Both
        // carry the same "clock in once" call to action — the checklist's FIRST_SHIFT
        // step is the same tap — so a brand-new dashboard showing the card would
        // duplicate the prompt, and the checklist is the richer surface.
        //
        // Carried over from the first-run journey work, which expressed it as
        // `setupChecklist == null` at the call site. Encoded here instead so the
        // ranking stays in one tested place, and so the refund reminder keeps its
        // position above both.
        if (inputs.hasNoShifts && !inputs.setupChecklistAvailable) {
            add(DashboardPromo.FIRST_RUN_WELCOME)
        }
        if (inputs.setupChecklistAvailable) add(DashboardPromo.SETUP_CHECKLIST)
        if (inputs.projectSummaryAvailable) add(DashboardPromo.PROJECT_SUMMARY)
    }
}

/** The nudge to render, or null when the dashboard should show none. */
fun activeDashboardPromo(inputs: DashboardPromoInputs): DashboardPromo? =
    resolveDashboardPromos(inputs).firstOrNull()

/**
 * Whether the refund reminder counts as dismissed for [month].
 *
 * The dismissal is stored as the month it was made in, not as a flag. Refund
 * claims are settled per month, so "not now" means "not for this month" — a flag
 * would silence every future month too, which is what the previous in-memory
 * version effectively did each time the process survived.
 *
 * Parsing is tolerant for the same reason the clock-face history is: the stored
 * value is a device-local hint, and a value written by another version, or left
 * behind by a corrupted store, must degrade to "not dismissed" rather than throw
 * on the dashboard's hot path. Showing the nudge once too often is recoverable;
 * crashing the main screen is not.
 */
fun isRefundReminderDismissed(storedMonth: String?, month: YearMonth): Boolean {
    val stored = storedMonth?.trim().orEmpty()
    if (stored.isEmpty()) return false
    return runCatching { YearMonth.parse(stored) }.getOrNull() == month
}

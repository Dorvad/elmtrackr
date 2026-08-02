package com.elmtrackr.app.domain.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class DashboardPromoSlotTest {

    private fun inputs(
        unresolvedRefundCount: Int = 0,
        isRefundWindow: Boolean = false,
        refundReminderDismissed: Boolean = false,
        hasNoShifts: Boolean = false,
        setupChecklistAvailable: Boolean = false,
        projectSummaryAvailable: Boolean = false,
        updateWizardVisible: Boolean = false,
    ) = DashboardPromoInputs(
        unresolvedRefundCount = unresolvedRefundCount,
        isRefundWindow = isRefundWindow,
        refundReminderDismissed = refundReminderDismissed,
        hasNoShifts = hasNoShifts,
        setupChecklistAvailable = setupChecklistAvailable,
        projectSummaryAvailable = projectSummaryAvailable,
        updateWizardVisible = updateWizardVisible,
    )

    @Test
    fun `nothing is shown when no nudge applies`() {
        assertNull(activeDashboardPromo(inputs()))
    }

    /**
     * The state this exists to prevent. Before the slot, all four rendered at
     * once, stacked above the month summary in identical cards.
     */
    @Test
    fun `only one nudge is shown when every condition is met at once`() {
        val all = inputs(
            unresolvedRefundCount = 3,
            isRefundWindow = true,
            hasNoShifts = true,
            setupChecklistAvailable = true,
            projectSummaryAvailable = true,
        )

        assertEquals(DashboardPromo.REFUND_REMINDER, activeDashboardPromo(all))
        assertEquals(3, resolveDashboardPromos(all).size)
    }

    @Test
    fun `the ranking is refund, checklist, project summary`() {
        val all = inputs(
            unresolvedRefundCount = 1,
            isRefundWindow = true,
            hasNoShifts = true,
            setupChecklistAvailable = true,
            projectSummaryAvailable = true,
        )

        assertEquals(
            listOf(
                DashboardPromo.REFUND_REMINDER,
                DashboardPromo.SETUP_CHECKLIST,
                DashboardPromo.PROJECT_SUMMARY,
            ),
            resolveDashboardPromos(all),
        )
    }

    /**
     * Both surfaces ask for the same thing — the checklist's first step *is*
     * "clock in once" — so a brand-new dashboard would otherwise carry the prompt
     * twice. The checklist is the richer of the two, so the card yields.
     */
    @Test
    fun `the welcome card yields to the checklist`() {
        val withChecklist = inputs(hasNoShifts = true, setupChecklistAvailable = true)
        assertEquals(DashboardPromo.SETUP_CHECKLIST, activeDashboardPromo(withChecklist))
        assertFalse(resolveDashboardPromos(withChecklist).contains(DashboardPromo.FIRST_RUN_WELCOME))

        val withoutChecklist = withChecklist.copy(setupChecklistAvailable = false)
        assertEquals(DashboardPromo.FIRST_RUN_WELCOME, activeDashboardPromo(withoutChecklist))
    }

    @Test
    fun `the refund reminder needs unresolved claims, the window and no dismissal`() {
        assertNull(activeDashboardPromo(inputs(unresolvedRefundCount = 2, isRefundWindow = false)))
        assertNull(activeDashboardPromo(inputs(unresolvedRefundCount = 0, isRefundWindow = true)))
        assertNull(
            activeDashboardPromo(
                inputs(unresolvedRefundCount = 2, isRefundWindow = true, refundReminderDismissed = true),
            ),
        )
        assertEquals(
            DashboardPromo.REFUND_REMINDER,
            activeDashboardPromo(inputs(unresolvedRefundCount = 2, isRefundWindow = true)),
        )
    }

    @Test
    fun `dismissing the refund reminder promotes the next nudge`() {
        val withRefund = inputs(
            unresolvedRefundCount = 1,
            isRefundWindow = true,
            setupChecklistAvailable = true,
        )
        assertEquals(DashboardPromo.REFUND_REMINDER, activeDashboardPromo(withRefund))

        val dismissed = withRefund.copy(refundReminderDismissed = true)
        assertEquals(DashboardPromo.SETUP_CHECKLIST, activeDashboardPromo(dismissed))
    }

    /**
     * The upgrade path that motivated this: a mid-setup user opening the app
     * after an update met a full-screen wizard over a dashboard that already
     * carried a welcome card and a six-step checklist.
     */
    @Test
    fun `nothing competes with the update wizard`() {
        val everything = inputs(
            unresolvedRefundCount = 5,
            isRefundWindow = true,
            hasNoShifts = true,
            setupChecklistAvailable = true,
            projectSummaryAvailable = true,
            updateWizardVisible = true,
        )

        assertTrue(resolveDashboardPromos(everything).isEmpty())
        assertNull(activeDashboardPromo(everything))
    }

    @Test
    fun `the welcome card disappears once a shift exists`() {
        assertEquals(DashboardPromo.FIRST_RUN_WELCOME, activeDashboardPromo(inputs(hasNoShifts = true)))
        assertNull(activeDashboardPromo(inputs(hasNoShifts = false)))
    }

    // ── Refund reminder dismissal ────────────────────────────────────────────

    private val august = YearMonth.of(2026, 8)

    @Test
    fun `a reminder never dismissed is not dismissed`() {
        assertFalse(isRefundReminderDismissed(null, august))
        assertFalse(isRefundReminderDismissed("", august))
        assertFalse(isRefundReminderDismissed("   ", august))
    }

    @Test
    fun `dismissing this month silences this month`() {
        assertTrue(isRefundReminderDismissed("2026-08", august))
    }

    /**
     * The point of storing a month rather than a flag: the reminder is recurring,
     * so last month's "not now" must not answer for this month's claims.
     */
    @Test
    fun `a dismissal does not carry into the next month`() {
        assertFalse(isRefundReminderDismissed("2026-07", august))
        assertFalse(isRefundReminderDismissed("2025-08", august))
    }

    /** A future value can only come from a clock change; it still is not this month. */
    @Test
    fun `a dismissal from a later month does not silence this one`() {
        assertFalse(isRefundReminderDismissed("2026-09", august))
    }

    /**
     * This runs on the dashboard's hot path, so an unreadable stored value has to
     * degrade to "show the nudge" rather than take the main screen down.
     */
    @Test
    fun `an unparseable stored value is treated as not dismissed`() {
        listOf("2026-13", "August", "2026", "2026-08-04").forEach { stored ->
            assertFalse(stored, isRefundReminderDismissed(stored, august))
        }
    }

    /** What YearMonth prints is what the comparison reads; no second format. */
    @Test
    fun `the stored form round-trips through YearMonth`() {
        assertTrue(isRefundReminderDismissed(august.toString(), august))
    }
}

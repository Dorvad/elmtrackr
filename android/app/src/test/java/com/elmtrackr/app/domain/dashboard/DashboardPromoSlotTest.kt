package com.elmtrackr.app.domain.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(4, resolveDashboardPromos(all).size)
    }

    @Test
    fun `the ranking is refund, welcome, checklist, project summary`() {
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
                DashboardPromo.FIRST_RUN_WELCOME,
                DashboardPromo.SETUP_CHECKLIST,
                DashboardPromo.PROJECT_SUMMARY,
            ),
            resolveDashboardPromos(all),
        )
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
}

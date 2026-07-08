package com.elmtrackr.app.domain.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupChecklistTest {

    private fun inputs(
        hasCompletedShift: Boolean = false,
        clockStyleCustomized: Boolean = false,
        compensationProfileCount: Int = 1,
        hasCustomPremiumProfile: Boolean = false,
        hasAnyTask: Boolean = false,
        hasPinnedWidget: Boolean = false,
        widgetPinSupported: Boolean = true,
        visitedStepKeys: Set<String> = emptySet(),
        dismissed: Boolean = false,
        celebrated: Boolean = false,
    ) = SetupChecklistInputs(
        hasCompletedShift = hasCompletedShift,
        clockStyleCustomized = clockStyleCustomized,
        compensationProfileCount = compensationProfileCount,
        hasCustomPremiumProfile = hasCustomPremiumProfile,
        hasAnyTask = hasAnyTask,
        hasPinnedWidget = hasPinnedWidget,
        widgetPinSupported = widgetPinSupported,
        visitedStepKeys = visitedStepKeys,
        dismissed = dismissed,
        celebrated = celebrated,
    )

    private fun SetupChecklistState.stepFor(step: SetupStep): SetupStepState =
        steps.first { it.step == step }

    @Test
    fun `fresh user sees all six steps incomplete`() {
        val state = SetupChecklist.build(inputs())
        assertNotNull(state)
        assertEquals(6, state!!.totalCount)
        assertEquals(0, state.completedCount)
        assertFalse(state.showCelebration)
    }

    @Test
    fun `dismissed checklist is hidden`() {
        assertNull(SetupChecklist.build(inputs(dismissed = true)))
    }

    @Test
    fun `widget step is omitted when pinning is unsupported`() {
        val state = SetupChecklist.build(inputs(widgetPinSupported = false))!!
        assertEquals(5, state.totalCount)
        assertTrue(state.steps.none { it.step == SetupStep.WIDGET })
    }

    @Test
    fun `completed shift ticks the first step`() {
        val state = SetupChecklist.build(inputs(hasCompletedShift = true))!!
        assertTrue(state.stepFor(SetupStep.FIRST_SHIFT).isComplete)
        assertEquals(1, state.completedCount)
    }

    @Test
    fun `changing clock style completes the clock step without a visit`() {
        val state = SetupChecklist.build(inputs(clockStyleCustomized = true))!!
        assertTrue(state.stepFor(SetupStep.CLOCK_STYLE).isComplete)
    }

    @Test
    fun `visiting compensation completes the step even with only the default profile`() {
        val state = SetupChecklist.build(
            inputs(visitedStepKeys = setOf(SetupStep.COMPENSATION.key)),
        )!!
        assertTrue(state.stepFor(SetupStep.COMPENSATION).isComplete)
    }

    @Test
    fun `second compensation profile completes the step without a visit`() {
        val state = SetupChecklist.build(inputs(compensationProfileCount = 2))!!
        assertTrue(state.stepFor(SetupStep.COMPENSATION).isComplete)
    }

    @Test
    fun `default premium profile alone does not complete the premium step`() {
        val state = SetupChecklist.build(inputs(hasCustomPremiumProfile = false))!!
        assertFalse(state.stepFor(SetupStep.PREMIUM).isComplete)
    }

    @Test
    fun `custom premium profile completes the premium step`() {
        val state = SetupChecklist.build(inputs(hasCustomPremiumProfile = true))!!
        assertTrue(state.stepFor(SetupStep.PREMIUM).isComplete)
    }

    @Test
    fun `all steps complete shows celebration until celebrated, then hides`() {
        val done = inputs(
            hasCompletedShift = true,
            clockStyleCustomized = true,
            compensationProfileCount = 2,
            hasCustomPremiumProfile = true,
            hasAnyTask = true,
            hasPinnedWidget = true,
        )
        val state = SetupChecklist.build(done)!!
        assertTrue(state.allComplete)
        assertTrue(state.showCelebration)

        assertNull(SetupChecklist.build(done.copy(celebrated = true)))
    }
}

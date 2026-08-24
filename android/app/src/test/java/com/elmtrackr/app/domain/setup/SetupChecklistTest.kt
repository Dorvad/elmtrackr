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
        hasWorkProfileRate: Boolean = false,
        hasCustomPremiumProfile: Boolean = false,
        hasAnyTask: Boolean = false,
        hasPinnedWidget: Boolean = false,
        widgetPinSupported: Boolean = true,
        hasDisplayName: Boolean = false,
        hasEnabledFeature: Boolean = false,
        appLockConfigured: Boolean = false,
        visitedStepKeys: Set<String> = emptySet(),
        dismissed: Boolean = false,
        celebrated: Boolean = false,
    ) = SetupChecklistInputs(
        hasCompletedShift = hasCompletedShift,
        hasDisplayName = hasDisplayName,
        hasEnabledFeature = hasEnabledFeature,
        appLockConfigured = appLockConfigured,
        clockStyleCustomized = clockStyleCustomized,
        compensationProfileCount = compensationProfileCount,
        hasWorkProfileRate = hasWorkProfileRate,
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
    fun `fresh user sees every step incomplete`() {
        val state = SetupChecklist.build(inputs())
        assertNotNull(state)
        assertEquals(SetupStep.entries.size, state!!.totalCount)
        assertEquals(0, state.completedCount)
        assertFalse(state.showCelebration)
    }

    /**
     * These three moved off the sign-up wizard. Each completes from real data
     * where there is data to read, so a user who already answered is not asked
     * again, and from a visit otherwise — leaving the features screen with
     * everything off is a real answer.
     */
    @Test
    fun `a name already on the profile completes its step`() {
        val state = SetupChecklist.build(inputs(hasDisplayName = true))!!

        assertTrue(state.stepFor(SetupStep.PROFILE_NAME).isComplete)
    }

    @Test
    fun `an app lock already switched on completes its step`() {
        val state = SetupChecklist.build(inputs(appLockConfigured = true))!!

        assertTrue(state.stepFor(SetupStep.APP_LOCK).isComplete)
    }

    @Test
    fun `a feature already switched on completes the features step`() {
        val state = SetupChecklist.build(inputs(hasEnabledFeature = true))!!

        assertTrue(state.stepFor(SetupStep.FEATURES).isComplete)
    }

    @Test
    fun `visiting the features screen completes its step whatever was chosen`() {
        val state = SetupChecklist.build(
            inputs(visitedStepKeys = setOf(SetupStep.FEATURES.key)),
        )!!

        assertTrue(state.stepFor(SetupStep.FEATURES).isComplete)
    }

    @Test
    fun `dismissed checklist is hidden`() {
        assertNull(SetupChecklist.build(inputs(dismissed = true)))
    }

    @Test
    fun `widget step is omitted when pinning is unsupported`() {
        val state = SetupChecklist.build(inputs(widgetPinSupported = false))!!
        assertEquals(SetupStep.entries.size - 1, state.totalCount)
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

    /**
     * The signal the step actually asks for. It used to be a *second* work
     * profile, which most people never have — so the step could only ever be
     * cleared by opening the screen, and the one thing every pay figure depends
     * on went unchecked.
     */
    @Test
    fun `an hourly rate completes the work profile step without a visit`() {
        val state = SetupChecklist.build(inputs(hasWorkProfileRate = true))!!
        assertTrue(state.stepFor(SetupStep.COMPENSATION).isComplete)
    }

    @Test
    fun `a second work profile with no rate does not complete the step`() {
        val state = SetupChecklist.build(inputs(compensationProfileCount = 2))!!
        assertFalse(state.stepFor(SetupStep.COMPENSATION).isComplete)
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
            hasWorkProfileRate = true,
            hasCustomPremiumProfile = true,
            hasAnyTask = true,
            hasPinnedWidget = true,
            hasDisplayName = true,
            hasEnabledFeature = true,
            appLockConfigured = true,
            // Engagement evidence: the user followed at least one checklist CTA
            // (or onboarding recorded a visit on their behalf).
            visitedStepKeys = setOf(SetupStep.COMPENSATION.key),
        )
        val state = SetupChecklist.build(done)!!
        assertTrue(state.allComplete)
        assertTrue(state.showCelebration)

        assertNull(SetupChecklist.build(done.copy(celebrated = true)))
    }

    /**
     * Adding steps in a later version must not resurrect the checklist for
     * someone who already cleared it.
     */
    @Test
    fun `a user who already celebrated never sees the checklist again`() {
        assertNull(SetupChecklist.build(inputs(celebrated = true)))
    }

    @Test
    fun `checklist that arrives already complete stays hidden for upgraders`() {
        // An existing user whose data satisfies every signal, with no recorded
        // interaction, must not get a surprise "You're all set!" dialog — or the
        // finished card — on first launch after an upgrade.
        val upgrader = inputs(
            hasCompletedShift = true,
            clockStyleCustomized = true,
            hasWorkProfileRate = true,
            hasCustomPremiumProfile = true,
            hasAnyTask = true,
            hasPinnedWidget = true,
            hasDisplayName = true,
            hasEnabledFeature = true,
            appLockConfigured = true,
            visitedStepKeys = emptySet(),
        )
        assertNull(SetupChecklist.build(upgrader))
    }
}

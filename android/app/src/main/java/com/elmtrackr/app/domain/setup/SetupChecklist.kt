package com.elmtrackr.app.domain.setup

/**
 * The guided getting-started checklist shown on the dashboard after onboarding.
 *
 * Steps either auto-complete from real data (a completed shift, a created task,
 * a pinned widget, a customized clock style, a work profile with an hourly rate,
 * a user-created premium profile) or complete when the user opens the relevant
 * screen from the checklist ("visited" steps, keyed by [SetupStep.key] in
 * preferences).
 */
enum class SetupStep(val key: String) {
    FIRST_SHIFT("first_shift"),
    // The three below used to be forced screens in the sign-up wizard. None of
    // them has to be answered before the app works — a name is for a greeting, a
    // feature toggle is an opt-in, an app lock is a preference — so they belong
    // here, where they can be answered when the user cares rather than before
    // they have seen anything.
    PROFILE_NAME("profile_name"),
    FEATURES("features"),
    APP_LOCK("app_lock"),
    CLOCK_STYLE("clock_style"),
    COMPENSATION("compensation"),
    PREMIUM("premium"),
    TASKS("tasks"),
    WIDGET("widget"),
}

data class SetupChecklistInputs(
    val hasCompletedShift: Boolean,
    val hasDisplayName: Boolean,
    val hasEnabledFeature: Boolean,
    val appLockConfigured: Boolean,
    val clockStyleCustomized: Boolean,
    /** Read by callers building the checklist; no step is gated on it any more. */
    val compensationProfileCount: Int,
    /**
     * True when the default work profile has an hourly rate.
     *
     * The signal that the step actually asks for. It used to be
     * `compensationProfileCount > 1` — a *second* job — which is a thing most
     * people never have, so the step could only ever be cleared by opening the
     * screen. A rate is what every pay figure in the app depends on, and its
     * absence is exactly what the step exists to catch.
     */
    val hasWorkProfileRate: Boolean = false,
    val hasCustomPremiumProfile: Boolean,
    val hasAnyTask: Boolean,
    val hasPinnedWidget: Boolean,
    val widgetPinSupported: Boolean,
    val visitedStepKeys: Set<String>,
    val dismissed: Boolean,
    val celebrated: Boolean,
)

data class SetupStepState(
    val step: SetupStep,
    val isComplete: Boolean,
)

data class SetupChecklistState(
    val steps: List<SetupStepState>,
    val completedCount: Int,
    val totalCount: Int,
    /** All steps done but the one-time celebration has not been shown yet. */
    val showCelebration: Boolean,
) {
    val allComplete: Boolean get() = completedCount == totalCount
}

object SetupChecklist {

    /** Returns null when the checklist should not be shown at all. */
    fun build(inputs: SetupChecklistInputs): SetupChecklistState? {
        if (inputs.dismissed) return null

        // Someone who already finished the checklist is done with it for good,
        // including when a later version adds steps. It is a one-time
        // getting-started aid, not a to-do list that grows back under a user who
        // had already cleared it.
        if (inputs.celebrated) return null

        val steps = SetupStep.entries
            .filter { it != SetupStep.WIDGET || inputs.widgetPinSupported }
            .map { SetupStepState(it, isComplete(it, inputs)) }
        val completed = steps.count { it.isComplete }
        val allComplete = completed == steps.size

        // A checklist that arrives already finished — an upgrading user whose
        // existing data satisfies every signal — has nothing to teach and no
        // celebration is owed for work never framed as a checklist. Visited
        // steps are the engagement evidence (onboarding and the CTAs record
        // them); without any, stay hidden instead of popping "You're all set!".
        if (allComplete && inputs.visitedStepKeys.isEmpty()) return null

        return SetupChecklistState(
            steps = steps,
            completedCount = completed,
            totalCount = steps.size,
            showCelebration = allComplete && !inputs.celebrated,
        )
    }

    private fun isComplete(step: SetupStep, inputs: SetupChecklistInputs): Boolean {
        val visited = step.key in inputs.visitedStepKeys
        return when (step) {
            SetupStep.FIRST_SHIFT -> inputs.hasCompletedShift
            // Data-driven where there is data to read, and "visited" otherwise:
            // leaving the features screen with everything off is a real answer,
            // and the checklist must not keep asking for it.
            SetupStep.PROFILE_NAME -> inputs.hasDisplayName || visited
            // Any optional feature already switched on counts, so an upgrading
            // user who configured these long ago is not asked again. Without a
            // data signal this step could only ever complete by visiting, which
            // would make "complete without any recorded visit" unreachable and
            // quietly disable the upgrader guard above.
            SetupStep.FEATURES -> inputs.hasEnabledFeature || visited
            SetupStep.APP_LOCK -> inputs.appLockConfigured || visited
            SetupStep.CLOCK_STYLE -> inputs.clockStyleCustomized || visited
            // A configured rate is the real answer: the profile itself is
            // auto-created, so its existence proves nothing, and a second one is
            // a thing most people never have. Reviewing the screen counts too —
            // someone whose job genuinely pays no fixed hourly rate has answered
            // the question by looking.
            SetupStep.COMPENSATION -> inputs.hasWorkProfileRate || visited
            // Same reasoning: a default premium profile always exists.
            SetupStep.PREMIUM -> inputs.hasCustomPremiumProfile || visited
            SetupStep.TASKS -> inputs.hasAnyTask
            SetupStep.WIDGET -> inputs.hasPinnedWidget
        }
    }
}

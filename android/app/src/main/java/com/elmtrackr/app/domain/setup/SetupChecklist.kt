package com.elmtrackr.app.domain.setup

/**
 * The guided getting-started checklist shown on the dashboard after onboarding.
 *
 * Steps either auto-complete from real data (a completed shift, a created task,
 * a pinned widget, a customized clock style, a user-created premium or second
 * compensation profile) or complete when the user opens the relevant screen
 * from the checklist ("visited" steps, keyed by [SetupStep.key] in preferences).
 */
enum class SetupStep(val key: String) {
    FIRST_SHIFT("first_shift"),
    CLOCK_STYLE("clock_style"),
    COMPENSATION("compensation"),
    PREMIUM("premium"),
    TASKS("tasks"),
    WIDGET("widget"),
}

data class SetupChecklistInputs(
    val hasCompletedShift: Boolean,
    val clockStyleCustomized: Boolean,
    val compensationProfileCount: Int,
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

        val steps = SetupStep.entries
            .filter { it != SetupStep.WIDGET || inputs.widgetPinSupported }
            .map { SetupStepState(it, isComplete(it, inputs)) }
        val completed = steps.count { it.isComplete }
        val allComplete = completed == steps.size

        if (allComplete && inputs.celebrated) return null

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
            SetupStep.CLOCK_STYLE -> inputs.clockStyleCustomized || visited
            // The default "Main job" profile is auto-created, so only a second
            // profile counts as data-driven completion; reviewing the rules
            // screen from the checklist counts too.
            SetupStep.COMPENSATION -> inputs.compensationProfileCount > 1 || visited
            // Same reasoning: a default premium profile always exists.
            SetupStep.PREMIUM -> inputs.hasCustomPremiumProfile || visited
            SetupStep.TASKS -> inputs.hasAnyTask
            SetupStep.WIDGET -> inputs.hasPinnedWidget
        }
    }
}

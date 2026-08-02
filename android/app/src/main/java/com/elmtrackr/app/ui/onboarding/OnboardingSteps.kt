package com.elmtrackr.app.ui.onboarding

import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.RegionCode

/**
 * Step indices for the onboarding wizard, and the two pure functions that walk
 * between them.
 *
 * [STEP_PROJECT_DEFAULTS] is conditional: it is only reachable when the user
 * answered yes to Paid Projects, so plain `step + 1` / `step - 1` would strand
 * everyone else on an empty screen. Keeping the traversal here — rather than in
 * the per-step lambdas and the back handler — means forward and backward
 * navigation cannot disagree about which steps exist.
 */
/**
 * The region the wizard opens on, and the source of every work-setup default.
 *
 * These lived apart: the region field defaulted to [RegionCode.IL] while the
 * overtime thresholds and weekend days were separate hardcoded constants
 * (8 h / 40 h / Fri-Sat), and the preset was applied only when the user actually
 * tapped a region chip. Accepting the pre-selected region therefore produced
 * thresholds that did not match it — and those feed the pay calculation.
 *
 * Keeping them in one place means the mismatch cannot come back silently.
 */
internal val ONBOARDING_DEFAULT_REGION = RegionCode.IL

internal fun onboardingDefaultRules(): CompensationRules =
    RegionPresets.forRegion(ONBOARDING_DEFAULT_REGION).rules

internal const val STEP_LANGUAGE = 1
internal const val STEP_WELCOME = 2
internal const val STEP_REGION = 3
internal const val STEP_PROFILE = 4
internal const val STEP_PAY = 5
internal const val STEP_WORK_WEEK = 6
internal const val STEP_FEATURES = 7
internal const val STEP_PAID_PROJECTS = 8
internal const val STEP_PROJECT_DEFAULTS = 9
internal const val STEP_SECURITY = 10
internal const val STEP_REVIEW = 11

/** Steps shown in the progress counter for the current answer. */
internal fun onboardingTotalSteps(paidProjectsEnabled: Boolean): Int =
    if (paidProjectsEnabled) STEP_REVIEW else STEP_REVIEW - 1

internal fun nextOnboardingStep(step: Int, paidProjectsEnabled: Boolean): Int = when {
    step >= STEP_REVIEW -> STEP_REVIEW
    step == STEP_PAID_PROJECTS && !paidProjectsEnabled -> STEP_SECURITY
    else -> step + 1
}

internal fun previousOnboardingStep(step: Int, paidProjectsEnabled: Boolean): Int = when {
    step <= STEP_LANGUAGE -> STEP_LANGUAGE
    step == STEP_SECURITY && !paidProjectsEnabled -> STEP_PAID_PROJECTS
    else -> step - 1
}

/**
 * Position in the progress bar. With the conditional step skipped, Security and
 * Review would otherwise report 10/10 and 11/10.
 */
internal fun onboardingProgressStep(step: Int, paidProjectsEnabled: Boolean): Int =
    if (!paidProjectsEnabled && step > STEP_PROJECT_DEFAULTS) step - 1 else step

package com.elmtrackr.app.ui.onboarding

import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.setup.SetupStep

/**
 * Step indices for the onboarding wizard, and the two pure functions that walk
 * between them. Keeping the traversal here — rather than in the per-step lambdas
 * and the back handler — means forward and backward navigation cannot disagree
 * about which steps exist.
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

/**
 * The four screens between signing up and using the app.
 *
 * There were eleven. Nothing in the other seven had to be answered before the
 * app could work: a display name feeds a greeting, the feature toggles are
 * opt-ins, an app lock is a preference, and the work-week and project-tax fields
 * already had correct values sitting in the region preset before the user
 * reached the screen asking for them again. What that produced was eleven
 * screens of questions in front of a product nobody had seen yet, most of them
 * answerable only by guessing.
 *
 * These four are the ones with no safe default:
 *  - [STEP_WELCOME] carries the language choice, because a wizard the user
 *    cannot read is not a wizard. (It was its own screen before this.)
 *  - [STEP_REGION] sets the currency, timezone, overtime thresholds and weekend
 *    days from one answer. Those feed the pay calculation, and there is no
 *    default that is right for everyone.
 *  - [STEP_PAY] is the hourly rate. Optional, but asking once here is far
 *    cheaper than every pay figure reading zero until the user finds Settings.
 *  - [STEP_REVIEW] shows what was set and finishes.
 *
 * Everything removed is now on the setup checklist (see [SetupStep]) or in
 * Settings, both of which the user reaches after seeing what the app does.
 */
internal const val STEP_WELCOME = 1
internal const val STEP_REGION = 2
internal const val STEP_PAY = 3
internal const val STEP_REVIEW = 4

/** Steps shown in the progress counter. */
internal fun onboardingTotalSteps(): Int = STEP_REVIEW

internal fun nextOnboardingStep(step: Int): Int = (step + 1).coerceAtMost(STEP_REVIEW)

internal fun previousOnboardingStep(step: Int): Int = (step - 1).coerceAtLeast(STEP_WELCOME)

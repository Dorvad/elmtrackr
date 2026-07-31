package com.elmtrackr.app.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingStepsTest {

    // ── Forward traversal ─────────────────────────────────────────────────────

    @Test
    fun `answering yes leads into the project defaults step`() {
        assertEquals(
            STEP_PROJECT_DEFAULTS,
            nextOnboardingStep(STEP_PAID_PROJECTS, paidProjectsEnabled = true),
        )
    }

    @Test
    fun `answering not now skips the project defaults step`() {
        assertEquals(
            STEP_SECURITY,
            nextOnboardingStep(STEP_PAID_PROJECTS, paidProjectsEnabled = false),
        )
    }

    @Test
    fun `every other step advances by one`() {
        listOf(STEP_LANGUAGE, STEP_WELCOME, STEP_REGION, STEP_PROFILE, STEP_PAY, STEP_WORK_WEEK, STEP_FEATURES)
            .forEach { step ->
                assertEquals(step + 1, nextOnboardingStep(step, paidProjectsEnabled = true))
                assertEquals(step + 1, nextOnboardingStep(step, paidProjectsEnabled = false))
            }
    }

    @Test
    fun `review is the last step`() {
        assertEquals(STEP_REVIEW, nextOnboardingStep(STEP_REVIEW, paidProjectsEnabled = true))
    }

    // ── Back traversal ────────────────────────────────────────────────────────

    @Test
    fun `back from security skips the project defaults step when declined`() {
        assertEquals(
            STEP_PAID_PROJECTS,
            previousOnboardingStep(STEP_SECURITY, paidProjectsEnabled = false),
        )
    }

    @Test
    fun `back from security lands on project defaults when enabled`() {
        assertEquals(
            STEP_PROJECT_DEFAULTS,
            previousOnboardingStep(STEP_SECURITY, paidProjectsEnabled = true),
        )
    }

    @Test
    fun `back never goes below the first step`() {
        assertEquals(STEP_LANGUAGE, previousOnboardingStep(STEP_LANGUAGE, paidProjectsEnabled = false))
        assertEquals(STEP_LANGUAGE, previousOnboardingStep(0, paidProjectsEnabled = false))
    }

    @Test
    fun `forward and back are inverses for every reachable step`() {
        listOf(true, false).forEach { enabled ->
            var step = STEP_LANGUAGE
            val visited = mutableListOf(step)
            while (step != STEP_REVIEW) {
                step = nextOnboardingStep(step, enabled)
                visited += step
            }
            visited.zipWithNext().reversed().forEach { (before, after) ->
                assertEquals(
                    "back from $after should return to $before (paidProjects=$enabled)",
                    before,
                    previousOnboardingStep(after, enabled),
                )
            }
        }
    }

    @Test
    fun `declining paid projects never visits the project defaults step`() {
        var step = STEP_LANGUAGE
        val visited = mutableListOf(step)
        while (step != STEP_REVIEW) {
            step = nextOnboardingStep(step, paidProjectsEnabled = false)
            visited += step
        }
        assertEquals(false, visited.contains(STEP_PROJECT_DEFAULTS))
    }

    // ── Progress counter ──────────────────────────────────────────────────────

    @Test
    fun `total steps reflects the answer`() {
        assertEquals(11, onboardingTotalSteps(paidProjectsEnabled = true))
        assertEquals(10, onboardingTotalSteps(paidProjectsEnabled = false))
    }

    @Test
    fun `progress never exceeds the total`() {
        listOf(true, false).forEach { enabled ->
            var step = STEP_LANGUAGE
            while (step != STEP_REVIEW) {
                val shown = onboardingProgressStep(step, enabled)
                assert(shown in 1..onboardingTotalSteps(enabled)) {
                    "step $step showed $shown/${onboardingTotalSteps(enabled)}"
                }
                step = nextOnboardingStep(step, enabled)
            }
            assertEquals(onboardingTotalSteps(enabled), onboardingProgressStep(STEP_REVIEW, enabled))
        }
    }

    // ── Optional tax rate parsing ─────────────────────────────────────────────

    @Test
    fun `blank tax rate means no tax rather than a guessed one`() {
        assertEquals(0, percentTextToBasisPoints(""))
        assertEquals(0, percentTextToBasisPoints("   "))
        assertEquals(0, percentTextToBasisPoints("abc"))
    }

    @Test
    fun `tax rate converts to basis points`() {
        assertEquals(1800, percentTextToBasisPoints("18"))
        assertEquals(1750, percentTextToBasisPoints("17.5"))
        assertEquals(10000, percentTextToBasisPoints("100"))
        assertEquals(1, percentTextToBasisPoints("0.01"))
    }

    @Test
    fun `out of range tax rates fall back to no tax`() {
        assertEquals(0, percentTextToBasisPoints("0"))
        assertEquals(0, percentTextToBasisPoints("-5"))
        assertEquals(0, percentTextToBasisPoints("101"))
    }

    @Test
    fun `basis points render back as an editable percentage`() {
        assertEquals("18", basisPointsToPercentText(1800))
        assertEquals("17.5", basisPointsToPercentText(1750))
        assertEquals("", basisPointsToPercentText(0))
        assertEquals("", basisPointsToPercentText(-1))
    }

    @Test
    fun `percent text survives a round trip`() {
        listOf("18", "17.5", "0.5", "100").forEach { input ->
            assertEquals(input, basisPointsToPercentText(percentTextToBasisPoints(input)))
        }
    }
}

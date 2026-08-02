package com.elmtrackr.app.review

import com.elmtrackr.app.domain.model.Shift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReviewPromptPolicyTest {

    private val now = 100L * 24 * 60 * 60 * 1000 // an arbitrary "day 100" in epoch millis

    /** A baseline that satisfies every rule; individual tests break one rule at a time. */
    private fun eligibleInputs() = ReviewPromptInputs(
        nowMs = now,
        validShiftCount = ReviewPromptPolicy.STRONG_MILESTONE_SHIFTS,
        usageDayCount = ReviewPromptPolicy.MIN_USAGE_DAYS,
        firstUsedAt = now - ReviewPromptPolicy.MIN_APP_AGE_MS,
        onboardingActive = false,
        shiftActive = false,
    )

    @Test
    fun `baseline with five valid shifts and enough usage days is eligible`() {
        val verdict = ReviewPromptPolicy.evaluate(eligibleInputs())
        assertTrue(verdict.eligible)
        assertTrue(verdict.hasStrongMilestone)
        assertTrue(verdict.blockers.isEmpty())
    }

    // ── Strong milestones ────────────────────────────────────────────────────

    @Test
    fun `viewed monthly report alone is a strong milestone`() {
        val verdict = ReviewPromptPolicy.evaluate(
            eligibleInputs().copy(validShiftCount = 0, monthlyReportViewedAt = now - 1_000),
        )
        assertTrue(verdict.eligible)
        assertTrue(verdict.hasStrongMilestone)
    }

    @Test
    fun `exported report alone is a strong milestone`() {
        val verdict = ReviewPromptPolicy.evaluate(
            eligibleInputs().copy(validShiftCount = 0, reportExportedAt = now - 1_000),
        )
        assertTrue(verdict.eligible)
        assertTrue(verdict.hasStrongMilestone)
    }

    @Test
    fun `four valid shifts and nothing else is not a strong milestone`() {
        val verdict = ReviewPromptPolicy.evaluate(
            eligibleInputs().copy(validShiftCount = ReviewPromptPolicy.STRONG_MILESTONE_SHIFTS - 1),
        )
        assertFalse(verdict.eligible)
        assertFalse(verdict.hasStrongMilestone)
        assertEquals(listOf(ReviewPromptBlocker.NO_STRONG_MILESTONE), verdict.blockers)
    }

    @Test
    fun `usage days alone without a strong milestone stays blocked`() {
        val verdict = ReviewPromptPolicy.evaluate(
            eligibleInputs().copy(validShiftCount = 0, usageDayCount = 10),
        )
        assertFalse(verdict.eligible)
        assertTrue(ReviewPromptBlocker.NO_STRONG_MILESTONE in verdict.blockers)
    }

    // ── Continued usage ──────────────────────────────────────────────────────

    @Test
    fun `fewer than three usage days blocks even with a strong milestone`() {
        val verdict = ReviewPromptPolicy.evaluate(
            eligibleInputs().copy(usageDayCount = ReviewPromptPolicy.MIN_USAGE_DAYS - 1),
        )
        assertFalse(verdict.eligible)
        assertEquals(listOf(ReviewPromptBlocker.NOT_ENOUGH_USAGE_DAYS), verdict.blockers)
    }

    // ── Suppression rules ────────────────────────────────────────────────────

    @Test
    fun `onboarding blocks the prompt`() {
        val verdict = ReviewPromptPolicy.evaluate(eligibleInputs().copy(onboardingActive = true))
        assertFalse(verdict.eligible)
        assertTrue(ReviewPromptBlocker.ONBOARDING_ACTIVE in verdict.blockers)
    }

    @Test
    fun `active shift blocks the prompt`() {
        val verdict = ReviewPromptPolicy.evaluate(eligibleInputs().copy(shiftActive = true))
        assertFalse(verdict.eligible)
        assertTrue(ReviewPromptBlocker.SHIFT_ACTIVE in verdict.blockers)
    }

    @Test
    fun `fresh install blocks the prompt`() {
        val verdict = ReviewPromptPolicy.evaluate(
            eligibleInputs().copy(firstUsedAt = now - ReviewPromptPolicy.MIN_APP_AGE_MS + 1),
        )
        assertFalse(verdict.eligible)
        assertTrue(ReviewPromptBlocker.APP_TOO_NEW in verdict.blockers)
    }

    @Test
    fun `unknown first usage counts as a fresh install`() {
        val verdict = ReviewPromptPolicy.evaluate(eligibleInputs().copy(firstUsedAt = null))
        assertFalse(verdict.eligible)
        assertTrue(ReviewPromptBlocker.APP_TOO_NEW in verdict.blockers)
    }

    @Test
    fun `recent clock-in blocks the prompt even after the shift ended`() {
        val verdict = ReviewPromptPolicy.evaluate(
            eligibleInputs().copy(lastClockInAt = now - ReviewPromptPolicy.CLOCK_IN_QUIET_MS + 1),
        )
        assertFalse(verdict.eligible)
        assertTrue(ReviewPromptBlocker.TOO_SOON_AFTER_CLOCK_IN in verdict.blockers)
    }

    @Test
    fun `old clock-in does not block`() {
        val verdict = ReviewPromptPolicy.evaluate(
            eligibleInputs().copy(lastClockInAt = now - ReviewPromptPolicy.CLOCK_IN_QUIET_MS),
        )
        assertTrue(verdict.eligible)
    }

    @Test
    fun `recent error or failed purchase blocks the prompt`() {
        val verdict = ReviewPromptPolicy.evaluate(
            eligibleInputs().copy(
                lastDiscouragingEventAt = now - ReviewPromptPolicy.DISCOURAGING_EVENT_QUIET_MS + 1,
            ),
        )
        assertFalse(verdict.eligible)
        assertTrue(ReviewPromptBlocker.RECENT_DISCOURAGING_EVENT in verdict.blockers)
    }

    @Test
    fun `attempt inside the long cooldown blocks the prompt`() {
        val verdict = ReviewPromptPolicy.evaluate(
            eligibleInputs().copy(lastAttemptAt = now - ReviewPromptPolicy.ATTEMPT_COOLDOWN_MS + 1),
        )
        assertFalse(verdict.eligible)
        assertTrue(ReviewPromptBlocker.RECENTLY_ATTEMPTED in verdict.blockers)
    }

    @Test
    fun `attempt older than the cooldown no longer blocks`() {
        val verdict = ReviewPromptPolicy.evaluate(
            eligibleInputs().copy(lastAttemptAt = now - ReviewPromptPolicy.ATTEMPT_COOLDOWN_MS),
        )
        assertTrue(verdict.eligible)
    }

    @Test
    fun `all blockers are reported together`() {
        val verdict = ReviewPromptPolicy.evaluate(
            ReviewPromptInputs(nowMs = now, onboardingActive = true, shiftActive = true),
        )
        assertFalse(verdict.eligible)
        assertTrue(ReviewPromptBlocker.ONBOARDING_ACTIVE in verdict.blockers)
        assertTrue(ReviewPromptBlocker.SHIFT_ACTIVE in verdict.blockers)
        assertTrue(ReviewPromptBlocker.APP_TOO_NEW in verdict.blockers)
        assertTrue(ReviewPromptBlocker.NO_STRONG_MILESTONE in verdict.blockers)
        assertTrue(ReviewPromptBlocker.NOT_ENOUGH_USAGE_DAYS in verdict.blockers)
    }

    // ── Valid shift counting ─────────────────────────────────────────────────

    @Test
    fun `countValidShifts skips active and too-short shifts`() {
        val start = Instant.parse("2024-01-08T09:00:00Z")
        val shifts = listOf(
            // Long enough: counts.
            shift("s1", start, start.plusSeconds(8 * 3600)),
            // Still active: does not count.
            shift("s2", start, end = null),
            // Below the minimum net duration: does not count.
            shift("s3", start, start.plusSeconds((ReviewPromptPolicy.MIN_VALID_SHIFT_MINUTES - 1) * 60L)),
            // Nominally long but breaks eat it below the minimum: does not count.
            shift(
                "s4",
                start,
                start.plusSeconds(ReviewPromptPolicy.MIN_VALID_SHIFT_MINUTES * 60L),
                breakMinutes = 5,
            ),
            // Exactly the minimum: counts.
            shift("s5", start, start.plusSeconds(ReviewPromptPolicy.MIN_VALID_SHIFT_MINUTES * 60L)),
        )
        assertEquals(2, ReviewPromptPolicy.countValidShifts(shifts))
    }

    private fun shift(id: String, start: Instant, end: Instant?, breakMinutes: Int = 0) = Shift(
        id = id,
        userId = "u1",
        startTime = start,
        endTime = end,
        breakMinutes = breakMinutes,
    )
}

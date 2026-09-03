package com.elmtrackr.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract that lets the bracket labels be translated.
 *
 * Both pay engines used to sort money into totals by substring-matching the
 * bracket's English label. That gave the label two jobs, and the second one is
 * why the first could never be localised: rendering a Hebrew label would have
 * collapsed every category into `regularGross` — no error, no failing test, just
 * wrong numbers on the reports and in the exports.
 *
 * These tests fix the mapping so that when the labels do move to resources, the
 * money cannot follow them.
 */
class PayCategoryTest {

    @Test
    fun `every category is booked to exactly one bucket`() {
        val expected = mapOf(
            PayCategory.REGULAR to PayBucket.REGULAR,
            PayCategory.OVERTIME to PayBucket.OVERTIME,
            PayCategory.DAILY_OVERTIME to PayBucket.OVERTIME,
            PayCategory.WEEKLY_OVERTIME to PayBucket.OVERTIME,
            PayCategory.SEVENTH_DAY to PayBucket.OVERTIME,
            PayCategory.WEEKEND to PayBucket.WEEKEND,
            PayCategory.WEEKLY_REST to PayBucket.WEEKEND,
            PayCategory.WEEKLY_REST_OVERTIME to PayBucket.OVERTIME,
            PayCategory.HOLIDAY to PayBucket.HOLIDAY,
            PayCategory.HOLIDAY_OVERTIME to PayBucket.OVERTIME,
            PayCategory.PREMIUM to PayBucket.HOLIDAY,
        )

        assertEquals(
            "a new category must be given a bucket here, deliberately",
            PayCategory.entries.toSet(),
            expected.keys,
        )
        expected.forEach { (category, bucket) ->
            assertEquals("$category", bucket, category.bucket)
        }
    }

    /**
     * Overtime worked during weekly rest or on a holiday is overtime money.
     *
     * This is not obvious and is easy to "tidy" the wrong way — the label says
     * "Weekly rest overtime", so a reader may expect the weekend bucket. The
     * label-matching code reached the overtime branch, and this keeps it there:
     * changing it would move money between two figures a user reads.
     */
    @Test
    fun `overtime during rest or holiday stays overtime money`() {
        assertEquals(PayBucket.OVERTIME, PayCategory.WEEKLY_REST_OVERTIME.bucket)
        assertEquals(PayBucket.OVERTIME, PayCategory.HOLIDAY_OVERTIME.bucket)
    }

    @Test
    fun `a premium profile is booked with holiday money`() {
        // `isSpecialDay = premiumProfileId != null` was once a money bug; it is now
        // only a UI coupling, and a premium shift is priced at its own multiplier.
        // Where that money lands is still the holiday bucket, as it always was.
        assertEquals(PayBucket.HOLIDAY, PayCategory.PREMIUM.bucket)
    }

    @Test
    fun `every bucket is reachable`() {
        assertEquals(
            "an unreachable bucket means a category was dropped",
            PayBucket.entries.toSet(),
            PayCategory.entries.map { it.bucket }.toSet(),
        )
    }

    @Test
    fun `night is not a bucket`() {
        // The night uplift is blended into each bracket's rate and subtracted back
        // out into nightGross, so night minutes are already counted under their own
        // category. A NIGHT bucket would count them twice and could drive another
        // bucket negative.
        assertTrue(PayBucket.entries.none { it.name.contains("NIGHT") })
    }
}

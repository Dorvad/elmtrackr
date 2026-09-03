package com.elmtrackr.app.domain.model

/**
 * Which of the displayed money totals a bracket belongs to.
 *
 * Four buckets, not five: night pay is not a bucket. The night uplift is blended
 * into each bracket's own rate and then subtracted back out into `nightGross`, so
 * a fifth minute-category would count the same minutes twice and let a bucket go
 * negative. See `PayrollCalculator.israeliBreakdown`.
 */
enum class PayBucket { REGULAR, OVERTIME, WEEKEND, HOLIDAY }

/**
 * What a stretch of paid minutes *is*, as a value rather than as a sentence.
 *
 * Both engines used to sort money into totals by substring-matching the bracket's
 * English label — `label.contains("overtime")`, `label.contains("Weekly rest")`.
 * That made the label do two jobs at once: something a user reads, and the key the
 * money is classified by. The second job is the reason the first could never be
 * translated: rendering "175% — מנוחה שבועית" would have collapsed every category
 * into `regularGross` silently, with no error and no failing test — just wrong
 * numbers on the reports and in the exports.
 *
 * Giving the category a type separates them. Money is classified from [bucket];
 * the label is free to become a translated string.
 */
enum class PayCategory(val bucket: PayBucket) {
    REGULAR(PayBucket.REGULAR),

    /** Overtime that is not attributed to a daily or weekly threshold. */
    OVERTIME(PayBucket.OVERTIME),
    DAILY_OVERTIME(PayBucket.OVERTIME),
    WEEKLY_OVERTIME(PayBucket.OVERTIME),

    /** California-style premium for the 7th consecutive workday of a pay week. */
    SEVENTH_DAY(PayBucket.OVERTIME),

    /** A calendar weekend day, on engines that treat the whole day alike. */
    WEEKEND(PayBucket.WEEKEND),

    /** Statutory weekly rest — Shabbat on the Israeli engine, from Friday 17:00. */
    WEEKLY_REST(PayBucket.WEEKEND),

    /**
     * Overtime worked during weekly rest.
     *
     * Booked to overtime rather than to the weekend bucket, which is what the
     * label matching did: `isWeeklyRest && bucket != REGULAR` reached the overtime
     * branch. Kept deliberately so the split does not move.
     */
    WEEKLY_REST_OVERTIME(PayBucket.OVERTIME),

    HOLIDAY(PayBucket.HOLIDAY),
    HOLIDAY_OVERTIME(PayBucket.OVERTIME),

    /** A user-defined premium profile attached to the shift. */
    PREMIUM(PayBucket.HOLIDAY),
}

/**
 * A single pay bracket (e.g. "100% Regular", "125% Overtime").
 *
 * [label] is display text. [category] is what the money is sorted by — never the
 * label. See [PayCategory].
 */
data class PayBracket(
    val label: String,
    val minutes: Int,
    val rate: Double,
    val amount: Double,
    val category: PayCategory,
)

/** Pay breakdown for a single completed shift. */
data class ShiftPayBreakdown(
    val brackets: List<PayBracket>,
    val totalGross: Double,
    val regularGross: Double = 0.0,
    val overtimeGross: Double = 0.0,
    val weekendGross: Double = 0.0,
    val holidayGross: Double = 0.0,
    val nightGross: Double = 0.0,
    val deductionsGross: Double = 0.0,
    val netGross: Double = totalGross,
    val isSpecial: Boolean,
    val profileId: String? = null,
    val profileName: String? = null,
    val currencyCode: String = "USD",
    val disclaimer: String = "",
)

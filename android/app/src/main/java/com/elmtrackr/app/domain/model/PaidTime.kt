package com.elmtrackr.app.domain.model

/**
 * One shift's time, from the clock to what is paid.
 *
 * Built by `PayrollCalculator.payableTime`, which is the walk both pay engines
 * price from, so an explanation built from this cannot disagree with the money.
 *
 * The invariant `payable == worked + paidBreak + rounding + minimumTopUp` holds by
 * construction, which is what lets a screen list the steps and have them add up.
 */
data class PaidTime(
    /** Wall-clock minutes between clock-in and clock-out. */
    val elapsedMinutes: Int,
    /** The break the user recorded on the shift. */
    val recordedBreakMinutes: Int,
    /** An unpaid break the profile deducted because none was recorded. */
    val autoBreakMinutes: Int,
    /** Whether the profile pays for recorded breaks. */
    val breaksPaid: Boolean,
    /** Signed change made by the rounding rule. */
    val roundingMinutes: Int,
    /** Minutes added to reach the profile's minimum paid shift. */
    val minimumTopUpMinutes: Int,
    /** What the pay engines price. */
    val payableMinutes: Int,
) {
    /**
     * Time actually worked: clock time less every break, paid or not.
     *
     * Never rounded and never topped up — this is the figure a person can check
     * against their own memory of the day.
     */
    val workedMinutes: Int
        get() = maxOf(0, elapsedMinutes - recordedBreakMinutes - autoBreakMinutes)

    /** Breaks that cost the person pay: recorded ones when unpaid, plus any auto-deduction. */
    val unpaidBreakMinutes: Int
        get() = if (breaksPaid) 0 else minOf(elapsedMinutes, recordedBreakMinutes) + autoBreakMinutes

    /** Recorded break minutes that the profile pays for anyway. */
    val paidBreakMinutes: Int
        get() = if (breaksPaid) minOf(elapsedMinutes, recordedBreakMinutes) else 0

    /** True when what is paid is not simply the time worked. */
    val payableDiffersFromWorked: Boolean
        get() = payableMinutes != workedMinutes

    /** Why [payableMinutes] differs from [workedMinutes]; empty when it does not. */
    val reasons: Set<PaidTimeReason>
        get() = PaidTimeReason.of(roundingMinutes, minimumTopUpMinutes, paidBreakMinutes)
}

/**
 * A rule that made paid time differ from worked time.
 *
 * Kept to the three adjustments [PaidTime] can tell apart. An automatically deducted
 * break is not one: it is taken out of worked and paid time alike.
 */
enum class PaidTimeReason {
    ROUNDING,
    MINIMUM_SHIFT,
    PAID_BREAKS,
    ;

    companion object {
        fun of(roundingMinutes: Int, minimumTopUpMinutes: Int, paidBreakMinutes: Int): Set<PaidTimeReason> =
            buildSet {
                if (roundingMinutes != 0) add(ROUNDING)
                if (minimumTopUpMinutes > 0) add(MINIMUM_SHIFT)
                if (paidBreakMinutes > 0) add(PAID_BREAKS)
            }
    }
}

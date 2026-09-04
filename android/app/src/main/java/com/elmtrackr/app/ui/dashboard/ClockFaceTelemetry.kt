package com.elmtrackr.app.ui.dashboard

/**
 * The figures the [drawsOwnReading] faces print.
 *
 * ### Why the money arrives pre-computed and pre-formatted
 *
 * A face must never work out what a shift earns. Waves B–E of the September 2026
 * audit existed to make one thing true — the hours a surface reports and the money it
 * shows are derived from the same classification — and `hours × rate` in a renderer
 * would break it on the most visible surface in the app. A shift with a rounding rule,
 * an auto-deducted break, a minimum-shift floor, an overtime tier or a night premium
 * earns something other than the naive product, and the dashboard's own pay card would
 * disagree with the clock face directly above it.
 *
 * So the caller computes earnings through `PayrollCalculator` and formats them through
 * `MoneyFormatter` — which since Wave E means CLDR, so an Israeli user reading Hebrew
 * sees `531.25 ₪` and a reader of Arabic sees Arabic-Indic digits. The renderer receives
 * strings it only measures and draws.
 *
 * Time is different and stays here: [elapsedMinutes] and [goalMinutes] are integers, and
 * everything derived from them is arithmetic no engine owns.
 */
internal data class ClockFaceTelemetry(
    val elapsedMinutes: Int,
    val goalMinutes: Int,
    /** Earnings so far, engine-computed and locale-formatted. */
    val earnedText: String,
    /** The hourly rate as a labelled figure, e.g. "₪62.50/h". */
    val rateText: String,
    /** Earnings at the goal — the Sparkline's target line. */
    val targetEarnedText: String,
    /**
     * The same two figures as numbers, because the Sparkline plots against them.
     *
     * Only ever used to scale a curve, never to display: whatever is shown comes from
     * the pre-formatted strings above.
     */
    val earned: Double,
    val targetEarned: Double,
) {
    /** 0..1 through the goal, clamped — a face never draws past its own box. */
    val progress: Float
        get() = if (goalMinutes <= 0) 0f else (elapsedMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f)

    val elapsedClock: String get() = clock(elapsedMinutes)

    /** Time left to the goal, floored at zero: overtime reads "00:00" left, not negative. */
    val remainingClock: String get() = clock((goalMinutes - elapsedMinutes).coerceAtLeast(0))

    val goalClock: String get() = clock(goalMinutes)

    /**
     * Five-minute cells filled, and the total the grid draws.
     *
     * Derived from the goal rather than fixed at 96 so an eight-hour goal gives the
     * 12×8 grid the design shows, and a six-hour goal gives a smaller one instead of a
     * grid that can never fill.
     */
    val cellCount: Int get() = (goalMinutes / MINUTES_PER_CELL).coerceAtLeast(1)
    val cellsFilled: Int get() = (elapsedMinutes / MINUTES_PER_CELL).coerceIn(0, cellCount)

    private fun clock(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    internal companion object {
        /** One cell per five minutes, which is what makes an hour twelve cells wide. */
        const val MINUTES_PER_CELL = 5

        /**
         * The figures a preview draws: 4 h 58 m of an eight-hour goal.
         *
         * Matches the reference design's fixture so a store tile and the design document
         * show the same shift. Money is passed in because even a preview must not invent
         * a currency — the picker formats the user's own rate.
         */
        fun preview(
            earnedText: String,
            rateText: String,
            targetEarnedText: String,
            earned: Double = 74.5,
            targetEarned: Double = 120.0,
        ) = ClockFaceTelemetry(
            elapsedMinutes = 298,
            goalMinutes = 480,
            earnedText = earnedText,
            rateText = rateText,
            targetEarnedText = targetEarnedText,
            earned = earned,
            targetEarned = targetEarned,
        )
    }
}

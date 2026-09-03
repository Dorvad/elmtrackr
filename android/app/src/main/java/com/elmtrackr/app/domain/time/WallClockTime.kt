package com.elmtrackr.app.domain.time

/**
 * A local wall-clock time of day, as stored in `rules_json`.
 *
 * The compensation rules carry three of these as strings — the weekly-rest start,
 * and the night window's two ends — and they were parsed with
 * `time.split(":")[0].toInt()`, twice, once in each engine. That throws on
 * anything that is not two integers around a colon, from inside the payroll
 * calculation, on a value that arrives over the wire.
 *
 * `CompensationRulesCodec` reads them with `optString` and no validation, so a
 * malformed value in a synced profile — a future version writing a different
 * shape, a hand-edited row, a truncated write — reached the engine intact. The
 * settings screen validates with a regex before it will save one, but the server
 * is not the settings screen, and the contract's own rule is that nothing crashes
 * on synced data.
 *
 * Parsing here returns null instead of throwing, and every caller supplies the
 * fallback that is right for it.
 */
object WallClockTime {

    /** `HH:mm` with an optional leading zero, 00:00–23:59. */
    private val PATTERN = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$")

    /**
     * Minutes since local midnight, or null when [time] is not a valid `HH:mm`.
     *
     * Deliberately strict rather than lenient: accepting "25:00" or "7" would put
     * a night window or a rest boundary somewhere nobody chose, and a silently
     * wrong boundary is worse than a documented fallback.
     */
    fun parseMinutesOfDayOrNull(time: String?): Int? {
        val match = PATTERN.matchEntire(time?.trim().orEmpty()) ?: return null
        val (hours, minutes) = match.destructured
        return hours.toInt() * 60 + minutes.toInt()
    }

    /** True when [time] is a value the engines can use. */
    fun isValid(time: String?): Boolean = parseMinutesOfDayOrNull(time) != null
}

package com.elmtrackr.wear.sync

import kotlinx.serialization.Serializable

@Serializable
data class WearShiftSnapshot(
    val signedIn: Boolean = false,
    val isActive: Boolean = false,
    val shiftId: String = "",
    val shiftStartEpochMillis: Long = 0L,
    val lastPunchEndEpochMillis: Long = 0L,
    val startTimeLabel: String = "--:--",
    val lastPunchLabel: String = "",
    val todayMinutes: Int = 0,
    val dailyGoalMinutes: Int = DEFAULT_DAILY_GOAL_MINUTES,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    /**
     * Whether the user has left crash reporting on, mirrored from the phone.
     *
     * Not shift state, and it is here because this snapshot is the only channel the
     * watch has. The watch ships no settings screen -- there is nowhere on a 1.2"
     * screen to put a privacy toggle that belongs next to the rest of them -- so the
     * consent the user gave or withheld in the phone app has to travel to the watch,
     * or the watch would report crashes for someone who opted out.
     *
     * Defaults to true, matching the phone's own opt-out default, which is also what
     * makes the watch diagnosable before it has ever seen a phone. Defaulted rather
     * than required so an older phone that does not send the field still decodes, and
     * the codec ignores unknown keys so an older watch still decodes a newer phone's
     * payload.
     */
    val crashReportingEnabled: Boolean = true,
) {
    companion object {
        /**
         * The fallback daily goal, in minutes, when a user has set no threshold.
         *
         * The single source for all three surfaces that need it. It lived here, in
         * `WidgetShiftState` and in `OvertimeReminderPolicy` as three independent
         * literals, so changing the default meant finding all three — and this module
         * is the one both the phone and the watch already depend on.
         */
        const val DEFAULT_DAILY_GOAL_MINUTES = 480

        fun signedOut(nowMillis: Long = System.currentTimeMillis()): WearShiftSnapshot =
            WearShiftSnapshot(signedIn = false, updatedAtEpochMillis = nowMillis)
    }
}

@Serializable
data class PunchResult(
    val success: Boolean,
    val errorCode: String? = null,
)

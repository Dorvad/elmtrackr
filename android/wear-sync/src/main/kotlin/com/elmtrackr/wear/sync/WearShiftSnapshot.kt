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

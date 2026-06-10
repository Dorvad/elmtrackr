package com.elmtrackr.app.domain.model

import java.time.Instant

/**
 * Per-user configuration.
 *
 * weekendDays uses the same 0=Sun…6=Sat encoding as JavaScript's Date.getUTCDay()
 * (not java.time.DayOfWeek.value which is 1=Mon…7=Sun).
 *
 * Israeli defaults: Friday(5) + Saturday(6), daily threshold 8h, weekly 40h.
 */
data class UserSettings(
    val id: String,
    val userId: String,
    val timezone: String = "UTC",
    val dailyOvertimeThresholdMinutes: Int = DEFAULT_DAILY_OT_MINUTES,
    val weeklyOvertimeThresholdMinutes: Int = DEFAULT_WEEKLY_OT_MINUTES,
    val weekendDays: List<Int> = DEFAULT_WEEKEND_DAYS,
    val hourlyRate: Double? = null,
    val onboardingCompleted: Boolean = false,
    val onboardingCompletedAt: Instant? = null,
    val featuresTravelRefunds: Boolean = false,
    val featuresPaidProjects: Boolean = false,
    val featuresInsights: Boolean = true,
    val featuresClockStyles: Boolean = true,
    val clockStyle: ClockStyle = ClockStyle.CLASSIC,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    companion object {
        const val DEFAULT_DAILY_OT_MINUTES = 480    // 8 hours
        const val DEFAULT_WEEKLY_OT_MINUTES = 2400  // 40 hours
        val DEFAULT_WEEKEND_DAYS = listOf(5, 6)     // Friday + Saturday
    }
}

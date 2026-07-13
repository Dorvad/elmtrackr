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
    val currency: CurrencyCode = CurrencyCode.ILS,
    val regionCode: RegionCode? = null,
    val currencyCode: String? = null,
    val defaultCompensationProfileId: String? = null,
    val onboardingCompleted: Boolean = false,
    val onboardingCompletedAt: Instant? = null,
    val featuresTravelRefunds: Boolean = false,
    // Reserved / not yet implemented. Persisted and synced for wire
    // compatibility, but there is deliberately no settings switch and no
    // consumer — it stays false until the paid-projects feature ships. Do not
    // surface it in the UI until then.
    val featuresPaidProjects: Boolean = false,
    val featuresInsights: Boolean = true,
    val featuresClockStyles: Boolean = true,
    val featuresOvertimeReminders: Boolean = true,
    val clockStyle: ClockStyle = ClockStyle.CLASSIC,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    companion object {
        const val DEFAULT_DAILY_OT_MINUTES = 480    // 8 hours
        const val DEFAULT_WEEKLY_OT_MINUTES = 2400  // 40 hours
        val DEFAULT_WEEKEND_DAYS = listOf(5, 6)     // Friday + Saturday
    }

    /**
     * The currency code every money display should use. The nullable string
     * (mirrored from the default compensation profile) wins over the legacy
     * enum so all screens agree after a profile currency change.
     */
    fun displayCurrencyCode(): String = currencyCode ?: currency.name

    data class Updates(
        val hourlyRate: Double? = null,
        val timezone: String? = null,
        val regionCode: RegionCode? = null,
        val currencyCode: String? = null,
        val currency: CurrencyCode? = null,
        val defaultCompensationProfileId: String? = null,
        val dailyOvertimeThresholdMinutes: Int? = null,
        val weeklyOvertimeThresholdMinutes: Int? = null,
        val weekendDays: List<Int>? = null,
    )

    fun apply(updates: Updates): UserSettings = copy(
        hourlyRate = updates.hourlyRate ?: hourlyRate,
        timezone = updates.timezone ?: timezone,
        regionCode = updates.regionCode ?: regionCode,
        currencyCode = updates.currencyCode ?: currencyCode,
        currency = updates.currency ?: currency,
        defaultCompensationProfileId = updates.defaultCompensationProfileId ?: defaultCompensationProfileId,
        dailyOvertimeThresholdMinutes = updates.dailyOvertimeThresholdMinutes ?: dailyOvertimeThresholdMinutes,
        weeklyOvertimeThresholdMinutes = updates.weeklyOvertimeThresholdMinutes ?: weeklyOvertimeThresholdMinutes,
        weekendDays = updates.weekendDays ?: weekendDays,
    )
}

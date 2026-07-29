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
    /**
     * Master switch for the optional Paid Projects module. Off for every
     * existing user; turning it off hides all project surfaces but never
     * deletes project data.
     */
    val featuresPaidProjects: Boolean = false,
    val featuresInsights: Boolean = true,
    val featuresClockStyles: Boolean = true,
    val featuresOvertimeReminders: Boolean = true,
    /**
     * Defaults applied to newly created projects. Collected in the optional
     * onboarding step and editable later; all of them are safe to leave unset.
     * Not part of the Supabase wire format yet — see AGENTS/contract notes.
     */
    val projectsDefaultRegionCode: RegionCode? = null,
    val projectsDefaultCurrencyCode: String? = null,
    /** e.g. "VAT" / "מע״מ". Null means the app falls back to a generic label. */
    val projectsTaxLabel: String? = null,
    /** Basis points: 1800 = 18.00 %. Zero means project tax is off. */
    val projectsTaxRateBasisPoints: Int = 0,
    /** True = the project fee already includes tax. */
    val projectsTaxInclusive: Boolean = false,
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

    /** Currency a newly created project defaults to. */
    fun projectsCurrencyCode(): String = projectsDefaultCurrencyCode ?: displayCurrencyCode()

    /** False when the user skipped or cleared the optional project tax rate. */
    val projectsTaxEnabled: Boolean get() = projectsTaxRateBasisPoints > 0

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

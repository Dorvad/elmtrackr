package com.elmtrackr.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteUserSettingsRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val timezone: String,
    @SerialName("daily_overtime_threshold_minutes") val dailyOvertimeThresholdMinutes: Int,
    @SerialName("weekly_overtime_threshold_minutes") val weeklyOvertimeThresholdMinutes: Int,
    @SerialName("weekend_days") val weekendDays: List<Int>,
    @SerialName("hourly_rate") val hourlyRate: Double? = null,
    val currency: String? = null,
    @SerialName("region_code") val regionCode: String? = null,
    @SerialName("currency_code") val currencyCode: String? = null,
    @SerialName("default_compensation_profile_id") val defaultCompensationProfileId: String? = null,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = true,
    @SerialName("onboarding_completed_at") val onboardingCompletedAt: String? = null,
    // Travel refunds is opt-in: fall back to false (matching the local/domain
    // default and onboarding) when a server row omits the column, so a missing
    // value never silently switches the feature on.
    @SerialName("features_travel_refunds") val featuresTravelRefunds: Boolean = false,
    @SerialName("features_paid_projects") val featuresPaidProjects: Boolean = false,
    @SerialName("features_insights") val featuresInsights: Boolean = true,
    @SerialName("features_clock_styles") val featuresClockStyles: Boolean = true,
    @SerialName("features_overtime_reminders") val featuresOvertimeReminders: Boolean = true,
    @SerialName("clock_style") val clockStyle: String = "classic",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class RemoteUserSettingsUpsert(
    @SerialName("user_id") val userId: String,
    val timezone: String,
    @SerialName("daily_overtime_threshold_minutes") val dailyOvertimeThresholdMinutes: Int,
    @SerialName("weekly_overtime_threshold_minutes") val weeklyOvertimeThresholdMinutes: Int,
    @SerialName("weekend_days") val weekendDays: List<Int>,
    @SerialName("hourly_rate") val hourlyRate: Double? = null,
    val currency: String,
    @SerialName("region_code") val regionCode: String? = null,
    @SerialName("currency_code") val currencyCode: String? = null,
    @SerialName("default_compensation_profile_id") val defaultCompensationProfileId: String? = null,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean,
    @SerialName("onboarding_completed_at") val onboardingCompletedAt: String? = null,
    @SerialName("features_travel_refunds") val featuresTravelRefunds: Boolean,
    @SerialName("features_paid_projects") val featuresPaidProjects: Boolean,
    @SerialName("features_insights") val featuresInsights: Boolean,
    @SerialName("features_clock_styles") val featuresClockStyles: Boolean,
    @SerialName("features_overtime_reminders") val featuresOvertimeReminders: Boolean = true,
    @SerialName("clock_style") val clockStyle: String,
)

@Serializable
data class RemoteUserSettingsUpdate(
    val timezone: String,
    @SerialName("daily_overtime_threshold_minutes") val dailyOvertimeThresholdMinutes: Int,
    @SerialName("weekly_overtime_threshold_minutes") val weeklyOvertimeThresholdMinutes: Int,
    @SerialName("weekend_days") val weekendDays: List<Int>,
    @SerialName("hourly_rate") val hourlyRate: Double? = null,
    val currency: String,
    @SerialName("region_code") val regionCode: String? = null,
    @SerialName("currency_code") val currencyCode: String? = null,
    @SerialName("default_compensation_profile_id") val defaultCompensationProfileId: String? = null,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean,
    @SerialName("onboarding_completed_at") val onboardingCompletedAt: String? = null,
    @SerialName("features_travel_refunds") val featuresTravelRefunds: Boolean,
    @SerialName("features_paid_projects") val featuresPaidProjects: Boolean,
    @SerialName("features_insights") val featuresInsights: Boolean,
    @SerialName("features_clock_styles") val featuresClockStyles: Boolean,
    @SerialName("features_overtime_reminders") val featuresOvertimeReminders: Boolean = true,
    @SerialName("clock_style") val clockStyle: String,
    /**
     * The device's edit time, and the guard the write is filtered on. Without it
     * a device that had been offline overwrote the server unconditionally, and the
     * pull in the same pass carried those stale settings back to the device that
     * had the newer ones — both converging on the old values, silently.
     */
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

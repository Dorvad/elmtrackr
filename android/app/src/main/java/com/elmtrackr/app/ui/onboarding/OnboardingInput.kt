package com.elmtrackr.app.ui.onboarding

import java.util.TimeZone

data class OnboardingInput(
    val displayName: String = "",
    val timezone: String = TimeZone.getDefault().id,
    val dailyOvertimeHours: Int = 8,
    val weeklyOvertimeHours: Int = 40,
    val weekendDays: List<Int> = listOf(5, 6),
    val hourlyRate: Double? = null,
    val featuresTravelRefunds: Boolean = false,
    val featuresPaidProjects: Boolean = false,
    val featuresInsights: Boolean = true,
    val featuresClockStyles: Boolean = true,
)
